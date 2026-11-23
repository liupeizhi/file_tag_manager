<template>
  <div class="home-container">
    <AppHeader
      @open-tag-graph="openTagGraph"
      @open-dir-graph="openServerDirGraph"
    />
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
            <el-tab-pane label="历史" name="history">
              <HistoryList @navigate="handleHistoryNavigate" @preview="handleHistoryPreview" />
            </el-tab-pane>
            <el-tab-pane label="收藏" name="favorites">
              <FavoriteList @navigate="handleHistoryNavigate" @preview="handleHistoryPreview" />
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
        <FileList
          ref="fileListRef"
          @preview="handlePreview"
          @open-dir-graph="openCurrentDirGraph"
          @open-tag-graph="openCurrentTagGraph"
          @open-file-graph="openFileGraph"
        />
      </div>
    </div>
    <FilePreview 
      v-model="showPreview" 
      :file="previewFile"
      :server-id="serverStore.currentServer?.id"
      :directory-files="fileStore.fileList"
      @file-change="handleFileChange"
    />

    <RelationGraphDialog
      ref="tagGraphRef"
      title="标签节点关系图"
      hint="原点 → 标签组 → 标签层级"
      root-label="原点"
      :legend-items="tagLegend"
      :loader="loadTagGraph"
      @add-child="handleAddChildTag"
      @remove-node="handleRemoveTagNode"
    />

    <RelationGraphDialog
      ref="dirGraphRef"
      title="目录节点关系图"
      hint="服务器根目录 → 目录与文件"
      root-label="服务器"
      :legend-items="dirLegend"
      :loader="loadDirectoryGraph"
    />

    <RelationGraphDialog
      ref="fileGraphRef"
      title="文件关系图"
      hint="文件 → 所在目录层级 / 所属标签层级"
      root-label="文件"
      default-layout="tree"
      :legend-items="fileLegend"
      :loader="loadFileGraph"
    />
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppHeader from '@/components/layout/AppHeader.vue'
import FileTree from '@/components/file/FileTree.vue'
import FileList from '@/components/file/FileList.vue'
import TagTree from '@/components/tag/TagTree.vue'
import HistoryList from '@/components/history/HistoryList.vue'
import FavoriteList from '@/components/favorite/FavoriteList.vue'
import FilePreview from '@/components/preview/FilePreview.vue'
import RelationGraphDialog from '@/components/graph/RelationGraphDialog.vue'
import { loadTagGraph, loadDirectoryGraph, loadFileGraph } from '@/utils/graph-loaders'
import { getFilesByTagWithDetails, createTag, deleteTag } from '@/api/tag'
import { useServerStore } from '@/store/server'
import { useFileStore } from '@/store/file'
import { useHistoryStore } from '@/store/history'
import { useFavoriteStore } from '@/store/favorite'

const serverStore = useServerStore()
const fileStore = useFileStore()
const historyStore = useHistoryStore()
const favoriteStore = useFavoriteStore()

const sidebarTab = ref('directory')
const showPreview = ref(false)
const previewFile = ref(null)
const tagTreeRef = ref(null)
const fileListRef = ref(null)

const tagGraphRef = ref(null)
const dirGraphRef = ref(null)
const fileGraphRef = ref(null)

const tagLegend = [
  { label: '标签组', dotClass: 'dot-group' },
  { label: '标签', dotClass: 'dot-tag' }
]
const dirLegend = [
  { label: '文件夹', dotClass: 'dot-group' },
  { label: '文件', dotClass: 'dot-tag' }
]
const fileLegend = [
  { label: '目录', dotClass: 'dot-group' },
  { label: '标签', dotClass: 'dot-tag' }
]

/** 顶栏: 标签关系图 */
function openTagGraph() {
  tagGraphRef.value?.open()
}

/** 顶栏: 当前服务器的目录关系图(从根目录开始) */
function openServerDirGraph() {
  const server = serverStore.currentServer
  dirGraphRef.value?.open({
    serverId: server ? server.id : null,
    serverName: server ? server.name : '',
    basePath: '/'
  })
}

/** 标签关系图: 点节点上的 "+" 新增子标签 */
async function handleAddChildTag(node) {
  const meta = node?.add
  if (!meta || meta.tagId == null) return

  let name
  try {
    const res = await ElMessageBox.prompt('请输入新标签的名称', `在「${meta.parentName}」下新增子标签`, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '标签名称',
      inputValidator: (v) => {
        const val = String(v ?? '').trim()
        if (!val) return '名称不能为空'
        if (val.length > 50) return '名称不能超过 50 个字符'
        return true
      }
    })
    name = String(res.value).trim()
  } catch (e) {
    // 取消/关闭弹窗不算错误
    return
  }

  try {
    await createTag({
      name,
      // 沿用父标签的颜色, 新节点打开后就能看出归属
      color: node.color || '#409EFF',
      parentId: meta.tagId,
      groupId: meta.groupId
    })
    ElMessage.success('已新增子标签')
    tagTreeRef.value?.loadGroups?.()
    tagGraphRef.value?.reload()
  } catch (e) {
    ElMessage.error('新增失败：' + (e?.message || '未知错误'))
  }
}

/** 标签关系图: 点节点上的 "-" 删除叶子标签 */
async function handleRemoveTagNode(node) {
  const meta = node?.remove
  if (!meta || meta.tagId == null) return

  try {
    await ElMessageBox.confirm(
      `确定删除标签「${meta.tagName}」吗？已关联该标签的文件会失去这个标签。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }

  try {
    await deleteTag(meta.tagId)
    ElMessage.success('已删除标签')
    tagTreeRef.value?.loadGroups?.()
    tagGraphRef.value?.reload()
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || '未知错误'))
  }
}

/** 文件列表: 当前标签的关系图(只看该标签及其下级) */
function openCurrentTagGraph(tag) {
  if (!tag) return
  tagGraphRef.value?.open({ tagId: tag.id })
}

/** 文件列表: 以该文件为根, 看它所在的目录层级和标签层级 */
function openFileGraph(file) {
  const server = serverStore.currentServer
  if (!server) {
    ElMessage.warning('请先选择服务器')
    return
  }
  if (!file || !file.path) return
  fileGraphRef.value?.open({
    filePath: file.path,
    fileName: file.name,
    serverId: server.id,
    serverName: server.name
  })
}

/** 文件列表: 当前所在目录的目录关系图 */
function openCurrentDirGraph() {
  const server = serverStore.currentServer
  if (!server) {
    ElMessage.warning('请先选择服务器')
    return
  }
  dirGraphRef.value?.open({
    serverId: server.id,
    serverName: server.name,
    basePath: fileStore.currentPath || '/'
  })
}

const sidebarWidth = ref(250)
const isResizing = ref(false)
const isSidebarCollapsed = ref(false)
const minSidebarWidth = 180
const maxSidebarWidth = 500

watch(sidebarTab, (newVal) => {
  if (newVal === 'directory' && fileListRef.value) {
    fileListRef.value.exitTagFilterMode()
  }
  if (newVal === 'history') {
    // 切到历史页时刷新一次, 兜住其它标签页/设备产生的变更
    historyStore.load(true)
  }
  if (newVal === 'favorites') {
    favoriteStore.load(true)
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

/**
 * 把当前服务器切到历史条目所属的那台; 服务器已被删除时给出提示。
 */
function ensureServerOf(item) {
  const current = serverStore.currentServer
  if (current && current.id === item.serverId) return true

  const server = serverStore.servers.find((s) => s.id === item.serverId)
  if (!server) {
    ElMessage.warning('该记录所属的服务器已不存在')
    return false
  }
  serverStore.setCurrentServer(server)
  fileStore.setPath('/')
  fileStore.loadTree(server.id, '/')
  return true
}

function handleHistoryNavigate(item) {
  if (!ensureServerOf(item)) return
  fileListRef.value?.navigateTo(item.path)
}

function handleHistoryPreview(item) {
  if (!ensureServerOf(item)) return
  handlePreview({ path: item.path, name: item.name, isDirectory: false })
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