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
      <el-table-column label="地址" min-width="250">
        <template #default="{ row }">
          <span>{{ getServerAddress(row) }}</span>
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
      width="600px"
      destroy-on-close
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入服务器名称" />
        </el-form-item>
        
        <el-form-item label="协议" prop="protocol">
          <el-select v-model="form.protocol" @change="onProtocolChange" style="width: 100%">
            <el-option label="WebDAV" value="webdav" />
            <el-option label="SMB" value="smb" />
            <el-option label="SFTP" value="sftp" />
            <el-option label="FTP" value="ftp" />
          </el-select>
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
        
        <template v-if="form.protocol === 'webdav'">
          <el-form-item label="服务器地址" prop="url">
            <el-input v-model="form.url" placeholder="https://nas.example.com/webdav" />
          </el-form-item>
        </template>
        
        <template v-if="form.protocol === 'smb'">
          <el-form-item label="主机地址" prop="host">
            <el-input v-model="form.host" placeholder="192.168.1.100" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="共享名" prop="shareName">
            <el-input v-model="form.shareName" placeholder="share" />
          </el-form-item>
          <el-form-item label="域">
            <el-input v-model="form.domain" placeholder="WORKGROUP" />
          </el-form-item>
        </template>
        
        <template v-if="form.protocol === 'sftp'">
          <el-form-item label="主机地址" prop="host">
            <el-input v-model="form.host" placeholder="192.168.1.100" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="认证方式">
            <el-select v-model="form.authType" style="width: 100%">
              <el-option label="密码" value="password" />
              <el-option label="私钥" value="privateKey" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.authType === 'privateKey'" label="私钥">
            <el-input v-model="form.privateKey" type="textarea" :rows="5" placeholder="-----BEGIN RSA PRIVATE KEY-----" />
          </el-form-item>
        </template>
        
        <template v-if="form.protocol === 'ftp'">
          <el-form-item label="主机地址" prop="host">
            <el-input v-model="form.host" placeholder="192.168.1.100" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="被动模式">
            <el-checkbox v-model="form.passiveMode">启用被动模式（防火墙环境下建议启用）</el-checkbox>
          </el-form-item>
        </template>
        
        <el-form-item label="根路径" prop="rootPath">
          <el-input v-model="form.rootPath" placeholder="/" />
        </el-form-item>
        
        <el-form-item label="描述">
          <el-input 
            v-model="form.description" 
            type="textarea" 
            :rows="2"
            placeholder="服务器描述（可选）"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button @click="testFromDialog" :loading="testing">测试连接</el-button>
        <el-button type="primary" @click="submitForm" :loading="saving">保存</el-button>
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
const testing = ref(false)
const saving = ref(false)
const servers = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  name: '',
  protocol: 'webdav',
  url: '',
  host: '',
  port: null,
  shareName: '',
  domain: '',
  username: '',
  password: '',
  privateKey: '',
  passiveMode: true,
  authType: 'password',
  rootPath: '/',
  description: '',
  enabled: true
})

const rules = computed(() => {
  const baseRules = {
    name: [{ required: true, message: '请输入服务器名称', trigger: 'blur' }],
    protocol: [{ required: true, message: '请选择协议', trigger: 'change' }]
  }
  
  if (form.protocol === 'webdav') {
    baseRules.url = [{ required: true, message: '请输入服务器地址', trigger: 'blur' }]
  } else {
    baseRules.host = [{ required: true, message: '请输入主机地址', trigger: 'blur' }]
    if (form.protocol === 'smb') {
      baseRules.shareName = [{ required: true, message: '请输入共享名', trigger: 'blur' }]
    }
  }
  
  return baseRules
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

function getDefaultPort(protocol) {
  switch (protocol) {
    case 'smb': return 445
    case 'sftp': return 22
    case 'ftp': return 21
    default: return null
  }
}

function getDefaultForm() {
  return {
    id: null,
    name: '',
    protocol: 'webdav',
    url: '',
    host: '',
    port: null,
    shareName: '',
    domain: '',
    username: '',
    password: '',
    privateKey: '',
    passiveMode: true,
    authType: 'password',
    rootPath: '/',
    description: '',
    enabled: true
  }
}

function onProtocolChange(protocol) {
  form.port = getDefaultPort(protocol)
  if (protocol !== 'smb') {
    form.shareName = ''
    form.domain = ''
  }
  if (protocol !== 'sftp') {
    form.privateKey = ''
    form.authType = 'password'
  }
  if (protocol !== 'ftp') {
    form.passiveMode = true
  }
  if (protocol !== 'webdav') {
    form.url = ''
  }
  if (protocol === 'webdav') {
    form.host = ''
  }
}

function openDialog(server = null) {
  isEdit.value = !!server
  if (server) {
    Object.assign(form, getDefaultForm(), {
      id: server.id,
      name: server.name,
      protocol: server.protocol || 'webdav',
      url: server.url || '',
      host: server.host || '',
      port: server.port || getDefaultPort(server.protocol),
      shareName: server.shareName || '',
      domain: server.domain || '',
      username: server.username || '',
      password: '',
      privateKey: server.privateKey || '',
      passiveMode: server.passiveMode !== false,
      authType: server.privateKey ? 'privateKey' : 'password',
      rootPath: server.rootPath || '/',
      description: server.description || '',
      enabled: server.enabled !== false
    })
  } else {
    Object.assign(form, getDefaultForm())
  }
  dialogVisible.value = true
}

function prepareFormData() {
  const data = { ...form }
  
  if (data.authType === 'password') {
    data.privateKey = ''
  }
  
  if (data.protocol === 'webdav') {
    data.host = ''
    data.port = null
    data.shareName = ''
    data.domain = ''
    data.privateKey = ''
    data.passiveMode = true
  }
  
  if (!data.password) delete data.password
  
  return data
}

async function submitForm() {
  try {
    await formRef.value.validate()
    
    saving.value = true
    const data = prepareFormData()
    
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
  } finally {
    saving.value = false
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
    
    testing.value = true
    const data = prepareFormData()
    
    if (isEdit.value) {
      await testServerConnection(form.id)
      ElMessage.success('连接成功')
    } else {
      const result = await addServer(data)
      if (result) {
        await testServerConnection(result.id)
        ElMessage.success('连接成功')
      }
    }
  } catch (error) {
    ElMessage.error('连接失败: ' + (error.message || '未知错误'))
  } finally {
    testing.value = false
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

function getServerAddress(server) {
  if (server.protocol === 'webdav' || !server.protocol) {
    return server.url
  }
  const host = server.host || ''
  const port = server.port ? `:${server.port}` : ''
  if (server.protocol === 'smb') {
    return `${host}${port}/${server.shareName || ''}`
  }
  return `${host}${port}`
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
</style>