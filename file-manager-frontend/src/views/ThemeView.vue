<template>
  <div class="theme-view">
    <div class="theme-shell">
      <div class="page-header">
        <div class="header-left">
          <button class="back-button" @click="goHome">
            <el-icon :size="14"><ArrowLeft /></el-icon>
            返回主页
          </button>
          <div>
            <h2>主题</h2>
            <p class="page-subtitle">你的工作区，你的氛围</p>
          </div>
        </div>
        <el-button @click="handleRestoreDefaults">
          <el-icon><RefreshLeft /></el-icon>
          恢复默认
        </el-button>
      </div>

      <div class="theme-content">
      <!-- 外观 -->
      <section class="theme-card">
        <div class="section-heading">
          <h3>外观</h3>
          <p>亮度、风格与排版</p>
        </div>

        <div class="theme-setting">
          <label>亮度</label>
          <div class="setting-control">
            <el-radio-group
              :model-value="themeStore.brightness"
              size="small"
              @change="handleBrightness"
            >
              <el-radio-button value="system">跟随系统</el-radio-button>
              <el-radio-button value="light">浅色</el-radio-button>
              <el-radio-button value="dark">深色</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <div class="theme-setting">
          <label>风格</label>
          <div class="setting-control">
            <el-radio-group
              :model-value="themeStore.style"
              size="small"
              @change="handleStyle"
            >
              <el-radio-button value="ink">墨水 Ink</el-radio-button>
              <el-radio-button value="comic">漫画 Comic</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <div class="theme-setting">
          <label>字号</label>
          <div class="setting-control font-size-control">
            <el-slider
              :model-value="themeStore.fontSize"
              :min="12"
              :max="20"
              :step="1"
              :show-tooltip="false"
              class="font-size-slider"
              @input="handleFontSizeInput"
              @change="handleFontSizeCommit"
            />
            <span class="font-size-value">{{ themeStore.fontSize }}px</span>
          </div>
        </div>

        <div class="theme-setting">
          <label>文字颜色</label>
          <div class="setting-control color-control">
            <input
              type="color"
              class="color-swatch"
              :value="themeStore.textColor || defaultTextColor"
              @input="handleTextColorPreview"
              @change="handleTextColorCommit"
            />
            <code class="color-code">{{ themeStore.textColor || '默认' }}</code>
            <el-button
              v-if="themeStore.textColor"
              link
              size="small"
              @click="handleResetTextColor"
            >
              重置
            </el-button>
          </div>
        </div>

        <div class="theme-setting">
          <label>强调色</label>
          <div class="setting-control color-control">
            <input
              type="color"
              class="color-swatch"
              :value="themeStore.accentColor || defaultAccentColor"
              @input="handleAccentColorPreview"
              @change="handleAccentColorCommit"
            />
            <code class="color-code">{{ themeStore.accentColor || '默认' }}</code>
            <el-button
              v-if="themeStore.accentColor"
              link
              size="small"
              @click="handleResetAccentColor"
            >
              重置
            </el-button>
          </div>
        </div>
      </section>

      <!-- 背景图 -->
      <section class="theme-card">
        <div class="section-heading">
          <h3>背景图</h3>
          <p>为工作区添加一张壁纸</p>
        </div>

        <div class="background-actions">
          <el-button type="primary" @click="triggerFileSelect">
            <el-icon><Upload /></el-icon>
            上传图片
          </el-button>
          <el-button v-if="themeStore.background" @click="handleRemoveBackground">
            <el-icon><Delete /></el-icon>
            移除
          </el-button>
          <input
            ref="fileInput"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            class="hidden-input"
            @change="handleFileChange"
          />
        </div>

        <div
          class="background-preview"
          :style="backgroundPreviewStyle"
        >
          <template v-if="themeStore.background">
            <span class="background-name">{{ themeStore.background.name }}</span>
          </template>
          <span v-else class="background-empty">尚未设置背景图</span>
        </div>

        <p class="background-note">支持 PNG / JPEG / WEBP / GIF，最大 10 MB</p>
      </section>

      <!-- 预览 -->
      <section class="theme-card">
        <div class="section-heading">
          <h3>预览</h3>
          <p>当前主题在内容区域的效果</p>
        </div>

        <div class="theme-preview">
          <div class="preview-kicker">PREVIEW</div>
          <h3>文件标签浏览器</h3>
          <p>主色调与文字层级将随上面的设置实时变化。</p>
          <button class="preview-button">示例按钮</button>
        </div>
      </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { ArrowLeft, RefreshLeft, Upload, Delete } from '@element-plus/icons-vue'
import { useThemeStore } from '@/store/theme'

const router = useRouter()
const themeStore = useThemeStore()
const fileInput = ref(null)

function goHome() {
  router.push('/')
}

const defaultTextColor = computed(() => (themeStore.isDark ? '#e0e0e0' : '#1a1a1a'))
const defaultAccentColor = computed(() => (themeStore.isDark ? '#e0e0e0' : '#1a1a1a'))

const backgroundPreviewStyle = computed(() => {
  if (themeStore.background?.url) {
    return { backgroundImage: `url("${themeStore.background.url}")` }
  }
  return {}
})

function handleBrightness(value) {
  themeStore.setBrightness(value)
}

function handleStyle(value) {
  themeStore.setStyle(value)
}

function handleFontSizeInput(value) {
  themeStore.setFontSize(value)
}

async function handleFontSizeCommit(value) {
  themeStore.setFontSize(value)
  try {
    await themeStore.persist()
  } catch (e) {
    ElMessage.error('保存字号失败')
  }
}

/**
 * 颜色选择器拖动过程中会持续触发 input 事件。
 * 预览走本地(不落库), 只有松开手柄触发 change 时才写服务端,
 * 避免一次拖动产生几十次 PUT。
 */
function handleTextColorPreview(event) {
  themeStore.setTextColor(event.target.value)
}

function handleAccentColorPreview(event) {
  themeStore.setAccentColor(event.target.value)
}

async function handleTextColorCommit(event) {
  themeStore.setTextColor(event.target.value)
  await persistQuietly()
}

async function handleAccentColorCommit(event) {
  themeStore.setAccentColor(event.target.value)
  await persistQuietly()
}

async function handleResetTextColor() {
  themeStore.setTextColor(null)
  await persistQuietly()
}

async function handleResetAccentColor() {
  themeStore.setAccentColor(null)
  await persistQuietly()
}

async function persistQuietly() {
  try {
    await themeStore.persist()
  } catch (e) {
    ElMessage.error('保存主题失败')
  }
}

function triggerFileSelect() {
  fileInput.value?.click()
}

async function handleFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('图片不能超过 10 MB')
    return
  }

  try {
    await themeStore.uploadBackground(file)
    ElMessage.success('背景图已更新')
  } catch (e) {
    ElMessage.error('上传失败: ' + (e.message || '未知错误'))
  }
}

async function handleRemoveBackground() {
  try {
    await themeStore.removeBackground()
    ElMessage.success('背景图已移除')
  } catch (e) {
    ElMessage.error('移除失败')
  }
}

async function handleRestoreDefaults() {
  try {
    await themeStore.restoreDefaults()
    ElMessage.success('已恢复默认主题')
  } catch (e) {
    ElMessage.error('恢复失败')
  }
}
</script>

<style scoped>
.theme-view {
  height: 100vh;
  overflow-y: auto;
  padding: 12px;
  background: var(--canvas-bg);
  color: var(--text-primary);
}

.theme-shell {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  max-width: 960px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--border-color);
}

.header-left {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.back-button {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  margin-top: -2px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-input);
  color: var(--text-secondary);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: background var(--transition-fast), color var(--transition-fast),
    border-color var(--transition-fast);
}

.back-button:hover {
  background: var(--bg-secondary);
  border-color: var(--text-muted);
  color: var(--text-primary);
}

.page-header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}

.theme-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 24px 32px;
  max-width: 880px;
}

.theme-card {
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 18px;
}

.section-heading {
  margin-bottom: 16px;
}

.section-heading h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.section-heading p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}

.theme-setting {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-light);
}

.theme-setting:last-child {
  border-bottom: none;
}

.theme-setting label {
  flex-shrink: 0;
  width: 80px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.setting-control {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
}

.font-size-control {
  max-width: 320px;
}

.font-size-slider {
  flex: 1;
}

.font-size-value {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
  width: 38px;
  text-align: right;
}

.color-control {
  gap: 10px;
}

.color-swatch {
  width: 34px;
  height: 34px;
  padding: 2px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-input);
  background: var(--bg-card);
  cursor: pointer;
}

.color-code {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
  width: 76px;
}

.background-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.hidden-input {
  display: none;
}

.background-preview {
  position: relative;
  min-height: 170px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-control);
  background-color: var(--bg-secondary);
  background-position: 50%;
  background-repeat: no-repeat;
  background-size: cover;
  display: flex;
  align-items: flex-end;
  padding: 12px;
  overflow: hidden;
}

.background-name {
  color: #ffffff;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 5px;
  padding: 4px 8px;
  font-size: 11px;
}

.background-empty {
  color: var(--text-muted);
  font-size: 12px;
  margin: auto;
}

.background-note {
  margin: 10px 0 0;
  font-size: 11px;
  color: var(--text-muted);
}

.theme-preview {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-control);
  background: linear-gradient(135deg,
      color-mix(in srgb, var(--accent-primary) 14%, transparent),
      transparent 58%),
    var(--bg-card);
  padding: 32px;
}

.preview-kicker {
  color: var(--accent-primary);
  letter-spacing: 0.08em;
  font-size: 11px;
  font-weight: 600;
}

.theme-preview h3 {
  font-size: calc(var(--font-size-base) + 8px);
  margin: 6px 0 4px;
  color: var(--text-primary);
}

.theme-preview p {
  color: var(--text-secondary);
  font-size: var(--font-size-base);
  margin: 0 0 16px;
}

.preview-button {
  background: var(--accent-primary);
  color: var(--text-on-accent);
  border: none;
  border-radius: var(--radius-input);
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.preview-button:hover {
  background: var(--accent-hover);
}
</style>
