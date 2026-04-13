<template>
  <el-dialog
    v-model="visible"
    title="服务器配置"
    width="600px"
    @close="resetForm"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
      <el-form-item label="服务器名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入服务器名称" />
      </el-form-item>
      
      <el-form-item label="协议类型" prop="protocol">
        <el-select v-model="form.protocol" @change="onProtocolChange" style="width: 100%">
          <el-option label="WebDAV" value="webdav" />
          <el-option label="SMB" value="smb" />
          <el-option label="SFTP" value="sftp" />
          <el-option label="FTP" value="ftp" />
        </el-select>
      </el-form-item>
      
      <el-form-item label="用户名">
        <el-input v-model="form.username" placeholder="请输入用户名" />
      </el-form-item>
      
      <el-form-item label="密码">
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
      
      <el-form-item label="根路径">
        <el-input v-model="form.rootPath" placeholder="/" />
      </el-form-item>
      
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleTest" :loading="testing">测试连接</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="loading">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { useServerStore } from '@/store/server'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: Boolean,
  server: Object
})

const emit = defineEmits(['update:modelValue', 'saved'])

const visible = ref(false)
const loading = ref(false)
const testing = ref(false)
const formRef = ref(null)
const serverStore = useServerStore()
const isEdit = ref(false)

const form = ref(getDefaultForm())

function getDefaultForm() {
  return {
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

const rules = computed(() => {
  const baseRules = {
    name: [{ required: true, message: '请输入服务器名称', trigger: 'blur' }]
  }
  
  if (form.value.protocol === 'webdav') {
    baseRules.url = [{ required: true, message: '请输入服务器地址', trigger: 'blur' }]
  } else {
    baseRules.host = [{ required: true, message: '请输入主机地址', trigger: 'blur' }]
    if (form.value.protocol === 'smb') {
      baseRules.shareName = [{ required: true, message: '请输入共享名', trigger: 'blur' }]
    }
  }
  
  return baseRules
})

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.server) {
    isEdit.value = true
    form.value = { ...getDefaultForm(), ...props.server }
    if (!form.value.port) {
      form.value.port = getDefaultPort(form.value.protocol)
    }
    form.value.authType = form.value.privateKey ? 'privateKey' : 'password'
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

function getDefaultPort(protocol) {
  switch (protocol) {
    case 'smb': return 445
    case 'sftp': return 22
    case 'ftp': return 21
    default: return null
  }
}

function onProtocolChange(protocol) {
  form.value.port = getDefaultPort(protocol)
  if (protocol !== 'smb') {
    form.value.shareName = ''
    form.value.domain = ''
  }
  if (protocol !== 'sftp') {
    form.value.privateKey = ''
    form.value.authType = 'password'
  }
  if (protocol !== 'ftp') {
    form.value.passiveMode = true
  }
  if (protocol !== 'webdav') {
    form.value.url = ''
  }
  if (protocol === 'webdav') {
    form.value.host = ''
  }
}

async function handleTest() {
  await formRef.value.validate()
  
  testing.value = true
  try {
    const data = prepareFormData()
    if (isEdit.value) {
      await serverStore.test(props.server.id)
    } else {
      const result = await serverStore.add(data)
      if (result) {
        ElMessage.success('连接测试成功')
      }
    }
    ElMessage.success('连接测试成功')
  } catch (error) {
    ElMessage.error('连接失败: ' + error.message)
  } finally {
    testing.value = false
  }
}

async function handleSubmit() {
  await formRef.value.validate()
  
  loading.value = true
  try {
    const data = prepareFormData()
    if (isEdit.value) {
      await serverStore.update(props.server.id, data)
      ElMessage.success('更新成功')
    } else {
      await serverStore.add(data)
      ElMessage.success('添加成功')
    }
    visible.value = false
    resetForm()
    emit('saved')
  } catch (error) {
    ElMessage.error(isEdit.value ? '更新失败: ' : '添加失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

function prepareFormData() {
  const data = { ...form.value }
  
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
  
  return data
}

function resetForm() {
  form.value = getDefaultForm()
  isEdit.value = false
  formRef.value?.resetFields()
}
</script>