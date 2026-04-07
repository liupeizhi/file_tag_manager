<template>
  <div class="file-list">
    <div class="list-header">
      <div class="current-path">
        <template v-if="isTagFilterMode">
          <el-tag :color="currentTagFilter?.color" class="tag-filter-tag" :style="{ color: getContrastColor(currentTagFilter?.color) }">
            {{ currentTagFilter?.name }}
          </el-tag>
          <span class="tag-filter-label">标签下的文件</span>
        </template>
        <template v-else>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>
              <a @click="navigateTo('/')">根目录</a>
            </el-breadcrumb-item>
            <el-breadcrumb-item v-for="(item, index) in pathSegments" :key="index">
              <el-tooltip :content="item.name" placement="top" :disabled="!isNameOverflow(item.name)">
                <a @click="navigateTo(item.path)" class="breadcrumb-link">{{ item.name }}</a>
              </el-tooltip>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </template>
      </div>
      
      <div class="actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文件名"
          clearable
          style="width: 200px"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        
        <el-button-group>
          <el-button :type="viewMode === 'list' ? 'primary' : 'default'" @click="viewMode = 'list'">
            <el-icon><List /></el-icon>
          </el-button>
          <el-button :type="viewMode === 'grid' ? 'primary' : 'default'" @click="viewMode = 'grid'">
            <el-icon><Grid /></el-icon>
          </el-button>
        </el-button-group>
        
        <el-dropdown trigger="click" v-if="viewMode === 'list'">
          <el-button>
            <el-icon><Setting /></el-icon>
            列设置
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-checkbox-group v-model="visibleColumns">
                <el-dropdown-item v-for="col in optionalColumns" :key="col.prop">
                  <el-checkbox :value="col.prop">{{ col.label }}</el-checkbox>
                </el-dropdown-item>
              </el-checkbox-group>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        
        <el-button @click="openCreateFolder">
          <el-icon><FolderAdd /></el-icon>
          新建文件夹
        </el-button>
        
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon>
          上传文件
        </el-button>
        
        <el-button @click="handleSync" :loading="syncing">
          <el-icon><Refresh /></el-icon>
          同步文件
        </el-button>
      </div>
    </div>
    
    <!-- 批量操作栏 -->
    <div class="batch-actions" v-if="selectedFiles.length > 0 && viewMode === 'list'">
      <div class="batch-info">
        <el-icon><InfoFilled /></el-icon>
        <span>已选择 <strong>{{ selectedFiles.length }}</strong> 个项目</span>
      </div>
      <div class="batch-buttons">
        <el-button @click="clearSelection">取消选择</el-button>
        <el-button 
          type="primary" 
          @click="openBatchTagDialog"
          :disabled="!hasSelectedFiles"
        >
          <el-icon><PriceTag /></el-icon>
          批量设置标签
        </el-button>
        <el-button type="danger" @click="handleBatchDelete">
          <el-icon><Delete /></el-icon>
          批量删除
        </el-button>
      </div>
    </div>
    
    <!-- 列表视图 -->
    <div class="table-container" v-if="viewMode === 'list'">
      <el-table
        ref="tableRef"
        :data="filteredFileList"
        v-loading="fileStore.loading"
        @row-click="handleRowClick"
        @selection-change="handleSelectionChange"
        :row-class-name="getRowClassName"
        style="width: 100%"
        :height="isTagFilterMode && totalTagFilteredFiles > 0 ? 'calc(100% - 60px)' : '100%'"
      >
      <el-table-column type="selection" width="55" />
      
      <el-table-column width="60">
        <template #default="{ row }">
          <el-icon :color="getIconColor(row)" :size="24">
            <component :is="getIcon(row)" />
          </el-icon>
        </template>
      </el-table-column>
      
      <el-table-column prop="name" label="名称" sortable min-width="200" />
      
      <el-table-column v-if="visibleColumns.includes('fileType')" label="类型" width="100">
        <template #default="{ row }">
          {{ getFileTypeLabel(row) }}
        </template>
      </el-table-column>
      
      <el-table-column v-if="visibleColumns.includes('size')" prop="size" label="大小" width="120" sortable :sort-method="sortBySize">
        <template #default="{ row }">
          {{ row.isDirectory ? '-' : formatSize(row.size) }}
        </template>
      </el-table-column>
      
      <el-table-column v-if="visibleColumns.includes('createdTime')" prop="createdTime" label="创建时间" width="180" sortable :sort-method="sortByCreatedTime">
        <template #default="{ row }">
          {{ formatDate(row.createdTime || row.lastModified) }}
        </template>
      </el-table-column>
      
      <el-table-column v-if="visibleColumns.includes('lastModified')" prop="lastModified" label="修改时间" width="180" sortable :sort-method="sortByDate">
        <template #default="{ row }">
          {{ formatDate(row.lastModified) }}
        </template>
      </el-table-column>
      
      <el-table-column label="标签" width="150">
        <template #default="{ row }">
          <div class="file-tags-cell" v-if="!row.isDirectory">
            <el-tag
              v-for="tag in getFileTags(row.path)"
              :key="tag.id"
              :color="tag.color"
              size="small"
              class="file-tag"
              :style="{ color: getContrastColor(tag.color) }"
            >
              {{ tag.name }}
            </el-tag>
            <el-button
              link
              size="small"
              @click="openTagDialog(row)"
            >
              <el-icon><Plus /></el-icon>
            </el-button>
          </div>
          <span v-else style="color: var(--el-text-color-placeholder);">-</span>
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="200" fixed="right">
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
      
      <!-- 标签过滤模式分页 -->
      <div v-if="isTagFilterMode && totalTagFilteredFiles > 0" class="tag-pagination">
        <el-pagination
          v-model:current-page="tagFilterPage"
          v-model:page-size="tagFilterPageSize"
          :page-sizes="tagFilterPageSizes"
          :total="totalTagFilteredFiles"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handleTagPageChange"
          @size-change="handleTagPageSizeChange"
        />
      </div>
    </div>
    
    <!-- 网格视图 -->
    <div 
      v-else 
      class="grid-view"
      v-loading="fileStore.loading"
    >
      <div 
        v-for="file in filteredFileList" 
        :key="file.path"
        class="grid-item"
        :class="{ 'is-folder': file.isDirectory }"
        @click="handleGridItemClick(file)"
        @contextmenu.prevent="showContextMenu($event, file)"
      >
        <div class="grid-icon">
          <el-icon :color="getIconColor(file)" :size="48">
            <component :is="getIcon(file)" />
          </el-icon>
        </div>
        <div class="grid-name" :title="file.name">{{ file.name }}</div>
        <div class="grid-info">
          <span v-if="file.isDirectory">文件夹</span>
          <span v-else>{{ formatSize(file.size) }}</span>
        </div>
      </div>
      
      <el-empty v-if="!fileStore.loading && filteredFileList.length === 0" description="暂无文件" />
    </div>
    
    <!-- 右键菜单 -->
    <el-dropdown
      ref="contextMenuRef"
      trigger="contextmenu"
      :teleported="false"
      @command="handleContextMenuCommand"
    >
      <span style="display: none;"></span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="open" v-if="contextMenuFile?.isDirectory">
            <el-icon><FolderOpened /></el-icon>
            打开
          </el-dropdown-item>
          <el-dropdown-item command="preview" v-if="!contextMenuFile?.isDirectory">
            <el-icon><View /></el-icon>
            预览
          </el-dropdown-item>
          <el-dropdown-item command="download" v-if="!contextMenuFile?.isDirectory">
            <el-icon><Download /></el-icon>
            下载
          </el-dropdown-item>
          <el-dropdown-item command="rename">
            <el-icon><Edit /></el-icon>
            重命名
          </el-dropdown-item>
          <el-dropdown-item command="delete" divided>
            <el-icon><Delete /></el-icon>
            删除
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    
    <el-dialog v-model="showCreateFolderDialog" title="新建文件夹" width="400px">
      <el-form @submit.prevent="createFolder">
        <el-form-item label="文件夹名称">
          <el-input v-model="newFolderName" placeholder="请输入文件夹名称" autofocus />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateFolderDialog = false">取消</el-button>
        <el-button type="primary" @click="createFolder">确定</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showTagDialog" title="管理标签" width="500px">
      <div class="tag-dialog-content">
        <div class="current-file">文件: {{ tagDialogFile?.name }}</div>
        <el-tabs v-model="tagDialogActiveGroup" type="card" class="tag-dialog-tabs">
          <el-tab-pane 
            v-for="group in tagGroups" 
            :key="group.id" 
            :label="group.name" 
            :name="String(group.id)"
          >
            <el-tree
              :ref="el => tagTreeRefs[group.id] = el"
              :data="group.tags"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              show-checkbox
              check-strictly
              default-expand-all
              :default-checked-keys="getCheckedKeysForGroup(group.id)"
              class="tag-tree"
            >
              <template #default="{ node, data }">
                <span class="tag-tree-node">
                  <span class="tag-color-dot" :style="{ backgroundColor: data.color }"></span>
                  <span>{{ data.name }}</span>
                </span>
              </template>
            </el-tree>
            <el-empty v-if="!group.tags || group.tags.length === 0" description="暂无标签" :image-size="60" />
          </el-tab-pane>
        </el-tabs>
      </div>
      <template #footer>
        <el-button @click="showTagDialog = false">取消</el-button>
        <el-button type="primary" @click="saveFileTags">保存</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showBatchTagDialog" title="批量设置标签" width="500px">
      <div class="tag-dialog-content">
        <div class="current-file">已选择 {{ selectedFiles.length }} 个文件</div>
        <el-tabs v-model="batchTagActiveGroup" type="card" class="tag-dialog-tabs">
          <el-tab-pane 
            v-for="group in tagGroups" 
            :key="group.id" 
            :label="group.name" 
            :name="String(group.id)"
          >
            <el-tree
              :ref="el => batchTagTreeRefs[group.id] = el"
              :data="group.tags"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              show-checkbox
              check-strictly
              default-expand-all
              class="tag-tree"
            >
              <template #default="{ node, data }">
                <span class="tag-tree-node">
                  <span class="tag-color-dot" :style="{ backgroundColor: data.color }"></span>
                  <span>{{ data.name }}</span>
                </span>
              </template>
            </el-tree>
            <el-empty v-if="!group.tags || group.tags.length === 0" description="暂无标签" :image-size="60" />
          </el-tab-pane>
        </el-tabs>
      </div>
      <template #footer>
        <el-button @click="showBatchTagDialog = false">取消</el-button>
        <el-button type="primary" @click="saveBatchTags">保存</el-button>
      </template>
    </el-dialog>
    
    <FileUpload
      v-model="showUploadDialog"
      :server-id="serverStore.currentServer?.id"
      :path="fileStore.currentPath"
      @success="handleUploadSuccess"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useServerStore } from '@/store/server'
import { useFileStore } from '@/store/file'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Folder, Document, Picture, VideoPlay, Headset, Search, Setting, 
  FolderAdd, Upload, List, Grid, FolderOpened, View, Download, 
  Edit, Delete, Plus, Refresh, InfoFilled, PriceTag
} from '@element-plus/icons-vue'
import { getFileTypeInfo, formatFileSize } from '@/utils/file-type'
import { formatDate } from '@/utils/date-format'
import { downloadFile, createFolder as createFolderApi, syncFiles } from '@/api/file'
import { getTagGroups, getFileTags as getFileTagsApi, setFileTags } from '@/api/tag'
import FileUpload from './FileUpload.vue'

const serverStore = useServerStore()
const fileStore = useFileStore()

const emit = defineEmits(['preview'])

const showUploadDialog = ref(false)
const showCreateFolderDialog = ref(false)
const searchKeyword = ref('')
const newFolderName = ref('')
const viewMode = ref('list')
const contextMenuRef = ref(null)
const contextMenuFile = ref(null)
const syncing = ref(false)
const tableRef = ref(null)

const showTagDialog = ref(false)
const tagDialogFile = ref(null)
const selectedTagIds = ref([])
const allTags = ref([])
const tagGroups = ref([])
const tagTreeRefs = {}
const tagDialogActiveGroup = ref('')
const fileTagsMap = ref({})

const showBatchTagDialog = ref(false)
const batchTagActiveGroup = ref('')
const batchTagTreeRefs = {}

const optionalColumns = [
  { prop: 'fileType', label: '类型' },
  { prop: 'size', label: '大小' },
  { prop: 'createdTime', label: '创建时间' },
  { prop: 'lastModified', label: '修改时间' }
]

const visibleColumns = ref(['fileType', 'size', 'lastModified'])

const isTagFilterMode = ref(false)
const currentTagFilter = ref(null)
const tagFilteredFiles = ref([])

const tagFilterPage = ref(1)
const tagFilterPageSize = ref(20)
const tagFilterPageSizes = [10, 20, 50, 100]

const selectedFiles = ref([])

const hasSelectedFiles = computed(() => {
  return selectedFiles.value.some(f => !f.isDirectory)
})

const currentPath = computed(() => fileStore.currentPath)

const fileList = computed(() => fileStore.fileList || [])

const pathSegments = computed(() => {
  const path = fileStore.currentPath
  if (path === '/' || !path) return []
  
  const parts = path.split('/').filter(Boolean)
  let currentPath = ''
  
  return parts.map(part => {
    currentPath += '/' + part
    return {
      name: part,
      path: currentPath
    }
  })
})

const filteredFileList = computed(() => {
  if (isTagFilterMode.value) {
    const list = tagFilteredFiles.value || []
    let result = list
    
    if (searchKeyword.value) {
      const keyword = searchKeyword.value.toLowerCase()
      result = list.filter(file => file.name.toLowerCase().includes(keyword))
    }
    
    const start = (tagFilterPage.value - 1) * tagFilterPageSize.value
    const end = start + tagFilterPageSize.value
    return result.slice(start, end)
  }
  
  const list = fileList.value || []
  if (!searchKeyword.value) return list
  
  const keyword = searchKeyword.value.toLowerCase()
  return list.filter(file => file.name.toLowerCase().includes(keyword))
})

const totalTagFilteredFiles = computed(() => {
  if (!isTagFilterMode.value) return 0
  
  const list = tagFilteredFiles.value || []
  if (!searchKeyword.value) return list.length
  
  const keyword = searchKeyword.value.toLowerCase()
  return list.filter(file => file.name.toLowerCase().includes(keyword)).length
})

watch(fileList, async () => {
  fileTagsMap.value = {}
  if (fileList.value && fileList.value.length > 0 && serverStore.currentServer) {
    await loadAllFileTags()
  }
}, { immediate: true })

watch(searchKeyword, () => {
  if (isTagFilterMode.value) {
    tagFilterPage.value = 1
  }
})

function openCreateFolder() {
  newFolderName.value = ''
  showCreateFolderDialog.value = true
}

async function createFolder() {
  if (!newFolderName.value.trim()) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  
  try {
    const folderPath = fileStore.currentPath === '/' 
      ? '/' + newFolderName.value 
      : fileStore.currentPath + '/' + newFolderName.value
    
    await createFolderApi(serverStore.currentServer.id, folderPath)
    ElMessage.success('创建成功')
    showCreateFolderDialog.value = false
    await fileStore.loadFileList(serverStore.currentServer.id, fileStore.currentPath)
  } catch (error) {
    ElMessage.error('创建失败: ' + (error.message || '未知错误'))
  }
}

function navigateTo(path) {
  if (isTagFilterMode.value) {
    exitTagFilterMode()
  }
  if (serverStore.currentServer && path !== fileStore.currentPath) {
    fileStore.setPath(path)
    fileStore.loadFileList(serverStore.currentServer.id, path)
  }
}

function isNameOverflow(name) {
  return name && name.length > 15
}

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

function getRowClassName({ row }) {
  return row.isDirectory ? 'folder-row' : 'file-row'
}

function formatSize(size) {
  return formatFileSize(size)
}

function getFileTypeLabel(row) {
  if (row.isDirectory) return '文件夹'
  const typeInfo = getFileTypeInfo(row.name)
  if (typeInfo.label && typeInfo.type !== 'other') {
    return typeInfo.label
  }
  const ext = row.name.split('.').pop()?.toUpperCase()
  return ext && ext !== row.name ? `${ext}文件` : '文件'
}

function sortBySize(a, b) {
  const sizeA = a.size || 0
  const sizeB = b.size || 0
  return sizeA - sizeB
}

function sortByDate(a, b) {
  if (!a.lastModified && !b.lastModified) return 0
  if (!a.lastModified) return 1
  if (!b.lastModified) return -1
  return new Date(b.lastModified).getTime() - new Date(a.lastModified).getTime()
}

function sortByCreatedTime(a, b) {
  const timeA = a.createdTime || a.lastModified
  const timeB = b.createdTime || b.lastModified
  if (!timeA && !timeB) return 0
  if (!timeA) return 1
  if (!timeB) return -1
  return new Date(timeB).getTime() - new Date(timeA).getTime()
}

function handleRowClick(row) {
  if (row.isDirectory) {
    if (isTagFilterMode.value) {
      exitTagFilterMode()
    }
    fileStore.setPath(row.path)
    if (serverStore.currentServer) {
      fileStore.loadFileList(serverStore.currentServer.id, row.path)
    }
  }
}

function handleGridItemClick(file) {
  if (file.isDirectory) {
    if (isTagFilterMode.value) {
      exitTagFilterMode()
    }
    fileStore.setPath(file.path)
    if (serverStore.currentServer) {
      fileStore.loadFileList(serverStore.currentServer.id, file.path)
    }
  } else {
    emit('preview', file)
  }
}

function showContextMenu(event, file) {
  contextMenuFile.value = file
  const dropdown = contextMenuRef.value
  if (dropdown) {
    dropdown.handleOpen()
    const menuEl = dropdown.$el.querySelector('.el-dropdown-menu')
    if (menuEl) {
      menuEl.style.position = 'fixed'
      menuEl.style.left = event.clientX + 'px'
      menuEl.style.top = event.clientY + 'px'
    }
  }
}

function handleContextMenuCommand(command) {
  if (!contextMenuFile.value) return
  
  switch (command) {
    case 'open':
      handleGridItemClick(contextMenuFile.value)
      break
    case 'preview':
      handlePreview(contextMenuFile.value)
      break
    case 'download':
      handleDownload(contextMenuFile.value)
      break
    case 'rename':
      handleRename(contextMenuFile.value)
      break
    case 'delete':
      handleDelete(contextMenuFile.value)
      break
  }
  
  contextMenuFile.value = null
}

function handlePreview(row) {
  emit('preview', row)
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

function handleSelectionChange(selection) {
  selectedFiles.value = selection
}

function clearSelection() {
  if (tableRef.value) {
    tableRef.value.clearSelection()
  }
  selectedFiles.value = []
}

async function handleBatchDelete() {
  if (selectedFiles.value.length === 0) return
  
  const count = selectedFiles.value.length
  const folders = selectedFiles.value.filter(f => f.isDirectory).length
  const files = count - folders
  
  let message = `确定要删除选中的 ${count} 个项目吗？`
  if (folders > 0 && files > 0) {
    message = `确定要删除选中的 ${folders} 个文件夹和 ${files} 个文件吗？`
  } else if (folders > 0) {
    message = `确定要删除选中的 ${folders} 个文件夹吗？`
  } else if (files > 0) {
    message = `确定要删除选中的 ${files} 个文件吗？`
  }
  
  try {
    await ElMessageBox.confirm(message, '批量删除确认', { 
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
    
    const serverId = serverStore.currentServer?.id
    const path = fileStore.currentPath
    
    console.log('handleBatchDelete: serverId=', serverId, 'path=', path)
    
    let successCount = 0
    let failCount = 0
    
    for (const file of selectedFiles.value) {
      try {
        await fileStore.remove(serverId, file.path)
        successCount++
      } catch (error) {
        failCount++
        console.error(`删除失败: ${file.name}`, error)
      }
    }
    
    console.log('handleBatchDelete: deleted', successCount, 'files, failed', failCount)
    
    // 刷新文件列表 - 确保使用正确的 serverId 和 path
    const reloadServerId = serverStore.currentServer?.id
    const reloadPath = fileStore.currentPath
    
    console.log('handleBatchDelete: before clearSelection, will reload with serverId=', reloadServerId, 'path=', reloadPath)
    
    try {
      clearSelection()
      console.log('handleBatchDelete: clearSelection done')
    } catch (error) {
      console.error('handleBatchDelete: clearSelection error', error)
    }
    
    if (reloadServerId && reloadPath) {
      console.log('handleBatchDelete: calling loadFileList')
      try {
        await fileStore.loadFileList(reloadServerId, reloadPath)
        console.log('handleBatchDelete: loadFileList completed, fileList.length=', fileStore.fileList?.length)
      } catch (error) {
        console.error('handleBatchDelete: loadFileList error', error)
      }
    } else {
      console.error('handleBatchDelete: missing serverId or path', { reloadServerId, reloadPath })
    }
    
    if (failCount === 0) {
      ElMessage.success(`成功删除 ${successCount} 个项目`)
    } else {
      ElMessage.warning(`成功删除 ${successCount} 个，失败 ${failCount} 个`)
    }
  } catch {
    // 用户取消
  }
}

function handleUploadSuccess() {
  if (serverStore.currentServer) {
    fileStore.loadFileList(serverStore.currentServer.id, fileStore.currentPath)
  }
}

async function handleSync() {
  if (!serverStore.currentServer) return
  
  syncing.value = true
  try {
    const result = await syncFiles(serverStore.currentServer.id, fileStore.currentPath)
    const data = result.data || {}
    const added = data.added || 0
    const updated = data.updated || 0
    const deleted = data.deleted || 0
    ElMessage.success(`同步完成: 新增 ${added}, 更新 ${updated}, 删除 ${deleted}`)
  } catch (error) {
    ElMessage.error('同步失败: ' + (error.message || '未知错误'))
  } finally {
    syncing.value = false
  }
}

async function loadAllTags() {
  try {
    tagGroups.value = await getTagGroups()
    if (tagGroups.value.length > 0 && !tagDialogActiveGroup.value) {
      tagDialogActiveGroup.value = String(tagGroups.value[0].id)
    }
    allTags.value = flattenTagGroups(tagGroups.value)
  } catch (error) {
    console.error('加载标签失败', error)
  }
}

function flattenTagGroups(groups, result = []) {
  for (const group of groups) {
    if (group.tags) {
      flattenTags(group.tags, result)
    }
  }
  return result
}

function flattenTags(tags, result = []) {
  for (const tag of tags) {
    result.push(tag)
    if (tag.children && tag.children.length > 0) {
      flattenTags(tag.children, result)
    }
  }
  return result
}

function getCheckedKeysForGroup(groupId) {
  const group = tagGroups.value.find(g => g.id === groupId)
  if (!group || !group.tags) return []
  
  const groupTagIds = new Set()
  const collectIds = (tags) => {
    for (const tag of tags) {
      groupTagIds.add(tag.id)
      if (tag.children) collectIds(tag.children)
    }
  }
  collectIds(group.tags)
  
  return selectedTagIds.value.filter(id => groupTagIds.has(id))
}

function getContrastColor(hexColor) {
  if (!hexColor) return '#ffffff'
  
  let hex = hexColor.replace('#', '')
  if (hex.length === 3) {
    hex = hex.split('').map(c => c + c).join('')
  }
  
  const r = parseInt(hex.substr(0, 2), 16)
  const g = parseInt(hex.substr(2, 2), 16)
  const b = parseInt(hex.substr(4, 2), 16)
  
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  
  return luminance > 0.5 ? '#000000' : '#ffffff'
}

function getFileTags(filePath) {
  return fileTagsMap.value[filePath] || []
}

async function loadFileTags(filePath) {
  if (!serverStore.currentServer) return
  try {
    const tags = await getFileTagsApi(filePath, serverStore.currentServer.id)
    fileTagsMap.value[filePath] = tags
  } catch (error) {
    fileTagsMap.value[filePath] = []
  }
}

async function loadAllFileTags() {
  if (!serverStore.currentServer || !fileList.value) return
  
  // 只加载文件的标签，文件夹不需要标签
  const files = fileList.value.filter(f => !f.isDirectory)
  const batchSize = 10
  
  for (let i = 0; i < files.length; i += batchSize) {
    const batch = files.slice(i, i + batchSize)
    await Promise.all(batch.map(file => loadFileTags(file.path)))
  }
}

async function openTagDialog(file) {
  tagDialogFile.value = file
  await loadAllTags()
  await loadFileTags(file.path)
  selectedTagIds.value = (fileTagsMap.value[file.path] || []).map(t => t.id)
  showTagDialog.value = true
}

async function saveFileTags() {
  if (!tagDialogFile.value || !serverStore.currentServer) return
  
  try {
    const checkedKeys = []
    for (const group of tagGroups.value) {
      const treeRef = tagTreeRefs[group.id]
      if (treeRef) {
        const keys = treeRef.getCheckedKeys(false) || []
        checkedKeys.push(...keys)
      }
    }
    await setFileTags(tagDialogFile.value.path, serverStore.currentServer.id, checkedKeys)
    await loadFileTags(tagDialogFile.value.path)
    showTagDialog.value = false
    ElMessage.success('标签已保存')
  } catch (error) {
    ElMessage.error('保存标签失败')
  }
}

async function openBatchTagDialog() {
  if (selectedFiles.value.length === 0) return
  await loadAllTags()
  batchTagActiveGroup.value = tagGroups.value.length > 0 ? String(tagGroups.value[0].id) : ''
  showBatchTagDialog.value = true
}

async function saveBatchTags() {
  if (selectedFiles.value.length === 0 || !serverStore.currentServer) return
  
  try {
    const checkedKeys = []
    for (const group of tagGroups.value) {
      const treeRef = batchTagTreeRefs[group.id]
      if (treeRef) {
        const keys = treeRef.getCheckedKeys(false) || []
        checkedKeys.push(...keys)
      }
    }
    
    // 只对文件设置标签，跳过文件夹
    const files = selectedFiles.value.filter(f => !f.isDirectory)
    let successCount = 0
    let failCount = 0
    
    for (const file of files) {
      try {
        await setFileTags(file.path, serverStore.currentServer.id, checkedKeys)
        successCount++
      } catch (error) {
        failCount++
      }
    }
    
    showBatchTagDialog.value = false
    clearSelection()
    
    if (failCount === 0) {
      ElMessage.success(`已为 ${successCount} 个文件设置标签`)
    } else {
      ElMessage.warning(`成功 ${successCount} 个，失败 ${failCount} 个`)
    }
  } catch (error) {
    ElMessage.error('批量设置标签失败')
  }
}

function setTagFilterMode(enabled, tag = null) {
  isTagFilterMode.value = enabled
  currentTagFilter.value = tag
  if (!enabled) {
    tagFilteredFiles.value = []
  }
  tagFilterPage.value = 1
}

function setTagFilteredFiles(files) {
  tagFilteredFiles.value = files
}

function exitTagFilterMode() {
  isTagFilterMode.value = false
  currentTagFilter.value = null
  tagFilteredFiles.value = []
  tagFilterPage.value = 1
}

function handleTagPageChange(page) {
  tagFilterPage.value = page
}

function handleTagPageSizeChange(size) {
  tagFilterPageSize.value = size
  tagFilterPage.value = 1
}

defineExpose({
  setTagFilterMode,
  setTagFilteredFiles,
  exitTagFilterMode
})
</script>

<style scoped>
.file-list {
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.table-container {
  flex: 1;
  overflow: hidden;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.tag-pagination {
  flex-shrink: 0;
  padding: 16px 0 0 0;
  display: flex;
  justify-content: center;
  border-top: 1px solid var(--theme-border);
  margin-top: 16px;
}

.batch-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: color-mix(in srgb, var(--theme-primary) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--theme-primary) 30%, transparent);
  border-radius: 12px;
  margin-bottom: 12px;
  flex-shrink: 0;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.batch-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--theme-primary);
  font-size: 14px;
}

.batch-info .el-icon {
  font-size: 16px;
}

.batch-info strong {
  font-weight: 600;
}

.batch-buttons {
  display: flex;
  gap: 8px;
}

.grid-view {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 16px;
  align-content: start;
}

.current-path {
  font-size: 14px;
  display: flex;
  align-items: center;
  overflow: hidden;
}

.current-path :deep(.el-breadcrumb) {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  overflow: hidden;
}

.current-path :deep(.el-breadcrumb__item) {
  flex-shrink: 0;
}

.current-path :deep(.el-breadcrumb__item:last-child) {
  flex-shrink: 1;
  min-width: 0;
}

.current-path a {
  cursor: pointer;
  color: #409EFF;
  text-decoration: none;
}

.current-path a:hover {
  text-decoration: underline;
}

.breadcrumb-link {
  display: inline-block;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.tag-filter-tag {
  margin-right: 8px;
}

.tag-filter-label {
  color: var(--theme-text-secondary);
  font-size: 14px;
}

.actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

:deep(.folder-row) {
  cursor: pointer;
}

:deep(.folder-row:hover td) {
  background-color: var(--theme-background) !important;
}

:deep(.file-row) {
  cursor: default;
}

:deep(.el-dropdown-menu) {
  padding: 8px;
}

:deep(.el-checkbox-group) {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

:deep(.el-dropdown-item) {
  padding: 4px 8px;
}

/* 网格视图样式 */
.grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.grid-item:hover {
  background: var(--theme-background);
  border-color: var(--theme-border);
}

.grid-item.is-folder:hover {
  border-color: var(--theme-primary);
}

.grid-icon {
  margin-bottom: 8px;
}

.grid-name {
  font-size: 13px;
  color: var(--theme-text);
  text-align: center;
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
  max-width: 100%;
}

.grid-info {
  font-size: 12px;
  color: var(--theme-text-secondary);
  margin-top: 4px;
}

/* 文件标签样式 */
.file-tags-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.file-tag {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 标签对话框样式 */
.tag-dialog-content {
  max-height: 500px;
  overflow-y: auto;
}

.current-file {
  padding: 8px 0;
  margin-bottom: 12px;
  color: var(--theme-text-secondary);
  font-size: 13px;
  border-bottom: 1px solid var(--theme-border);
}

.tag-dialog-tabs {
  min-height: 300px;
}

.tag-dialog-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.tag-dialog-tabs :deep(.el-tabs__content) {
  overflow-y: auto;
  max-height: 350px;
}

.tag-tree {
  margin-top: 8px;
}

.tag-tree-node {
  display: flex;
  align-items: center;
}

.tag-color-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}
</style>