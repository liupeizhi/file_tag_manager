<template>
  <div class="file-tree">
    <div class="tree-header">
      <span>文件目录</span>
      <el-button link @click="handleRefresh">
        <el-icon><Refresh /></el-icon>
      </el-button>
    </div>
    
    <el-tree
      :data="treeData"
      :props="defaultProps"
      node-key="path"
      @node-click="handleNodeClick"
      :expand-on-click-node="false"
      default-expand-all
    >
      <template #default="{ node, data }">
        <div class="tree-node">
          <el-icon :color="getIconColor(data)">
            <component :is="getIcon(data)" />
          </el-icon>
          <span class="node-label">{{ data.name }}</span>
        </div>
      </template>
    </el-tree>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useServerStore } from '@/store/server'
import { useFileStore } from '@/store/file'
import { Folder, Document, Picture, VideoPlay, Headset } from '@element-plus/icons-vue'
import { getFileTypeInfo } from '@/utils/file-type'

const serverStore = useServerStore()
const fileStore = useFileStore()

const defaultProps = {
  children: 'children',
  label: 'name'
}

const treeData = computed(() => {
  if (!serverStore.currentServer) return []
  return buildTree(fileStore.treeData)
})

function buildTree(files) {
  const root = { name: '根目录', path: '/', isDirectory: true, children: [] }
  
  files.forEach(file => {
    if (file.isDirectory) {
      root.children.push({
        ...file,
        children: []
      })
    }
  })
  
  return [root]
}

function getIcon(data) {
  if (data.isDirectory) return Folder
  const typeInfo = getFileTypeInfo(data.name)
  
  switch (typeInfo.type) {
    case 'image': return Picture
    case 'video': return VideoPlay
    case 'audio': return Headset
    default: return Document
  }
}

function getIconColor(data) {
  if (data.isDirectory) return '#FFA726'
  const typeInfo = getFileTypeInfo(data.name)
  return typeInfo.color
}

async function handleNodeClick(data) {
  if (serverStore.currentServer) {
    fileStore.setPath(data.path)
    await fileStore.loadFileList(serverStore.currentServer.id, data.path)
  }
}

async function handleRefresh() {
  if (serverStore.currentServer) {
    await fileStore.loadTree(serverStore.currentServer.id, fileStore.currentPath)
  }
}
</script>

<style scoped>
.file-tree {
  height: 100%;
  padding: 12px;
}

.tree-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-label {
  font-size: 14px;
}
</style>