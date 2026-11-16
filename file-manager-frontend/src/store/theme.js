import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  mix,
  rgbString,
  normalizeHex,
  contrastText,
  clampFontSize
} from '@/utils/color'
import { getTheme, updateTheme, uploadThemeBackground, removeThemeBackground } from '@/api/theme'

const BRIGHTNESS_KEY = 'file-manager-brightness'
const STYLE_KEY = 'file-manager-style'

/**
 * 把用户自定义值叠加到基础主题上, 得到最终生效的令牌集合
 */
function resolveTheme(theme, isDark) {
  const bg = isDark ? '#1a1a1a' : '#fafafa'
  const resolved = { fontSize: clampFontSize(theme.fontSize) }

  const textColor = normalizeHex(theme.textColor)
  if (textColor) {
    resolved['text-primary'] = textColor
    resolved['text-secondary'] = mix(textColor, bg, 0.68)
    resolved['text-muted'] = mix(textColor, bg, 0.5)
    resolved['text-primary-rgb'] = rgbString(textColor)
    resolved['text-secondary-rgb'] = rgbString(resolved['text-secondary'])
    resolved['text-muted-rgb'] = rgbString(resolved['text-muted'])
  }

  const accentColor = normalizeHex(theme.accentColor)
  if (accentColor) {
    const base = isDark ? '#ffffff' : '#000000'
    resolved['accent-primary'] = accentColor
    resolved['accent-hover'] = mix(base, accentColor, 0.18)
    resolved['accent-muted'] = mix(accentColor, bg, 0.55)
    resolved['accent-primary-rgb'] = rgbString(accentColor)
    resolved['accent-hover-rgb'] = rgbString(resolved['accent-hover'])
    resolved['text-on-accent'] = contrastText(accentColor)
  }

  return resolved
}

/**
 * 用户自定义的令牌集合 —— 只有这些会以内联样式写入,
 * 基础令牌(亮/暗/漫画)全部由 CSS 提供, 因此行内样式不会压掉风格变体。
 */
const CUSTOM_TOKENS = [
  'text-primary',
  'text-secondary',
  'text-muted',
  'text-primary-rgb',
  'text-secondary-rgb',
  'text-muted-rgb',
  'accent-primary',
  'accent-hover',
  'accent-muted',
  'accent-primary-rgb',
  'accent-hover-rgb',
  'text-on-accent'
]

export const useThemeStore = defineStore('theme', () => {
  const brightness = ref('system')
  const style = ref('ink')
  const fontSize = ref(14)
  const textColor = ref(null)
  const accentColor = ref(null)
  const background = ref(null)
  const loaded = ref(false)

  const systemPrefersDark = ref(
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches
  )

  const isDark = computed(() => {
    if (brightness.value === 'system') return systemPrefersDark.value
    return brightness.value === 'dark'
  })

  const brightnessLabel = computed(() => {
    if (brightness.value === 'system') return '跟随系统'
    return brightness.value === 'dark' ? '深色' : '浅色'
  })

  /** 写入 CSS 变量; 值为空时移除以回落到样式表默认值 */
  function setVar(name, value) {
    const root = document.documentElement
    if (value) {
      root.style.setProperty(name, value)
    } else {
      root.style.removeProperty(name)
    }
  }

  /**
   * 基础令牌由 CSS 的 :root / html.dark 提供, 这里只写入用户自定义项;
   * 自定义项为空时移除行内样式, 自动回落到 CSS 默认值。
   */
  function applyTokens() {
    const custom = resolveTheme(
      {
        fontSize: fontSize.value,
        textColor: textColor.value,
        accentColor: accentColor.value
      },
      isDark.value
    )

    // 自定义项为空时移除行内样式, 自动回落到 CSS 默认值
    for (const name of CUSTOM_TOKENS) {
      setVar(`--${name}`, custom[name])
    }

    document.documentElement.style.setProperty(
      '--font-size-base',
      `${custom.fontSize}px`
    )

    document.body.classList.remove('light-theme', 'dark-theme')
    document.body.classList.add(`${isDark.value ? 'dark' : 'light'}-theme`)
  }

  /** 应用风格(ink / comic) 与明暗 class */
  function applyStyle() {
    const root = document.documentElement
    root.classList.toggle('comic', style.value === 'comic')
    root.classList.toggle('dark', isDark.value)
  }

  /** 应用背景图 */
  function applyBackground() {
    const root = document.documentElement
    if (background.value?.url) {
      root.style.setProperty('--app-background-image', `url("${background.value.url}")`)
      root.classList.add('has-background')
    } else {
      root.style.removeProperty('--app-background-image')
      root.classList.remove('has-background')
    }
  }

  function applyAll() {
    applyTokens()
    applyStyle()
    applyBackground()
  }

  function setBrightness(value) {
    brightness.value = value
    localStorage.setItem(BRIGHTNESS_KEY, value)
    applyAll()
    syncToServer()
  }

  function setStyle(value) {
    style.value = value
    localStorage.setItem(STYLE_KEY, value)
    applyStyle()
    syncToServer()
  }

  /**
   * 静默同步到服务端。
   * 亮度/风格也落库, 这样换浏览器或换设备时主题能跟着账号走;
   * 同步失败不回滚本地状态, 下次交互会再试。
   */
  async function syncToServer() {
    try {
      await persist()
    } catch (e) {
      // 本地已生效, 静默失败即可
    }
  }

  function setFontSize(value) {
    fontSize.value = clampFontSize(value)
    applyTokens()
  }

  function setTextColor(value) {
    textColor.value = normalizeHex(value)
    applyTokens()
  }

  function setAccentColor(value) {
    accentColor.value = normalizeHex(value)
    applyTokens()
  }

  function loadLocalPrefs() {
    const savedBrightness = localStorage.getItem(BRIGHTNESS_KEY)
    if (savedBrightness && ['system', 'light', 'dark'].includes(savedBrightness)) {
      brightness.value = savedBrightness
    }
    const savedStyle = localStorage.getItem(STYLE_KEY)
    if (savedStyle && ['ink', 'comic'].includes(savedStyle)) {
      style.value = savedStyle
    }
  }

  /** 从服务端拉取用户主题 */
  async function fetchTheme() {
    try {
      const theme = await getTheme()
      if (theme) {
        fontSize.value = clampFontSize(theme.fontSize)
        textColor.value = normalizeHex(theme.textColor)
        accentColor.value = normalizeHex(theme.accentColor)
        // 亮度/风格也以服务端为准; 服务端没有记录时保留本地偏好
        if (theme.brightness && ['system', 'light', 'dark'].includes(theme.brightness)) {
          brightness.value = theme.brightness
          localStorage.setItem(BRIGHTNESS_KEY, theme.brightness)
        }
        if (theme.style && ['ink', 'comic'].includes(theme.style)) {
          style.value = theme.style
          localStorage.setItem(STYLE_KEY, theme.style)
        }
        background.value = theme.background
          ? { ...theme.background, url: '/api/theme/background' }
          : null
      }
    } catch (e) {
      // 静默失败, 使用本地默认值
    } finally {
      loaded.value = true
      applyAll()
    }
  }

  /** 保存到服务端 */
  async function persist() {
    const payload = {
      fontSize: fontSize.value,
      brightness: brightness.value,
      style: style.value,
      textColor: textColor.value,
      accentColor: accentColor.value
    }
    const saved = await updateTheme(payload)
    if (saved) {
      fontSize.value = clampFontSize(saved.fontSize)
      textColor.value = normalizeHex(saved.textColor)
      accentColor.value = normalizeHex(saved.accentColor)
    }
    applyTokens()
    return saved
  }

  async function uploadBackground(file) {
    const saved = await uploadThemeBackground(file)
    if (saved) {
      background.value = saved.background
        ? { ...saved.background, url: '/api/theme/background' }
        : null
    }
    applyBackground()
    return saved
  }

  async function removeBackground() {
    const saved = await removeThemeBackground()
    background.value = null
    applyBackground()
    return saved
  }

  /** 恢复默认: 清空自定义色与字号, 亮度/风格回到出厂值 */
  async function restoreDefaults() {
    fontSize.value = 14
    textColor.value = null
    accentColor.value = null
    brightness.value = 'system'
    style.value = 'ink'
    localStorage.setItem(BRIGHTNESS_KEY, 'system')
    localStorage.setItem(STYLE_KEY, 'ink')
    await persist()
    applyAll()
  }

  function init() {
    loadLocalPrefs()
    applyAll()

    if (typeof window !== 'undefined') {
      const query = window.matchMedia('(prefers-color-scheme: dark)')
      const handler = e => {
        systemPrefersDark.value = e.matches
        if (brightness.value === 'system') applyAll()
      }
      if (query.addEventListener) {
        query.addEventListener('change', handler)
      }
    }
  }

  return {
    brightness,
    style,
    fontSize,
    textColor,
    accentColor,
    background,
    loaded,
    isDark,
    brightnessLabel,
    init,
    fetchTheme,
    applyAll,
    setBrightness,
    setStyle,
    setFontSize,
    setTextColor,
    setAccentColor,
    persist,
    uploadBackground,
    removeBackground,
    restoreDefaults
  }
})
