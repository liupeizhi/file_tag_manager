<template>
  <el-dialog
    v-model="visible"
    title="上传文件"
    width="600px"
    @close="resetUpload"
  >
    <el-upload
      ref="uploadRef"
      :auto-upload="false"
      :on-change="handleFileChange"
      :file-list="fileList"
      drag
      multiple
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">
        拖拽文件到此处或 <em>点击上传</em>
      </div>
    </el-upload>
    
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleUpload" :loading="uploading">
        上传
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useFileStore } from '@/store/file'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: Boolean,
  serverId: Number,
  path: String
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = ref(false)
const uploading = ref(false)
const fileList = ref([])
const fileStore = useFileStore()

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

function handleFileChange(file) {
  fileList.value.push(file)
}

async function handleUpload() {
  if (fileList.value.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  
  uploading.value = true
  
  try {
    for (const file of fileList.value) {
      await fileStore.upload(props.serverId, props.path, file.raw, (progress) => {
        file.percentage = Math.round((progress.loaded / progress.total) * 100)
      })
    }
    
    ElMessage.success('上传成功')
    emit('success')
    visible.value = false
    resetUpload()
  } catch (error) {
    ElMessage.error('上传失败: ' + error.message)
  } finally {
    uploading.value = false
  }
}

function resetUpload() {
  fileList.value = []
}
</script>

<style scoped>
.el-icon--upload {
  font-size: 67px;
  color: #409eff;
  margin: 40px 0 16px;
  line-height: 50px;
}
</style>