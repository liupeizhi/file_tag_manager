<template>
  <div class="tag-tree">
    <div class="tag-header">
      <span>标签</span>
      <el-dropdown trigger="click">
        <el-button type="primary" link size="small">
          <el-icon><Plus /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="showCreateGroupDialog">新建分组</el-dropdown-item>
            <el-dropdown-item @click="showCreateDialog">新建标签</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    
    <div class="tag-content" v-loading="loading">
      <el-tabs v-model="activeGroupTab" type="card" class="group-tabs" @tab-remove="handleRemoveGroup">
        <el-tab-pane 
          v-for="group in tagGroups" 
          :key="group.id" 
          :label="group.name" 
          :name="String(group.id)"
          :closable="tagGroups.length > 1"
        >
          <div class="tag-tree-container">
            <el-tree
              :data="group.tags"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              :expand-on-click-node="false"
              :highlight-current="true"
              @node-click="handleNodeClick"
              default-expand-all
            >
              <template #default="{ node, data }">
                <div class="tag-node">
                  <span class="tag-color" :style="{ backgroundColor: data.color }"></span>
                  <span class="tag-name">{{ data.name }}</span>
                  <span class="tag-actions">
                    <el-button link size="small" @click.stop="showEditDialog(data)">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button link size="small" type="danger" @click.stop="handleDelete(data)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </span>
                </div>
              </template>
            </el-tree>
            
            <el-empty v-if="!loading && (!group.tags || group.tags.length === 0)" description="暂无标签" :image-size="60" />
          </div>
        </el-tab-pane>
      </el-tabs>
      
      <el-empty v-if="!loading && tagGroups.length === 0" description="暂无标签分组" :image-size="60">
        <el-button type="primary" @click="showCreateGroupDialog">创建分组</el-button>
      </el-empty>
    </div>
    
    <!-- 标签分组对话框 -->
    <el-dialog v-model="groupDialogVisible" :title="isEditGroup ? '编辑分组' : '新建分组'" width="400px">
      <el-form :model="groupForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="groupForm.name" placeholder="请输入分组名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitGroupForm">确定</el-button>
      </template>
    </el-dialog>
    
    <!-- 标签对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑标签' : '新建标签'" width="400px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="请输入标签名称" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="form.color" />
        </el-form-item>
        <el-form-item label="所属分组">
          <el-select v-model="form.groupId" placeholder="选择分组" style="width: 100%">
            <el-option 
              v-for="group in tagGroups" 
              :key="group.id" 
              :label="group.name" 
              :value="group.id" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="父标签">
          <el-cascader
            v-model="form.parentPath"
            :options="getTagOptions(form.groupId)"
            :props="{ value: 'id', label: 'name', children: 'children', checkStrictly: true, emitPath: false }"
            clearable
            placeholder="选择父标签（可选）"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getTagGroups, createTag, updateTag, deleteTag, createTagGroup, updateTagGroup, deleteTagGroup } from '@/api/tag'

const emit = defineEmits(['tag-click'])

const loading = ref(false)
const tagGroups = ref([])
const activeGroupTab = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const groupDialogVisible = ref(false)
const isEditGroup = ref(false)

const form = ref({
  id: null,
  name: '',
  color: '#409EFF',
  parentId: null,
  groupId: null,
  parentPath: []
})

const groupForm = ref({
  id: null,
  name: ''
})

onMounted(() => {
  loadGroups()
})

async function loadGroups() {
  loading.value = true
  try {
    tagGroups.value = await getTagGroups()
    if (tagGroups.value.length > 0 && !activeGroupTab.value) {
      activeGroupTab.value = String(tagGroups.value[0].id)
    }
  } catch (error) {
    ElMessage.error('加载标签失败')
  } finally {
    loading.value = false
  }
}

function getTagOptions(groupId) {
  const group = tagGroups.value.find(g => g.id === groupId)
  if (!group || !group.tags) return []
  return filterTagTree(group.tags, form.value.id)
}

function filterTagTree(tags, excludeId) {
  return tags.filter(tag => tag.id !== excludeId).map(tag => ({
    id: tag.id,
    name: tag.name,
    children: tag.children && tag.children.length > 0 ? filterTagTree(tag.children, excludeId) : []
  }))
}

function handleNodeClick(data) {
  emit('tag-click', data)
}

function showCreateDialog() {
  isEdit.value = false
  const currentGroupId = activeGroupTab.value ? parseInt(activeGroupTab.value) : (tagGroups.value[0]?.id || null)
  form.value = {
    id: null,
    name: '',
    color: '#409EFF',
    parentId: null,
    groupId: currentGroupId,
    parentPath: []
  }
  dialogVisible.value = true
}

function showEditDialog(data) {
  isEdit.value = true
  form.value = {
    id: data.id,
    name: data.name,
    color: data.color,
    parentId: data.parentId,
    groupId: data.groupId,
    parentPath: data.parentId || []
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入标签名称')
    return
  }
  
  try {
    let parentId = null
    if (form.value.parentPath) {
      if (Array.isArray(form.value.parentPath)) {
        parentId = form.value.parentPath.length > 0 ? form.value.parentPath[form.value.parentPath.length - 1] : null
      } else {
        parentId = form.value.parentPath
      }
    }
    
    const data = {
      name: form.value.name,
      color: form.value.color,
      parentId: parentId,
      groupId: form.value.groupId
    }
    
    if (isEdit.value) {
      await updateTag(form.value.id, data)
      ElMessage.success('更新成功')
    } else {
      await createTag(data)
      ElMessage.success('创建成功')
    }
    
    dialogVisible.value = false
    loadGroups()
  } catch (error) {
    ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
  }
}

async function handleDelete(data) {
  try {
    await ElMessageBox.confirm(
      `确定要删除标签 "${data.name}" 吗？文件关联的标签也会被移除。`,
      '确认删除',
      { type: 'warning' }
    )
    
    await deleteTag(data.id)
    ElMessage.success('删除成功')
    loadGroups()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

function showCreateGroupDialog() {
  isEditGroup.value = false
  groupForm.value = { id: null, name: '' }
  groupDialogVisible.value = true
}

async function submitGroupForm() {
  if (!groupForm.value.name.trim()) {
    ElMessage.warning('请输入分组名称')
    return
  }
  
  try {
    if (isEditGroup.value) {
      await updateTagGroup(groupForm.value.id, { name: groupForm.value.name })
      ElMessage.success('更新成功')
    } else {
      await createTagGroup({ name: groupForm.value.name })
      ElMessage.success('创建成功')
    }
    
    groupDialogVisible.value = false
    loadGroups()
  } catch (error) {
    ElMessage.error(isEditGroup.value ? '更新失败' : '创建失败')
  }
}

async function handleRemoveGroup(groupName) {
  const group = tagGroups.value.find(g => String(g.id) === groupName)
  if (!group) return
  
  try {
    await ElMessageBox.confirm(
      `确定要删除分组 "${group.name}" 吗？该分组下的所有标签也会被删除。`,
      '确认删除',
      { type: 'warning' }
    )
    
    await deleteTagGroup(group.id)
    ElMessage.success('删除成功')
    loadGroups()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

defineExpose({
  loadGroups
})
</script>

<style scoped>
.tag-tree {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.tag-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  font-weight: 500;
  color: #303133;
}

.tag-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.group-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.group-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 8px;
}

.group-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
}

.group-tabs :deep(.el-tab-pane) {
  height: 100%;
  overflow-y: auto;
}

.tag-tree-container {
  padding: 8px;
}

.tag-node {
  display: flex;
  align-items: center;
  flex: 1;
  font-size: 13px;
}

.tag-color {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

.tag-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag-actions {
  display: none;
  margin-left: 8px;
}

.tag-node:hover .tag-actions {
  display: inline-flex;
}

:deep(.el-tree-node__content) {
  height: 32px;
}

:deep(.el-tree-node__content:hover) {
  background-color: #f5f7fa;
}
</style>