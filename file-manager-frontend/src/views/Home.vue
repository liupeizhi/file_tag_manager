<template>
  <div class="home-container">
    <AppHeader />
    <div class="main-content">
      <div class="sidebar-wrapper" :style="{ width: isSidebarCollapsed ? '0px' : sidebarWidth + 'px' }">
        <div class="sidebar" v-show="!isSidebarCollapsed">
          <el-tabs v-model="sidebarTab" class="sidebar-tabs">
            <el-tab-pane label="目录" name="directory">
              <FileTree />
            </el-tab-pane>
            <el-tab-pane label="标签" name="tags">
              <TagTree ref="tagTreeRef" @tag-click="handleTagClick" />
            </el-tab-pane>
          </el-tabs>
        </div>
        <div class="collapse-btn" @click="toggleSidebar" v-show="!isSidebarCollapsed">
          <el-icon :size="16">
            <ArrowLeft />
          </el-icon>
        </div>
        <div 
          class="resize-handle" 
          @mousedown="startResize"
          :class="{ active: isResizing }"
          v-show="!isSidebarCollapsed"
        ></div>
      </div>
      <div class="collapse-btn collapsed-btn" @click="toggleSidebar" v-show="isSidebarCollapsed">
        <el-icon :size="16">
          <ArrowRight />
        </el-icon>
      </div>
      <div class="content">
        <FileList ref="fileListRef" @preview="handlePreview" />
      </div>
    </div>
    <FilePreview 
      v-model="showPreview" 
      :file="previewFile"
      :server-id="serverStore.currentServer?.id"
      :directory-files="fileStore.fileList"
      @file-change="handleFileChange"
    />
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import FileTree from '@/components/file/FileTree.vue'
import FileList from '@/components/file/FileList.vue'
import TagTree from '@/components/tag/TagTree.vue'
import FilePreview from '@/components/preview/FilePreview.vue'
import { getFilesByTagWithDetails } from '@/api/tag'
import { useServerStore } from '@/store/server'
import { useFileStore } from '@/store/file'

const serverStore = useServerStore()
const fileStore = useFileStore()

const sidebarTab = ref('directory')
const showPreview = ref(false)
const previewFile = ref(null)
const tagTreeRef = ref(null)
const fileListRef = ref(null)

const sidebarWidth = ref(250)
const isResizing = ref(false)
const isSidebarCollapsed = ref(false)
const minSidebarWidth = 180
const maxSidebarWidth = 500

watch(sidebarTab, (newVal) => {
  if (newVal === 'directory' && fileListRef.value) {
    fileListRef.value.exitTagFilterMode()
  }
})

async function handleTagClick(tag) {
  console.log('Tag clicked:', tag)
  if (!fileListRef.value) return
  
  try {
    fileListRef.value.setTagFilterMode(true, tag)
    const files = await getFilesByTagWithDetails(tag.id, serverStore.currentServer?.id)
    console.log('Files by tag:', files)
    fileListRef.value.setTagFilteredFiles(files || [])
  } catch (error) {
    console.error('Failed to get files by tag:', error)
    fileListRef.value.setTagFilterMode(false)
  }
}

function handlePreview(file) {
  previewFile.value = file
  showPreview.value = true
}

function handleFileChange(newFile) {
  previewFile.value = newFile
}

function toggleSidebar() {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

function startResize(e) {
  isResizing.value = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function handleResize(e) {
  if (!isResizing.value) return
  
  const newWidth = e.clientX
  if (newWidth >= minSidebarWidth && newWidth <= maxSidebarWidth) {
    sidebarWidth.value = newWidth
  }
}

function stopResize() {
  isResizing.value = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onMounted(() => {
  document.addEventListener('mousemove', handleResize)
  document.addEventListener('mouseup', stopResize)
})

onUnmounted(() => {
  document.removeEventListener('mousemove', handleResize)
  document.removeEventListener('mouseup', stopResize)
})
</script>

<style scoped>
.home-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  background: var(--canvas-bg);
  transition: background var(--transition-normal);
}

.main-content {
  flex: 1;
  display: flex;
  gap: 12px;
  overflow: hidden;
  position: relative;
  min-height: 0;
}

.sidebar-wrapper {
  position: relative;
  flex-shrink: 0;
  transition: width 0.15s ease;
  display: flex;
  min-height: 0;
}

.sidebar {
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--theme-surface);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.sidebar-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.el-tabs__header) {
  margin: 0;
  padding: 0 16px;
  background: transparent;
}

:deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
}

:deep(.el-tab-pane) {
  height: 100%;
  overflow-y: auto;
}

.resize-handle {
  width: 5px;
  background: transparent;
  cursor: col-resize;
  flex-shrink: 0;
  transition: background 0.15s;
  position: relative;
}

.resize-handle:hover,
.resize-handle.active {
  background: var(--theme-primary);
}

.resize-handle::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: -5px;
  right: -5px;
}

/*
 * 折叠按钮: 垂直居中于内容区, 右半部分压在 content 卡片下方,
 * 图标左移 2px 以避开被卡片遮挡的区域。
 * z-index 介于 resize-handle(0) 与 content(2) 之间 —— 保证可点击但被卡片压住。
 */
.collapse-btn {
  width: 24px;
  height: 24px;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: var(--radius-input);
  display: flex;
  align-items: center;
  justify-content: center;
  padding-right: 4px;
  cursor: pointer;
  position: absolute;
  right: -16px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1;
  color: var(--text-muted);
  transition: color var(--transition-fast), border-color var(--transition-fast);
}

.collapse-btn:hover {
  border-color: var(--text-primary);
  color: var(--text-primary);
}

.collapsed-btn {
  /* 右移一点, 避免按钮被视口左边缘切掉 */
  left: -6px;
  right: auto;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  padding-right: 0;
  border-radius: var(--radius-input);
  position: absolute;
  /* 折叠态下按钮完全落在 content 卡片上, 需高于 content(2) 才不会被盖住 */
  z-index: 3;
}

.content {
  flex: 1;
  overflow: hidden;
  min-width: 0;
  display: flex;
  flex-direction: column;
  /* 建立独立层级, 使折叠按钮压在 content 卡片下方 */
  position: relative;
  z-index: 2;
}

.content > * {
  flex: 1;
  min-height: 0;
}
</style>