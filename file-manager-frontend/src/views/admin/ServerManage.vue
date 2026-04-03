<template>
  <div class="server-manage">
    <div class="page-header">
      <h2>服务器管理</h2>
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>
        添加服务器
      </el-button>
    </div>
    
    <el-table :data="servers" v-loading="loading" stripe>
      <el-table-column prop="name" label="名称" width="150" />
      <el-table-column label="协议" width="100">
        <template #default="{ row }">
          <el-tag :type="getProtocolTag(row.protocol)">{{ row.protocol?.toUpperCase() || 'WEBDAV' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="url" label="地址" min-width="250">
        <template #default="{ row }">
          <el-tooltip :content="row.url" placement="top">
            <span class="url-cell">{{ row.url }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column label="密码" width="100">
        <template #default>
          <span>••••••</span>
        </template>
      </el-table-column>
      <el-table-column prop="rootPath" label="根路径" width="120">
        <template #default="{ row }">
          {{ row.rootPath || '/' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-switch 
            :model-value="row.enabled !== false" 
            @change="(val) => toggleServer(row, val)" 
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" text @click="testConnection(row)">
            测试
          </el-button>
          <el-button type="primary" size="small" text @click="openDialog(row)">
            编辑
          </el-button>
          <el-button type="danger" size="small" text @click="deleteServer(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑服务器' : '添加服务器'"
      width="550px"
      destroy-on-close
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入服务器名称" />
        </el-form-item>
        
        <el-form-item label="协议" prop="protocol">
          <el-select v-model="form.protocol" placeholder="请选择协议" style="width: 100%">
            <el-option label="WebDAV" value="webdav" />
            <el-option label="SFTP" value="sftp" disabled />
            <el-option label="FTP" value="ftp" disabled />
            <el-option label="SMB" value="smb" disabled />
          </el-select>
        </el-form-item>
        
        <el-form-item label="地址" prop="url">
          <el-input v-model="form.url" placeholder="例如: http://192.168.1.100:8080">
            <template #prepend v-if="form.protocol === 'webdav'">
              <el-select v-model="urlScheme" style="width: 90px">
                <el-option label="http://" value="http://" />
                <el-option label="https://" value="https://" />
              </el-select>
            </template>
          </el-input>
        </el-form-item>
        
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        
        <el-form-item label="密码" prop="password">
          <el-input 
            v-model="form.password" 
            type="password" 
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        
        <el-form-item label="根路径" prop="rootPath">
          <el-input v-model="form.rootPath" placeholder="例如: /documents">
            <template #prepend>/</template>
          </el-input>
        </el-form-item>
        
        <el-form-item label="描述">
          <el-input 
            v-model="form.description" 
            type="textarea" 
            :rows="2"
            placeholder="服务器描述（可选）"
          />
        </el-form-item>
        
        <el-collapse>
          <el-collapse-item title="高级配置" name="advanced">
            <el-form-item label="扩展配置">
              <el-input 
                v-model="form.extraConfig" 
                type="textarea" 
                :rows="3"
                placeholder="JSON 格式的扩展配置"
              />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button @click="testFromDialog">测试连接</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getServers, addServer, updateServer, deleteServer as deleteServerApi, testServerConnection } from '@/api/server'

const loading = ref(false)
const servers = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  name: '',
  protocol: 'webdav',
  url: '',
  username: '',
  password: '',
  rootPath: '',
  description: '',
  extraConfig: ''
})

const rules = {
  name: [{ required: true, message: '请输入服务器名称', trigger: 'blur' }],
  protocol: [{ required: true, message: '请选择协议', trigger: 'change' }],
  url: [{ required: true, message: '请输入服务器地址', trigger: 'blur' }]
}

const urlScheme = computed({
  get: () => {
    if (form.url?.startsWith('https://')) return 'https://'
    return 'http://'
  },
  set: (val) => {
    const urlWithoutScheme = form.url?.replace(/^https?:\/\//, '') || ''
    form.url = val + urlWithoutScheme
  }
})

onMounted(() => {
  loadServers()
})

async function loadServers() {
  loading.value = true
  try {
    const res = await getServers()
    servers.value = res || []
  } catch (error) {
    ElMessage.error('加载服务器列表失败')
  } finally {
    loading.value = false
  }
}

function openDialog(server = null) {
  isEdit.value = !!server
  if (server) {
    Object.assign(form, {
      id: server.id,
      name: server.name,
      protocol: server.protocol || 'webdav',
      url: server.url,
      username: server.username || '',
      password: '',
      rootPath: server.rootPath || '',
      description: server.description || '',
      extraConfig: server.extraConfig || ''
    })
  } else {
    Object.assign(form, {
      id: null,
      name: '',
      protocol: 'webdav',
      url: '',
      username: '',
      password: '',
      rootPath: '',
      description: '',
      extraConfig: ''
    })
  }
  dialogVisible.value = true
}

async function submitForm() {
  try {
    await formRef.value.validate()
    
    const data = { ...form }
    if (!data.password) delete data.password
    if (!data.rootPath) data.rootPath = '/'
    
    if (isEdit.value) {
      await updateServer(form.id, data)
      ElMessage.success('更新成功')
    } else {
      await addServer(data)
      ElMessage.success('添加成功')
    }
    
    dialogVisible.value = false
    loadServers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(isEdit.value ? '更新失败' : '添加失败')
    }
  }
}

async function testConnection(server) {
  try {
    await testServerConnection(server.id)
    ElMessage.success('连接成功')
  } catch (error) {
    ElMessage.error('连接失败: ' + (error.message || '未知错误'))
  }
}

async function testFromDialog() {
  try {
    await formRef.value.validate()
    ElMessage.info('测试连接中...')
  } catch (error) {
    // ignore
  }
}

async function deleteServer(server) {
  try {
    await ElMessageBox.confirm(
      `确定要删除服务器 "${server.name}" 吗？`,
      '确认删除',
      { type: 'warning' }
    )
    await deleteServerApi(server.id)
    ElMessage.success('删除成功')
    loadServers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

async function toggleServer(server, val) {
  try {
    await updateServer(server.id, { enabled: val })
    server.enabled = val
    ElMessage.success(val ? '已启用' : '已禁用')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

function getProtocolTag(protocol) {
  const tags = {
    webdav: '',
    sftp: 'success',
    ftp: 'warning',
    smb: 'info'
  }
  return tags[protocol] || ''
}
</script>

<style scoped>
.server-manage {
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.url-cell {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
  max-width: 230px;
}

:deep(.el-collapse) {
  border: none;
  margin-top: 10px;
}

:deep(.el-collapse-item__header) {
  background: #f5f7fa;
  padding: 0 12px;
  border-radius: 4px;
  font-size: 13px;
  color: #909399;
}

:deep(.el-collapse-item__content) {
  padding: 16px 0 0 0;
}
</style>