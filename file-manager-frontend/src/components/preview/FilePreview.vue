<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="preview-overlay" @click.self="close">
        <div
          ref="dialogRef"
          class="preview-dialog"
          :class="{ maximized: isMaximized, dragging: isDragging }"
          :style="dialogStyle"
        >
          <div class="dialog-header" @mousedown="startDrag">
            <span class="dialog-title">{{ file?.name || '文件预览' }}</span>
            <div class="dialog-controls">
              <el-button text size="small" @click="toggleMaximize">
                <el-icon><FullScreen v-if="!isMaximized" /><CopyDocument v-else /></el-icon>
              </el-button>
              <el-button text size="small" @click="close">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </div>

          <div class="dialog-body">
            <div v-if="loading" class="loading-state">
              <el-icon class="is-loading" :size="48"><Loading /></el-icon>
              <p>加载中...</p>
            </div>

            <div v-else-if="error" class="error-state">
              <el-icon :size="48"><WarningFilled /></el-icon>
              <p>{{ error }}</p>
              <el-button type="primary" @click="download">下载查看</el-button>
            </div>

            <div v-else class="preview-content">
              <template v-if="fileType === 'image'">
                <div class="image-preview-container">
                  <div class="image-toolbar" ref="imageToolbarRef">
                    <el-button-group size="small" v-if="imageFiles.length > 1">
                      <el-button @click="prevImage" :disabled="!hasPrevImage">
                        <el-icon><ArrowLeft /></el-icon>
                      </el-button>
                      <el-button>{{ currentImageIndex + 1 }} / {{ imageFiles.length }}</el-button>
                      <el-button @click="nextImage" :disabled="!hasNextImage">
                        <el-icon><ArrowRight /></el-icon>
                      </el-button>
                    </el-button-group>
                    <el-button-group size="small">
                      <el-button @click="zoomOut" :disabled="imageScale <= 0.25">
                        <el-icon><ZoomOut /></el-icon>
                      </el-button>
                      <el-button>{{ Math.round(imageScale * 100) }}%</el-button>
                      <el-button @click="zoomIn" :disabled="imageScale >= 4">
                        <el-icon><ZoomIn /></el-icon>
                      </el-button>
                      <el-button @click="resetZoom">
                        <el-icon><RefreshRight /></el-icon>
                      </el-button>
                      <el-button @click="toggleNavigator" :type="showNavigator ? 'primary' : 'default'">
                        <el-icon><Aim /></el-icon>
                      </el-button>
                    </el-button-group>
                  </div>
                  <div class="image-wrapper" 
                       ref="imageWrapperRef"
                       :class="{ 'with-scroll': imageScale > 1, 'draggable': imageScale > 1 }"
                       @scroll="updateNavigatorViewport"
                       @mousedown="startImageDrag">
                    <img ref="imageRef"
                         :src="previewUrl" 
                         @error="onError('图片加载失败')" 
                         @load="onImageLoad"
                         :style="imageStyle" />
                  </div>
                  <div v-if="showNavigator" class="image-navigator">
                    <div class="navigator-title">鸟瞰图</div>
                    <div class="navigator-viewport"
                         ref="navigatorRef"
                         @mousedown="startNavigatorDrag">
                      <img :src="previewUrl" class="navigator-image" />
                      <div class="navigator-indicator"
                           :style="navigatorIndicatorStyle"
                           @mousedown.stop="startIndicatorDrag"></div>
                    </div>
                  </div>
                </div>
              </template>

              <template v-else-if="fileType === 'video'">
                <div class="video-preview-container">
                  <div class="video-toolbar" v-if="videoFiles.length > 1">
                    <el-button-group size="small">
                      <el-button @click="prevVideo" :disabled="!hasPrevVideo">
                        <el-icon><ArrowLeft /></el-icon>
                      </el-button>
                      <el-button>{{ currentVideoIndex + 1 }} / {{ videoFiles.length }}</el-button>
                      <el-button @click="nextVideo" :disabled="!hasNextVideo">
                        <el-icon><ArrowRight /></el-icon>
                      </el-button>
                    </el-button-group>
                    <el-button-group size="small">
                      <el-button @click="togglePlay">
                        <el-icon><VideoPlay v-if="!isVideoPlaying" /><VideoPause v-else /></el-icon>
                      </el-button>
                      <el-dropdown @command="setPlaybackRate" trigger="click">
                        <el-button>
                          {{ (videoPlaybackRate * 100).toFixed(0) }}%
                          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item 
                              v-for="rate in [0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 2.5, 3, 3.5, 4]"
                              :key="rate"
                              :command="rate"
                              :class="{ 'is-active': videoPlaybackRate === rate }">
                              {{ (rate * 100).toFixed(0) }}%
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </el-button-group>
                    <el-button size="small" @click="toggleVideoPlaylist" :type="showVideoPlaylist ? 'primary' : 'default'">
                      <el-icon><List /></el-icon> 播放列表
                    </el-button>
                  </div>
                  <div class="video-content">
                    <div class="video-main">
                      <video ref="videoRef" 
                             controls 
                             :src="previewUrl"
                             @error="onError('视频加载失败')"
                             @play="onVideoPlay"
                             @pause="onVideoPause"
                             @loadedmetadata="onVideoLoaded"
                             @timeupdate="onVideoTimeUpdate">
                        您的浏览器不支持视频播放
                      </video>
                    </div>
                    <div v-if="showVideoPlaylist && videoFiles.length > 1" class="video-playlist">
                      <div class="playlist-header">播放列表</div>
                      <div class="playlist-items">
                        <div 
                          v-for="(video, index) in videoFiles" 
                          :key="video.path"
                          class="playlist-item"
                          :class="{ active: index === currentVideoIndex }"
                          @click="playVideo(index)">
                          <span class="playlist-index">{{ index + 1 }}</span>
                          <span class="playlist-name">{{ video.name }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </template>

              <template v-else-if="fileType === 'audio'">
                <div class="audio-preview-container">
                  <div class="audio-toolbar" v-if="audioFiles.length > 1">
                    <el-button-group size="small">
                      <el-button @click="prevAudio" :disabled="!hasPrevAudio">
                        <el-icon><ArrowLeft /></el-icon>
                      </el-button>
                      <el-button>{{ currentAudioIndex + 1 }} / {{ audioFiles.length }}</el-button>
                      <el-button @click="nextAudio" :disabled="!hasNextAudio">
                        <el-icon><ArrowRight /></el-icon>
                      </el-button>
                    </el-button-group>
                    <el-button size="small" @click="togglePlaylist" :type="showPlaylist ? 'primary' : 'default'">
                      <el-icon><List /></el-icon> 播放列表
                    </el-button>
                  </div>
                  <div class="audio-content">
                    <div class="audio-main">
                      <div class="audio-cover">
                        <el-icon :size="80"><Headset /></el-icon>
                      </div>
                      <audio ref="audioRef" 
                             controls 
                             :src="previewUrl"
                             @loadeddata="onAudioLoaded"
                             @error="onAudioError"
                             @canplay="onAudioCanPlay"
                             @ended="onAudioEnded">
                        您的浏览器不支持音频播放
                      </audio>
                    </div>
                    <div v-if="showPlaylist && audioFiles.length > 1" class="audio-playlist">
                      <div class="playlist-header">播放列表</div>
                      <div class="playlist-items">
                        <div 
                          v-for="(audio, index) in audioFiles" 
                          :key="audio.path"
                          class="playlist-item"
                          :class="{ active: index === currentAudioIndex }"
                          @click="playAudio(index)">
                          <span class="playlist-index">{{ index + 1 }}</span>
                          <span class="playlist-name">{{ audio.name }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </template>

              <template v-else-if="fileType === 'pdf'">
                <div class="pdf-preview-container">
                  <div class="pdf-toolbar">
                    <el-button size="small" @click="openPdfInNewTab">
                      <el-icon><Link /></el-icon>
                      在新窗口打开
                    </el-button>
                    <span class="pdf-hint">如果PDF无法显示，请尝试在新窗口中打开</span>
                  </div>
                  <embed 
                    :src="previewUrl" 
                    type="application/pdf" 
                    width="100%" 
                    height="100%"
                    @error="onError('PDF加载失败')"
                  />
                </div>
              </template>

              <template v-else-if="fileType === 'document'">
                <div ref="documentRef" class="document-container"></div>
              </template>

              <template v-else-if="fileType === 'ppt'">
                <div class="ppt-notice">
                  <el-icon :size="64"><Document /></el-icon>
                  <h3>PPT 文件预览</h3>
                  <p>暂不支持在线预览 PPT 文件</p>
                  <el-button type="primary" size="large" @click="download">
                    <el-icon><Download /></el-icon>
                    下载查看
                  </el-button>
                </div>
              </template>

              <template v-else-if="fileType === 'epub'">
                <div class="epub-preview-container">
                  <div class="epub-header">
                    <div class="epub-info">
                      <span class="epub-page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
                      <span class="epub-section" v-if="currentSection">{{ currentSection }}</span>
                    </div>
                    <div class="epub-progress">
                      <el-progress 
                        :percentage="readingProgress" 
                        :show-text="false"
                        :stroke-width="4"
                      />
                      <span class="progress-text">{{ readingProgress.toFixed(1) }}%</span>
                    </div>
                  </div>
                  
                  <div class="epub-main">
                    <div class="epub-toc-sidebar" :class="{ show: showToc }">
                      <div class="toc-header">
                        <h3>目录</h3>
                        <el-button text size="small" @click="showToc = false">
                          <el-icon><Close /></el-icon>
                        </el-button>
                      </div>
                      <div class="toc-content">
                        <div 
                          v-for="(item, index) in epubToc" 
                          :key="index"
                          class="toc-item"
                          :class="{ active: currentSection === item.label }"
                          :style="{ paddingLeft: (item.depth || 0) * 16 + 'px' }"
                          @click="goToChapter(item.href)"
                        >
                          {{ item.label }}
                        </div>
                      </div>
                    </div>
                    
                    <div ref="epubRef" class="epub-container"></div>
                  </div>
                  
                  <div class="epub-footer">
                    <el-button-group>
                      <el-button size="small" @click="prevPage">
                        <el-icon><ArrowLeft /></el-icon>
                      </el-button>
                      <el-button size="small" @click="showToc = !showToc">
                        <el-icon><List /></el-icon>
                      </el-button>
                      <el-button size="small" @click="nextPage">
                        <el-icon><ArrowRight /></el-icon>
                      </el-button>
                    </el-button-group>
                    <div class="keyboard-hints">
                      <span>← → 翻页</span>
                      <span>↑ ↓ 目录</span>
                    </div>
                  </div>
                </div>
              </template>

              <template v-else-if="fileType === 'markdown'">
                <div class="markdown-body" v-html="renderedMarkdown"></div>
              </template>

              <template v-else-if="fileType === 'code'">
                <pre class="code-block"><code v-html="highlightedCode"></code></pre>
              </template>

              <template v-else-if="fileType === 'text'">
                <pre class="text-block">{{ textContent }}</pre>
              </template>

              <template v-else>
                <div class="unsupported">
                  <el-empty description="暂不支持预览此类型文件">
                    <el-button type="primary" @click="download">下载查看</el-button>
                  </el-empty>
                </div>
              </template>
            </div>
          </div>

          <div class="dialog-footer">
            <span class="file-info">{{ file?.name }} · {{ formatSize(file?.size) }}</span>
            <el-button size="small" @click="download">下载</el-button>
          </div>

          <div v-if="!isMaximized" class="resize-handle" @mousedown.stop="startResize"></div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  FullScreen, Close, CopyDocument, Loading, WarningFilled,
  Headset, ArrowLeft, ArrowRight, Download, Document,
  ZoomIn, ZoomOut, RefreshRight, Aim, List,
  VideoPlay, VideoPause, ArrowDown, Link
} from '@element-plus/icons-vue'
import { getFileTypeInfo, formatFileSize } from '@/utils/file-type'
import { downloadFile } from '@/api/file'
import 'highlight.js/styles/github-dark.css'

const props = defineProps({
  modelValue: Boolean,
  file: Object,
  serverId: Number,
  directoryFiles: Array
})

const emit = defineEmits(['update:modelValue', 'fileChange'])

const visible = ref(false)
const loading = ref(false)
const error = ref('')
const textContent = ref('')
const renderedMarkdown = ref('')
const highlightedCode = ref('')

const dialogRef = ref(null)
const videoRef = ref(null)
const audioRef = ref(null)
const documentRef = ref(null)
const epubRef = ref(null)
const epubBook = ref(null)
const epubRendition = ref(null)
const epubReady = ref(false)
const epubToc = ref([])
const showToc = ref(false)
const currentSection = ref('')
const readingProgress = ref(0)
const currentPage = ref(1)
const totalPages = ref(0)

const imageScale = ref(1)
const showNavigator = ref(true)
const showPlaylist = ref(false)
const showVideoPlaylist = ref(false)
const isVideoPlaying = ref(false)
const videoPlaybackRate = ref(1)
const videoVolume = ref(1)
const videoCurrentTime = ref(0)
const imageNaturalSize = ref({ width: 0, height: 0 })
const navigatorViewport = ref({ x: 0, y: 0, width: 100, height: 100 })
const isNavigatorDragging = ref(false)
const isImageDragging = ref(false)
const imageDragStart = ref({ x: 0, y: 0, scrollX: 0, scrollY: 0 })

const savedPos = ref(null)
const arrowKeyDown = ref({ left: false, right: false })
const arrowKeyTimer = ref(null)

watch(() => props.modelValue, async (val) => {
  visible.value = val
  if (val && props.file) {
    resetDialog()
    await loadPreview()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
  if (!val) cleanup()
})

watch(() => props.file, async (newFile, oldFile) => {
  if (visible.value && newFile && newFile.path !== oldFile?.path) {
    await loadPreview()
  }
})

const fileType = computed(() => {
  if (!props.file) return 'other'
  return getFileTypeInfo(props.file.name).type
})

const fileExt = computed(() => {
  return props.file?.name?.split('.').pop()?.toLowerCase() || ''
})

const imageFiles = computed(() => {
  if (!props.directoryFiles || !Array.isArray(props.directoryFiles)) return []
  return props.directoryFiles.filter(f => {
    if (f.isDirectory) return false
    const type = getFileTypeInfo(f.name).type
    return type === 'image'
  })
})

const currentImageIndex = computed(() => {
  if (!props.file || imageFiles.value.length === 0) return -1
  return imageFiles.value.findIndex(f => f.path === props.file.path)
})

const hasPrevImage = computed(() => currentImageIndex.value > 0)
const hasNextImage = computed(() => currentImageIndex.value >= 0 && currentImageIndex.value < imageFiles.value.length - 1)

const audioFiles = computed(() => {
  if (!props.directoryFiles || !Array.isArray(props.directoryFiles)) return []
  return props.directoryFiles.filter(f => {
    if (f.isDirectory) return false
    const type = getFileTypeInfo(f.name).type
    return type === 'audio'
  })
})

const currentAudioIndex = computed(() => {
  if (!props.file || audioFiles.value.length === 0) return -1
  return audioFiles.value.findIndex(f => f.path === props.file.path)
})

const hasPrevAudio = computed(() => currentAudioIndex.value > 0)
const hasNextAudio = computed(() => currentAudioIndex.value >= 0 && currentAudioIndex.value < audioFiles.value.length - 1)

const videoFiles = computed(() => {
  if (!props.directoryFiles || !Array.isArray(props.directoryFiles)) return []
  return props.directoryFiles.filter(f => {
    if (f.isDirectory) return false
    const type = getFileTypeInfo(f.name).type
    return type === 'video'
  })
})

const currentVideoIndex = computed(() => {
  if (!props.file || videoFiles.value.length === 0) return -1
  return videoFiles.value.findIndex(f => f.path === props.file.path)
})

const hasPrevVideo = computed(() => currentVideoIndex.value > 0)
const hasNextVideo = computed(() => currentVideoIndex.value >= 0 && currentVideoIndex.value < videoFiles.value.length - 1)

const previewUrl = computed(() => {
  if (!props.file || !props.serverId) return ''
  
  // For audio and video, use download endpoint which is more reliable
  if (fileType.value === 'audio' || fileType.value === 'video') {
    return `/api/files/${props.serverId}/download?path=${encodeURIComponent(props.file.path)}`
  }
  
  const endpoints = {
    image: 'image', pdf: 'pdf', text: 'text', code: 'text',
    markdown: 'text', epub: 'ebook', document: 'document'
  }
  const endpoint = endpoints[fileType.value] || 'text'
  let url = `/api/preview/${props.serverId}/${endpoint}?path=${encodeURIComponent(props.file.path)}`
  if (endpoint === 'ebook') url += `&filename=${encodeURIComponent(props.file.name)}`
  return url
})

const shouldFitHeight = computed(() => {
  return fileType.value === 'video'
})

const dialogStyle = computed(() => {
  if (isMaximized.value || isMobile.value) {
    return { top: 0, left: 0, width: '100vw', height: '100vh' }
  }
  return {
    top: `${dialogPos.value.y}%`,
    left: `${dialogPos.value.x}%`,
    width: `${dialogPos.value.width}%`,
    height: `${dialogPos.value.height}%`
  }
})

const navigatorIndicatorStyle = computed(() => {
  return {
    left: `${navigatorViewport.value.x}%`,
    top: `${navigatorViewport.value.y}%`,
    width: `${navigatorViewport.value.width}%`,
    height: `${navigatorViewport.value.height}%`
  }
})

const imageStyle = computed(() => {
  const style = {
    userSelect: 'none',
    WebkitUserDrag: 'none',
    objectFit: 'contain'
  }
  
  if (imageNaturalSize.value.width > 0 && imageNaturalSize.value.height > 0) {
    const displayWidth = imageNaturalSize.value.width * imageScale.value
    const displayHeight = imageNaturalSize.value.height * imageScale.value
    
    const result = {
      ...style,
      width: `${displayWidth}px`,
      height: `${displayHeight}px`
    }
    
    if (imageScale.value > 1) {
      result.cursor = isImageDragging.value ? 'grabbing' : 'grab'
    }
    
    return result
  }
  
  return style
})

function resetDialog() {
  dialogPos.value = { x: 10, y: 10, width: 80, height: 80 }
  isMaximized.value = false
}

function formatSize(size) {
  return formatFileSize(size)
}

async function loadPreview() {
  loading.value = true
  error.value = ''
  
  try {
    if (['text', 'code', 'markdown'].includes(fileType.value)) {
      const res = await fetch(previewUrl.value)
      textContent.value = await res.text()
      
      if (fileType.value === 'markdown') {
        const { marked } = await import('marked')
        renderedMarkdown.value = marked.parse(textContent.value)
      } else if (fileType.value === 'code') {
        const hljs = await import('highlight.js')
        const lang = getLanguage(fileExt.value)
        try {
          highlightedCode.value = hljs.default.highlight(textContent.value, { language: lang }).value
        } catch {
          highlightedCode.value = hljs.default.highlightAuto(textContent.value).value
        }
      }
    } else if (fileType.value === 'document') {
      // 先完成 loading，让 DOM 渲染 documentRef
      loading.value = false
      await nextTick()
      await new Promise(resolve => setTimeout(resolve, 100))
      await loadDocument()
      return
    } else if (fileType.value === 'epub') {
      loading.value = false
      await nextTick()
      await new Promise(resolve => setTimeout(resolve, 100))
      await loadEpub()
      return
    }
    
    await nextTick()
    if (videoRef.value) videoRef.value.load()
    if (audioRef.value) audioRef.value.load()
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadDocument() {
  if (!documentRef.value) {
    console.error('documentRef is null')
    return
  }
  
  try {
    console.log('Loading document:', fileExt.value, previewUrl.value)
    
    if (fileExt.value === 'docx') {
      const docx = await import('docx-preview')
      const res = await fetch(previewUrl.value)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const buffer = await res.arrayBuffer()
      console.log('docx buffer size:', buffer.byteLength)
      await docx.renderAsync(buffer, documentRef.value, null, {
        className: 'docx-wrapper',
        inWrapper: true,
        ignoreWidth: false,
        ignoreHeight: false
      })
      console.log('docx rendered')
    } else if (['xlsx', 'xls'].includes(fileExt.value)) {
      const XLSX = await import('xlsx')
      const res = await fetch(previewUrl.value)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const buffer = await res.arrayBuffer()
      console.log('xlsx buffer size:', buffer.byteLength)
      const wb = XLSX.read(buffer, { type: 'array' })
      const html = XLSX.utils.sheet_to_html(wb.Sheets[wb.SheetNames[0]])
      documentRef.value.innerHTML = `<div class="sheet-name">工作表: ${wb.SheetNames.join(', ')}</div>${html}`
      console.log('xlsx rendered')
    }
  } catch (e) {
    console.error('Document load error:', e)
    error.value = '文档加载失败: ' + e.message
  }
}

async function loadEpub() {
  if (!epubRef.value) return
  
  try {
    const ePub = (await import('epubjs')).default
    epubBook.value = ePub(previewUrl.value)
    
    epubRendition.value = epubBook.value.renderTo(epubRef.value, {
      width: '100%',
      height: '100%',
      spread: 'none'
    })
    
    await epubRendition.value.display()
    epubReady.value = true
    
    epubBook.value.loaded.spine.then(spine => {
      totalPages.value = spine.items.length
    })
    
    epubBook.value.loaded.navigation.then(nav => {
      epubToc.value = nav.toc
    })
    
    epubRendition.value.on('relocated', loc => {
      currentPage.value = loc.start.index + 1
      readingProgress.value = (currentPage.value / totalPages.value) * 100
      
      if (loc.start.href) {
        const section = epubToc.value.find(item => 
          item.href && loc.start.href.includes(item.href.split('#')[0])
        )
        currentSection.value = section ? section.label : ''
      }
    })
  } catch (e) {
    error.value = 'EPUB 加载失败: ' + e.message
  }
}

function goToChapter(href) {
  if (epubRendition.value && href) {
    epubRendition.value.display(href)
    showToc.value = false
  }
}

function prevPage() {
  if (epubRendition.value) epubRendition.value.prev()
}

function nextPage() {
  if (epubRendition.value) epubRendition.value.next()
}

function getLanguage(ext) {
  const map = {
    js: 'javascript', ts: 'typescript', py: 'python',
    rb: 'ruby', sh: 'bash', yml: 'yaml',
    rs: 'rust', kt: 'kotlin', go: 'go',
    xml: 'xml', html: 'html', htm: 'html',
    css: 'css', scss: 'scss', sass: 'sass',
    less: 'less', json: 'json', sql: 'sql',
    java: 'java', cpp: 'cpp', c: 'c',
    php: 'php', vue: 'vue', jsx: 'javascript',
    tsx: 'typescript', md: 'markdown'
  }
  return map[ext] || ext
}

function onError(msg) {
  error.value = msg
  loading.value = false
}

function onAudioLoaded() {
  console.log('Audio loaded successfully')
  loading.value = false
}

function onAudioCanPlay() {
  console.log('Audio can play')
  loading.value = false
}

function onAudioError(e) {
  console.error('Audio error:', e)
  const audio = audioRef.value
  if (audio && audio.error) {
    let errorMsg = '音频加载失败'
    switch (audio.error.code) {
      case MediaError.MEDIA_ERR_ABORTED:
        errorMsg = '音频加载被中止'
        break
      case MediaError.MEDIA_ERR_NETWORK:
        errorMsg = '网络错误，无法加载音频'
        break
      case MediaError.MEDIA_ERR_DECODE:
        errorMsg = '音频解码失败，可能是浏览器不支持的音频格式（如ADPCM压缩）'
        break
      case MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED:
        errorMsg = '不支持的音频格式，建议下载后使用专业播放器播放'
        break
    }
    error.value = errorMsg
  } else {
    error.value = '音频加载失败'
  }
  loading.value = false
}

function onAudioEnded() {
  if (hasNextAudio.value) {
    nextAudio()
  }
}

function togglePlaylist() {
  showPlaylist.value = !showPlaylist.value
}

function prevAudio() {
  if (!hasPrevAudio.value) return
  const prevFile = audioFiles.value[currentAudioIndex.value - 1]
  emit('fileChange', prevFile)
}

function nextAudio() {
  if (!hasNextAudio.value) return
  const nextFile = audioFiles.value[currentAudioIndex.value + 1]
  emit('fileChange', nextFile)
}

function playAudio(index) {
  if (index < 0 || index >= audioFiles.value.length) return
  const audio = audioFiles.value[index]
  emit('fileChange', audio)
}

function toggleVideoPlaylist() {
  showVideoPlaylist.value = !showVideoPlaylist.value
}

function prevVideo() {
  if (!hasPrevVideo.value) return
  const prevFile = videoFiles.value[currentVideoIndex.value - 1]
  emit('fileChange', prevFile)
}

function nextVideo() {
  if (!hasNextVideo.value) return
  const nextFile = videoFiles.value[currentVideoIndex.value + 1]
  emit('fileChange', nextFile)
}

function playVideo(index) {
  if (index < 0 || index >= videoFiles.value.length) return
  const video = videoFiles.value[index]
  emit('fileChange', video)
}

function togglePlay() {
  if (!videoRef.value) return
  if (videoRef.value.paused) {
    videoRef.value.play()
  } else {
    videoRef.value.pause()
  }
}

function setPlaybackRate(rate) {
  if (!videoRef.value) return
  videoPlaybackRate.value = rate
  videoRef.value.playbackRate = rate
}

function onVideoPlay() {
  isVideoPlaying.value = true
}

function onVideoPause() {
  isVideoPlaying.value = false
}

function onVideoLoaded() {
  if (videoRef.value) {
    videoRef.value.playbackRate = videoPlaybackRate.value
    videoRef.value.volume = videoVolume.value
  }
}

function onVideoTimeUpdate() {
  if (videoRef.value) {
    videoCurrentTime.value = videoRef.value.currentTime
  }
}

function startDrag(e) {
  if (isMaximized.value) return
  isDragging.value = true
  dragStart.value = { x: e.clientX, y: e.clientY }
  savedPos.value = { ...dialogPos.value }
  document.body.style.cursor = 'move'
  document.body.style.userSelect = 'none'
}

function startResize(e) {
  if (isMaximized.value) return
  isResizing.value = true
  resizeStart.value = { x: e.clientX, y: e.clientY, width: dialogPos.value.width, height: dialogPos.value.height }
  document.body.style.cursor = 'se-resize'
  document.body.style.userSelect = 'none'
}

function onMouseMove(e) {
  if (isDragging.value && savedPos.value) {
    const dx = ((e.clientX - dragStart.value.x) / window.innerWidth) * 100
    const dy = ((e.clientY - dragStart.value.y) / window.innerHeight) * 100
    dialogPos.value.x = Math.max(0, Math.min(100 - savedPos.value.width, savedPos.value.x + dx))
    dialogPos.value.y = Math.max(0, Math.min(100 - savedPos.value.height, savedPos.value.y + dy))
  }
  
  if (isResizing.value) {
    const dx = ((e.clientX - resizeStart.value.x) / window.innerWidth) * 100
    const dy = ((e.clientY - resizeStart.value.y) / window.innerHeight) * 100
    dialogPos.value.width = Math.max(30, Math.min(95, resizeStart.value.width + dx))
    dialogPos.value.height = Math.max(30, Math.min(95, resizeStart.value.height + dy))
  }
  
  if (isImageDragging.value && imageWrapperRef.value) {
    const dx = e.clientX - imageDragStart.value.x
    const dy = e.clientY - imageDragStart.value.y
    
    imageWrapperRef.value.scrollLeft = imageDragStart.value.scrollX - dx
    imageWrapperRef.value.scrollTop = imageDragStart.value.scrollY - dy
  }
}

function onMouseUp() {
  isDragging.value = false
  isResizing.value = false
  isImageDragging.value = false
  savedPos.value = null
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

function toggleMaximize() {
  if (!isMaximized.value) {
    savedPos.value = { ...dialogPos.value }
  }
  isMaximized.value = !isMaximized.value
  if (!isMaximized.value && savedPos.value) {
    dialogPos.value = savedPos.value
  }
}

function zoomIn() {
  if (imageScale.value >= 4) return
  imageScale.value = Math.min(4, imageScale.value + 0.25)
  nextTick(() => updateNavigatorViewport())
}

function zoomOut() {
  if (imageScale.value <= 0.25) return
  imageScale.value = Math.max(0.25, imageScale.value - 0.25)
  nextTick(() => updateNavigatorViewport())
}

function resetZoom() {
  imageScale.value = 1
  navigatorViewport.value = { x: 0, y: 0, width: 100, height: 100 }
}

function toggleNavigator() {
  showNavigator.value = !showNavigator.value
}

function prevImage() {
  if (!hasPrevImage.value) return
  imageScale.value = 1 // Reset scale before changing image
  const prevFile = imageFiles.value[currentImageIndex.value - 1]
  emit('fileChange', prevFile)
}

function nextImage() {
  if (!hasNextImage.value) return
  imageScale.value = 1 // Reset scale before changing image
  const nextFile = imageFiles.value[currentImageIndex.value + 1]
  emit('fileChange', nextFile)
}

function onImageLoad(e) {
  imageNaturalSize.value = {
    width: e.target.naturalWidth,
    height: e.target.naturalHeight
  }
  
  // Calculate optimal initial scale based on container and image size
  if (imageWrapperRef.value && imageRef.value) {
    const wrapper = imageWrapperRef.value
    const img = imageRef.value
    
    // Get container size (image-wrapper already accounts for toolbar via flex)
    const containerWidth = wrapper.clientWidth
    const containerHeight = wrapper.clientHeight
    
    // Get image natural size
    const imgWidth = img.naturalWidth
    const imgHeight = img.naturalHeight
    
    // Calculate scale to fit image in container
    const scaleX = containerWidth / imgWidth
    const scaleY = containerHeight / imgHeight
    
    // Use the smaller scale to ensure image fits completely
    const optimalScale = Math.min(scaleX, scaleY, 1) // Don't exceed 1 (100%)
    
    // Set initial scale
    imageScale.value = Math.max(0.25, Math.min(1, optimalScale))
    
    console.log(`Image loaded: ${imgWidth}x${imgHeight}, container: ${containerWidth}x${containerHeight}, optimal scale: ${imageScale.value.toFixed(2)}`)
  }
  
  updateNavigatorViewport()
}

function updateNavigatorViewport() {
  if (!imageWrapperRef.value || !imageRef.value) {
    navigatorViewport.value = { x: 0, y: 0, width: 100, height: 100 }
    return
  }
  
  const wrapper = imageWrapperRef.value
  const img = imageRef.value
  
  // When scale is 1 or less, show full image in navigator
  if (imageScale.value <= 1) {
    navigatorViewport.value = { x: 0, y: 0, width: 100, height: 100 }
    return
  }
  
  const scaledWidth = img.naturalWidth * imageScale.value
  const scaledHeight = img.naturalHeight * imageScale.value
  
  const viewportWidthPercent = Math.min(100, (wrapper.clientWidth / scaledWidth) * 100)
  const viewportHeightPercent = Math.min(100, (wrapper.clientHeight / scaledHeight) * 100)
  
  const maxScrollX = Math.max(0, scaledWidth - wrapper.clientWidth)
  const maxScrollY = Math.max(0, scaledHeight - wrapper.clientHeight)
  
  const scrollXPercent = maxScrollX > 0 ? (wrapper.scrollLeft / maxScrollX) * (100 - viewportWidthPercent) : 0
  const scrollYPercent = maxScrollY > 0 ? (wrapper.scrollTop / maxScrollY) * (100 - viewportHeightPercent) : 0
  
  navigatorViewport.value = {
    x: scrollXPercent,
    y: scrollYPercent,
    width: viewportWidthPercent,
    height: viewportHeightPercent
  }
}

function navigatorClick(e) {
  if (isNavigatorDragging.value) return
  if (imageScale.value <= 1) return
  
  const rect = navigatorRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  
  const percentX = (x / rect.width) * 100
  const percentY = (y / rect.height) * 100
  
  scrollToNavigatorPosition(percentX, percentY)
}

function startNavigatorDrag(e) {
  if (imageScale.value <= 1) return
  
  isNavigatorDragging.value = true
  
  const rect = navigatorRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  
  const percentX = (x / rect.width) * 100
  const percentY = (y / rect.height) * 100
  
  scrollToNavigatorPosition(percentX, percentY)
  
  const onMouseMove = (e) => {
    const rect = navigatorRef.value.getBoundingClientRect()
    const x = e.clientX - rect.left
    const y = e.clientY - rect.top
    
    const percentX = (x / rect.width) * 100
    const percentY = (y / rect.height) * 100
    
    scrollToNavigatorPosition(percentX, percentY)
  }
  
  const onMouseUp = () => {
    isNavigatorDragging.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }
  
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

function scrollToNavigatorPosition(percentX, percentY) {
  if (!imageWrapperRef.value || !imageRef.value || imageScale.value <= 1) return
  
  const wrapper = imageWrapperRef.value
  const img = imageRef.value
  
  const scaledWidth = img.naturalWidth * imageScale.value
  const scaledHeight = img.naturalHeight * imageScale.value
  
  const maxScrollX = Math.max(0, scaledWidth - wrapper.clientWidth)
  const maxScrollY = Math.max(0, scaledHeight - wrapper.clientHeight)
  
  const targetX = (percentX / 100) * scaledWidth - wrapper.clientWidth / 2
  const targetY = (percentY / 100) * scaledHeight - wrapper.clientHeight / 2
  
  wrapper.scrollTo({
    left: Math.max(0, Math.min(maxScrollX, targetX)),
    top: Math.max(0, Math.min(maxScrollY, targetY)),
    behavior: 'smooth'
  })
}

function startImageDrag(e) {
  if (imageScale.value <= 1) return
  if (e.target.tagName !== 'IMG') return
  
  isImageDragging.value = true
  imageDragStart.value = {
    x: e.clientX,
    y: e.clientY,
    scrollX: imageWrapperRef.value.scrollLeft,
    scrollY: imageWrapperRef.value.scrollTop
  }
  
  document.body.style.cursor = 'grabbing'
  document.body.style.userSelect = 'none'
  e.preventDefault()
}

function startIndicatorDrag(e) {
  if (imageScale.value <= 1) return
  
  isNavigatorDragging.value = true
  
  const rect = navigatorRef.value.getBoundingClientRect()
  const indicatorWidth = (navigatorViewport.value.width / 100) * rect.width
  const indicatorHeight = (navigatorViewport.value.height / 100) * rect.height
  
  const startPercentX = navigatorViewport.value.x + (navigatorViewport.value.width / 2)
  const startPercentY = navigatorViewport.value.y + (navigatorViewport.value.height / 2)
  
  const onMouseMove = (e) => {
    const rect = navigatorRef.value.getBoundingClientRect()
    const x = e.clientX - rect.left
    const y = e.clientY - rect.top
    
    const percentX = (x / rect.width) * 100
    const percentY = (y / rect.height) * 100
    
    scrollToNavigatorPosition(percentX, percentY)
  }
  
  const onMouseUp = () => {
    isNavigatorDragging.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }
  
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
  e.stopPropagation()
  e.preventDefault()
}

function close() {
  visible.value = false
}

function download() {
  if (props.file && props.serverId) {
    window.open(downloadFile(props.serverId, props.file.path), '_blank')
  }
}

function openPdfInNewTab() {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

function cleanup() {
  if (videoRef.value) { videoRef.value.pause(); videoRef.value.src = '' }
  if (audioRef.value) { audioRef.value.pause(); audioRef.value.src = '' }
  if (epubRendition.value) epubRendition.value.destroy()
  if (epubBook.value) epubBook.value.destroy()
  textContent.value = ''
  renderedMarkdown.value = ''
  highlightedCode.value = ''
  error.value = ''
  loading.value = false
  epubReady.value = false
  epubToc.value = []
  showToc.value = false
  currentSection.value = ''
  readingProgress.value = 0
  currentPage.value = 1
  totalPages.value = 0
  imageScale.value = 1
  imageNaturalSize.value = { width: 0, height: 0 }
  navigatorViewport.value = { x: 0, y: 0, width: 100, height: 100 }
}

onMounted(() => {
  // Detect mobile device
  isMobile.value = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent) || window.innerWidth < 768
  
  // Listen for resize
  window.addEventListener('resize', () => {
    isMobile.value = window.innerWidth < 768
  })
  
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('keyup', handleKeyup)
})

onUnmounted(() => {
  window.removeEventListener('resize', () => {})
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('keyup', handleKeyup)
  if (arrowKeyTimer.value) {
    clearInterval(arrowKeyTimer.value)
  }
  cleanup()
})

function handleKeydown(e) {
  if (!visible.value) return
  
  // Video controls
  if (fileType.value === 'video' && videoRef.value) {
    if (e.key === ' ') {
      e.preventDefault()
      togglePlay()
      return
    } else if (e.key === 'ArrowLeft') {
      if (e.repeat) {
        // Long press - rewind
        if (!arrowKeyDown.value.left) {
          arrowKeyDown.value.left = true
          arrowKeyTimer.value = setInterval(() => {
            if (videoRef.value) {
              videoRef.value.currentTime = Math.max(0, videoRef.value.currentTime - 5)
            }
          }, 200)
        }
      } else {
        // Single press - previous video
        if (videoFiles.value.length > 1) {
          prevVideo()
        }
      }
      e.preventDefault()
      return
    } else if (e.key === 'ArrowRight') {
      if (e.repeat) {
        // Long press - fast forward
        if (!arrowKeyDown.value.right) {
          arrowKeyDown.value.right = true
          arrowKeyTimer.value = setInterval(() => {
            if (videoRef.value) {
              videoRef.value.currentTime = Math.min(videoRef.value.duration, videoRef.value.currentTime + 5)
            }
          }, 200)
        }
      } else {
        // Single press - next video
        if (videoFiles.value.length > 1) {
          nextVideo()
        }
      }
      e.preventDefault()
      return
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      videoVolume.value = Math.min(1, videoVolume.value + 0.1)
      if (videoRef.value) videoRef.value.volume = videoVolume.value
      return
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      videoVolume.value = Math.max(0, videoVolume.value - 0.1)
      if (videoRef.value) videoRef.value.volume = videoVolume.value
      return
    }
  }
  
  if (e.key === 'ArrowLeft') {
    if (fileType.value === 'image') {
      prevImage()
    } else if (fileType.value === 'audio') {
      prevAudio()
    } else if (fileType.value === 'epub') {
      prevPage()
    }
  } else if (e.key === 'ArrowRight') {
    if (fileType.value === 'image') {
      nextImage()
    } else if (fileType.value === 'audio') {
      nextAudio()
    } else if (fileType.value === 'epub') {
      nextPage()
    }
  } else if (e.key === 'ArrowUp') {
    if (fileType.value === 'image') {
      e.preventDefault()
      zoomIn()
    } else if (fileType.value === 'epub') {
      e.preventDefault()
      showToc.value = true
    }
  } else if (e.key === 'ArrowDown') {
    if (fileType.value === 'image') {
      e.preventDefault()
      zoomOut()
    } else if (fileType.value === 'epub') {
      e.preventDefault()
      showToc.value = false
    }
  } else if (e.key === 'Escape') {
    close()
  }
}

function handleKeyup(e) {
  if (e.key === 'ArrowLeft') {
    arrowKeyDown.value.left = false
    if (arrowKeyTimer.value) {
      clearInterval(arrowKeyTimer.value)
      arrowKeyTimer.value = null
    }
  } else if (e.key === 'ArrowRight') {
    arrowKeyDown.value.right = false
    if (arrowKeyTimer.value) {
      clearInterval(arrowKeyTimer.value)
      arrowKeyTimer.value = null
    }
  }
}
</script>

<style scoped>
.preview-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.preview-dialog {
  position: fixed;
  background: var(--el-bg-color);
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.2s, height 0.2s;
}

.preview-dialog.maximized {
  border-radius: 0;
  transition: none;
}

.preview-dialog.dragging {
  transition: none;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--el-color-primary);
  color: #fff;
  cursor: move;
  user-select: none;
  flex-shrink: 0;
}

.dialog-title {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dialog-controls {
  display: flex;
  gap: 4px;
}

.dialog-controls .el-button {
  color: #fff;
}

.dialog-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.loading-state,
.error-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--el-text-color-secondary);
}

.preview-content {
  flex: 1;
  overflow: hidden;
  padding: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.preview-content.fit-height {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.preview-content.fit-height video {
  max-width: 100%;
  max-height: 100%;
}

.image-preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  position: relative;
}

.image-toolbar {
  display: flex;
  justify-content: center;
  padding: 8px 0;
  flex-shrink: 0;
  background: var(--el-bg-color);
  z-index: 10;
}

.image-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  overflow: auto;
}

.image-wrapper.with-scroll {
  align-items: flex-start;
  justify-content: flex-start;
}

.image-wrapper.draggable {
  cursor: grab;
}

.image-wrapper.draggable:active {
  cursor: grabbing;
}

.image-wrapper img {
  display: block;
  object-fit: contain;
}

.image-navigator {
  position: absolute;
  right: 16px;
  bottom: 16px;
  background: rgba(0, 0, 0, 0.85);
  border-radius: 8px;
  padding: 8px;
  z-index: 100;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.navigator-title {
  color: #fff;
  font-size: 12px;
  margin-bottom: 8px;
  text-align: center;
}

.navigator-viewport {
  width: 150px;
  height: 100px;
  position: relative;
  cursor: crosshair;
  border-radius: 4px;
  overflow: hidden;
  background: #000;
}

.navigator-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  opacity: 0.8;
}

.navigator-indicator {
  position: absolute;
  border: 2px solid #409eff;
  background: rgba(64, 158, 255, 0.2);
  pointer-events: none;
  box-shadow: 0 0 4px rgba(64, 158, 255, 0.5);
}

.video-preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
}

.video-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 0;
  flex-shrink: 0;
}

.video-content {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
}

.video-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-main video {
  max-width: 100%;
  max-height: 100%;
  outline: none;
}

.video-playlist {
  width: 280px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.video-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.video-container.fit-height {
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-container video {
  max-width: 100%;
  max-height: 100%;
}

.preview-content iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.audio-preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
}

.audio-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 0;
  flex-shrink: 0;
}

.audio-content {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
}

.audio-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.audio-cover {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 200px;
  height: 200px;
  margin: 40px auto;
  background: var(--el-fill-color-light);
  border-radius: 12px;
  color: var(--el-color-primary);
}

audio {
  display: block;
  width: 100%;
  max-width: 400px;
  margin: 0 auto;
}

.audio-playlist {
  width: 280px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.playlist-header {
  padding: 12px 16px;
  font-weight: 500;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.playlist-items {
  flex: 1;
  overflow: auto;
  padding: 8px;
}

.playlist-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.playlist-item:hover {
  background: var(--el-fill-color);
}

.playlist-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.playlist-index {
  width: 24px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
  flex-shrink: 0;
}

.playlist-item.active .playlist-index {
  color: var(--el-color-primary);
}

.playlist-name {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-container {
  width: 100%;
  height: 100%;
  overflow: auto;
  background: #fff;
}

.document-container :deep(.sheet-name) {
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  margin-bottom: 8px;
  font-size: 13px;
}

.document-container :deep(table) {
  border-collapse: collapse;
  width: 100%;
}

.document-container :deep(td) {
  border: 1px solid var(--el-border-color);
  padding: 8px;
}

.epub-preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
}

.epub-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--el-fill-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.epub-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.epub-page-info {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.epub-section {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.epub-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  max-width: 400px;
  margin: 0 20px;
}

.epub-progress .el-progress {
  flex: 1;
}

.progress-text {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  min-width: 50px;
  text-align: right;
}

.epub-main {
  flex: 1;
  display: flex;
  min-height: 0;
  position: relative;
}

.epub-toc-sidebar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 300px;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  transform: translateX(-100%);
  transition: transform 0.3s ease;
  z-index: 10;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
}

.epub-toc-sidebar.show {
  transform: translateX(0);
}

.toc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.toc-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}

.toc-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.toc-item {
  padding: 10px 16px;
  cursor: pointer;
  font-size: 14px;
  color: var(--el-text-color-primary);
  transition: all 0.2s;
  border-left: 3px solid transparent;
}

.toc-item:hover {
  background: var(--el-fill-color-light);
  border-left-color: var(--el-color-primary);
}

.toc-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 500;
  border-left-color: var(--el-color-primary);
}

.epub-container {
  flex: 1;
  width: 100%;
  height: 100%;
}

.epub-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--el-fill-color-lighter);
  border-top: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.keyboard-hints {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.keyboard-hints span {
  padding: 4px 8px;
  background: var(--el-fill-color);
  border-radius: 4px;
}

.markdown-body {
  line-height: 1.8;
}

.markdown-body :deep(h1) { font-size: 28px; margin: 1em 0 0.5em; }
.markdown-body :deep(h2) { font-size: 22px; margin: 1em 0 0.5em; }
.markdown-body :deep(h3) { font-size: 18px; margin: 1em 0 0.5em; }
.markdown-body :deep(pre) { background: var(--el-fill-color-dark); padding: 16px; border-radius: 6px; overflow-x: auto; }
.markdown-body :deep(code) { font-family: monospace; font-size: 13px; }
.markdown-body :deep(a) { color: var(--el-color-primary); }

.code-block {
  margin: 0;
  padding: 16px;
  background: #1e1e1e;
  border-radius: 6px;
  overflow: auto;
  min-height: 100%;
}

.code-block code {
  font-family: 'Fira Code', 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #d4d4d4;
  background: transparent;
}

.code-block code .hljs-keyword,
.code-block code .hljs-selector-tag,
.code-block code .hljs-built_in,
.code-block code .hljs-name,
.code-block code .hljs-tag {
  color: #569cd6;
}

.code-block code .hljs-string,
.code-block code .hljs-title,
.code-block code .hljs-section,
.code-block code .hljs-attribute,
.code-block code .hljs-literal,
.code-block code .hljs-template-tag,
.code-block code .hljs-template-variable,
.code-block code .hljs-type {
  color: #ce9178;
}

.code-block code .hljs-comment,
.code-block code .hljs-deletion {
  color: #6a9955;
}

.code-block code .hljs-number,
.code-block code .hljs-regexp,
.code-block code .hljs-addition {
  color: #b5cea8;
}

.code-block code .hljs-function {
  color: #dcdcaa;
}

.code-block code .hljs-variable,
.code-block code .hljs-params {
  color: #9cdcfe;
}

.code-block code .hljs-class .hljs-title,
.code-block code .hljs-title.class_ {
  color: #4ec9b0;
}

.code-block code .hljs-attr {
  color: #9cdcfe;
}

.code-block code .hljs-meta {
  color: #808080;
}

.text-block {
  margin: 0;
  padding: 16px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: monospace;
  font-size: 13px;
  line-height: 1.6;
}

.unsupported {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.pdf-preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
}

.pdf-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--el-fill-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.pdf-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-left: 12px;
}

.pdf-preview-container embed {
  flex: 1;
  min-height: 0;
}

.ppt-notice {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 16px;
  color: var(--el-text-color-secondary);
}

.ppt-notice h3 {
  margin: 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
}

.ppt-notice p {
  margin: 0;
  font-size: 14px;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.file-info {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 16px;
  height: 16px;
  cursor: se-resize;
}

.resize-handle::before {
  content: '';
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 8px;
  height: 8px;
  border-right: 2px solid var(--el-border-color);
  border-bottom: 2px solid var(--el-border-color);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Mobile Responsive Styles */
@media (max-width: 767px) {
  .preview-dialog {
    border-radius: 0;
  }

  .dialog-header {
    padding: 10px 12px;
  }

  .dialog-title {
    font-size: 14px;
  }

  .dialog-body {
    padding: 8px !important;
  }

  .dialog-footer {
    padding: 10px 12px;
  }

  .image-toolbar,
  .video-toolbar,
  .audio-toolbar {
    flex-wrap: wrap;
    padding: 6px 8px;
  }

  .image-toolbar .el-button-group,
  .video-toolbar .el-button-group,
  .audio-toolbar .el-button-group {
    margin-bottom: 4px;
  }

  .image-toolbar .el-button,
  .video-toolbar .el-button,
  .audio-toolbar .el-button {
    padding: 6px 10px;
    font-size: 12px;
  }

  .audio-cover {
    width: 120px;
    height: 120px;
    margin: 20px auto;
  }

  .audio-cover .el-icon {
    font-size: 48px !important;
  }

  audio {
    max-width: 100%;
  }

  .audio-playlist,
  .video-playlist {
    width: 100%;
    max-height: 200px;
  }

  .playlist-item {
    padding: 8px 10px;
    font-size: 12px;
  }

  .image-navigator {
    width: 100px;
    padding: 6px;
  }

  .navigator-title {
    font-size: 10px;
    margin-bottom: 4px;
  }

  .navigator-viewport {
    width: 88px;
    height: 60px;
  }

  .code-block {
    padding: 12px;
    font-size: 11px;
  }

  .markdown-body {
    padding: 12px;
    font-size: 13px;
  }

  .video-main video {
    width: 100%;
    height: auto;
  }

  .el-button {
    padding: 6px 10px;
    font-size: 13px;
  }

  .el-button-group .el-button {
    padding: 5px 8px;
    font-size: 12px;
  }
}

/* Tablet Styles */
@media (min-width: 768px) and (max-width: 1023px) {
  .preview-dialog {
    width: 90vw !important;
    height: 90vh !important;
  }

  .audio-playlist,
  .video-playlist {
    width: 220px;
  }
}

/* Touch Device Optimizations */
@media (hover: none) and (pointer: coarse) {
  .image-wrapper.draggable {
    cursor: default;
  }

  .image-wrapper img {
    touch-action: pan-x pan-y;
  }

  .el-button {
    min-height: 32px;
    min-width: 32px;
  }

  .playlist-item {
    min-height: 40px;
    padding: 10px 12px;
  }
}
</style>