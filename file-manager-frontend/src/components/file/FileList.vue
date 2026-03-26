<template>
  <div class="file-list">
    <div class="list-header">
      <div class="current-path">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>{{ currentPath }}</el-breadcrumb-item>
        </el-breadcrumb>
      </div>
      
      <div class="actions">
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon>
          上传文件
        </el-button>
        
        <el-button @click="showCreateFolderDialog = true">
          <el-icon><FolderAdd /></el-icon>
          新建文件夹
        </el-button>
      </div>
    </div>
    
    <el-table
      :data="fileStore.fileList"
      v-loading="fileStore.loading"
      @row-dblclick="handleRowDblClick"
      style="width: 100%"
    >
      <el-table-column width="60">
        <template #default="{ row }">
          <el-icon :color="getIconColor(row)" :size="24">
            <component :is="getIcon(row)" />
          </el-icon>
        </template>
      </el-table-column>
      
      <el-table-column prop="name" label="名称" sortable />
      
      <el-table-column prop="size" label="大小" width="120">
        <template #default="{ row }">
          {{ formatSize(row.size) }}
        </template>
      </el-table-column>
      
      <el-table-column prop="lastModified" label="修改时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.lastModified) }}
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link @click="handlePreview(row)" v-if="!row.isDirectory">
            预览
          </el-button>
          <el-button link @click="handleDownload(row)" v-if="!row.isDirectory">
            下载
          </el-button>
          <el-button link @click="handleRename(row)">
            重命名
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <FileUpload
      v-model="showUploadDialog"
      :server-id="serverStore.currentServer?.id"
      :path="fileStore.currentPath"
      @success="handleUploadSuccess"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useServerStore } from '@/store/server'
import { useFileStore } from '@/store/file'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Folder, Document, Picture, VideoPlay, Headset } from '@element-plus/icons-vue'
import { getFileTypeInfo, formatFileSize } from '@/utils/file-type'
import { formatDate } from '@/utils/date-format'
import { downloadFile } from '@/api/file'
import FileUpload from './FileUpload.vue'

const serverStore = useServerStore()
const fileStore = useFileStore()

const showUploadDialog = ref(false)
const showCreateFolderDialog = ref(false)

const currentPath = computed(() => fileStore.currentPath)

function getIcon(row) {
  if (row.isDirectory) return Folder
  const typeInfo = getFileTypeInfo(row.name)
  
  switch (typeInfo.type) {
    case 'image': return Picture
    case 'video': return VideoPlay
    case 'audio': return Headset
    default: return Document
  }
}

function getIconColor(row) {
  if (row.isDirectory) return '#FFA726'
  const typeInfo = getFileTypeInfo(row.name)
  return typeInfo.color
}

function formatSize(size) {
  return formatFileSize(size)
}

function handleRowDblClick(row) {
  if (row.isDirectory) {
    fileStore.setPath(row.path)
    if (serverStore.currentServer) {
      fileStore.loadFileList(serverStore.currentServer.id, row.path)
    }
  }
}

function handlePreview(row) {
  ElMessage.info('预览功能开发中')
}

function handleDownload(row) {
  if (serverStore.currentServer) {
    const url = downloadFile(serverStore.currentServer.id, row.path)
    window.open(url, '_blank')
  }
}

async function handleRename(row) {
  const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
    inputValue: row.name
  })
  
  if (value && value !== row.name) {
    const oldPath = row.path
    const newPath = row.path.replace(row.name, value)
    
    await fileStore.rename(serverStore.currentServer.id, oldPath, newPath)
    ElMessage.success('重命名成功')
    await fileStore.loadFileList(serverStore.currentServer.id, fileStore.currentPath)
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定要删除吗?', '提示', { type: 'warning' })
  
  await fileStore.remove(serverStore.currentServer.id, row.path)
  ElMessage.success('删除成功')
  await fileStore.loadFileList(serverStore.currentServer.id, fileStore.currentPath)
}

function handleUploadSuccess() {
  if (serverStore.currentServer) {
    fileStore.loadFileList(serverStore.currentServer.id, fileStore.currentPath)
  }
}
</script>

<style scoped>
.file-list {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.current-path {
  font-size: 14px;
}

.actions {
  display: flex;
  gap: 12px;
}
</style>