<template>
  <el-dialog
    v-model="visible"
    :title="file?.name || '文件预览'"
    width="80%"
    top="5vh"
  >
    <div class="preview-container">
      <div v-if="fileType === 'image'" class="preview-image">
        <el-image :src="previewUrl" fit="contain" />
      </div>
      
      <div v-else-if="fileType === 'video'" class="preview-video">
        <video :src="previewUrl" controls style="width: 100%; max-height: 70vh" />
      </div>
      
      <div v-else-if="fileType === 'audio'" class="preview-audio">
        <audio :src="previewUrl" controls style="width: 100%" />
      </div>
      
      <div v-else class="preview-text">
        <el-empty description="暂不支持预览此类型文件">
          <el-button type="primary" @click="handleDownload">下载查看</el-button>
        </el-empty>
      </div>
    </div>
    
    <template #footer>
      <div class="file-info">
        <span>{{ file?.name }} | {{ formatSize(file?.size) }}</span>
      </div>
      <el-button @click="handleDownload">下载</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getFileTypeInfo, formatFileSize } from '@/utils/file-type'
import { downloadFile } from '@/api/file'

const props = defineProps({
  modelValue: Boolean,
  file: Object,
  serverId: Number
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

const fileType = computed(() => {
  if (!props.file) return 'other'
  const typeInfo = getFileTypeInfo(props.file.name)
  return typeInfo.type
})

const previewUrl = computed(() => {
  if (!props.file || !props.serverId) return ''
  return `/api/preview/${props.serverId}/${fileType.value}?path=${encodeURIComponent(props.file.path)}`
})

function formatSize(size) {
  return formatFileSize(size)
}

function handleDownload() {
  if (props.file && props.serverId) {
    const url = downloadFile(props.serverId, props.file.path)
    window.open(url, '_blank')
  }
}
</script>

<style scoped>
.preview-container {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-image,
.preview-video,
.preview-audio {
  width: 100%;
}

.file-info {
  flex: 1;
  color: #909399;
  font-size: 14px;
}
</style>