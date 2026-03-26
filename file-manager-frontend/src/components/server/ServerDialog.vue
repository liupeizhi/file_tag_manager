<template>
  <el-dialog
    v-model="visible"
    title="服务器配置"
    width="500px"
    @close="resetForm"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="服务器名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入服务器名称" />
      </el-form-item>
      
      <el-form-item label="WebDAV地址" prop="url">
        <el-input v-model="form.url" placeholder="例如: https://dav.example.com" />
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
    </el-form>
    
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="loading">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useServerStore } from '@/store/server'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: Boolean
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const loading = ref(false)
const formRef = ref(null)
const serverStore = useServerStore()

const form = ref({
  name: '',
  url: '',
  username: '',
  password: ''
})

const rules = {
  name: [{ required: true, message: '请输入服务器名称', trigger: 'blur' }],
  url: [{ required: true, message: '请输入WebDAV地址', trigger: 'blur' }]
}

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

async function handleSubmit() {
  await formRef.value.validate()
  
  loading.value = true
  try {
    await serverStore.add(form.value)
    ElMessage.success('添加成功')
    visible.value = false
    resetForm()
  } catch (error) {
    ElMessage.error('添加失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.value = {
    name: '',
    url: '',
    username: '',
    password: ''
  }
  formRef.value?.resetFields()
}
</script>