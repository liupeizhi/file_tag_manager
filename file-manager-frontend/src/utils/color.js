/**
 * 颜色工具 —— 与 Hermes Studio 主题解析算法保持一致
 */

export function hexToRgb(hex) {
  const value = hex.replace('#', '')
  return [
    parseInt(value.slice(0, 2), 16),
    parseInt(value.slice(2, 4), 16),
    parseInt(value.slice(4, 6), 16)
  ]
}

export function rgbToHex(rgb) {
  return '#' + rgb.map(v => Math.round(v).toString(16).padStart(2, '0')).join('')
}

/**
 * 混合两个颜色。weight 为 a 的权重 (0~1)
 */
export function mix(a, b, weight) {
  const ca = hexToRgb(a)
  const cb = hexToRgb(b)
  return rgbToHex(ca.map((v, i) => v * weight + cb[i] * (1 - weight)))
}

/**
 * hex -> "r, g, b" 字符串, 供 rgba(var(--x-rgb), .5) 使用
 */
export function rgbString(hex) {
  return hexToRgb(hex).join(', ')
}

/**
 * 规范化十六进制颜色, 非法返回 null
 */
export function normalizeHex(color) {
  if (typeof color !== 'string') return null
  const value = color.trim().toLowerCase()
  if (/^#[0-9a-f]{6}$/.test(value)) return value
  if (/^#[0-9a-f]{3}$/.test(value)) {
    return '#' + value.slice(1).split('').map(c => c.repeat(2)).join('')
  }
  return null
}

/**
 * 依据相对亮度选择前景色
 */
export function contrastText(hex) {
  const [r, g, b] = hexToRgb(hex).map(v => {
    const s = v / 255
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.42 ? '#1a1a1a' : '#ffffff'
}

/**
 * 夹紧字号, 与 Hermes 一致 (12~20)
 */
export function clampFontSize(value) {
  const n = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(n)) return 14
  return Math.min(20, Math.max(12, Math.round(n)))
}
