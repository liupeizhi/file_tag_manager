<template>
  <div class="history-list">
    <div class="history-header">
      <span>访问历史</span>
      <el-tooltip content="清空历史" placement="bottom" :disabled="!historyStore.items.length">
        <el-button
          link
          :disabled="!historyStore.items.length"
          @click="handleClear"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </el-tooltip>
    </div>

    <div class="history-content" v-loading="historyStore.loading">
      <div v-if="!historyStore.items.length" class="history-empty">
        还没有访问记录
      </div>

      <div
        v-for="item in historyStore.items"
        :key="item.id"
        class="history-item"
        :class="{ 'is-dir': item.isDirectory }"
        @click="handleClick(item)"
      >
        <el-icon class="history-icon" :color="item.isDirectory ? '#F9A825' : undefined">
          <Folder v-if="item.isDirectory" />
          <component v-else :is="fileIcon(item.name)" />
        </el-icon>

        <div class="history-main">
          <div class="history-name" :title="item.path">{{ item.name }}</div>
          <div class="history-meta">
            <span class="history-time">{{ formatDate(item.accessedAt) }}</span>
            <span v-if="item.serverName" class="history-server">{{ item.serverName }}</span>
          </div>
        </div>

        <el-icon class="history-remove" @click.stop="handleRemove(item)">
          <Close />
        </el-icon>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Close, Folder, Document, Picture, VideoPlay, Headset } from '@element-plus/icons-vue'
import { useHistoryStore } from '@/store/history'
import { getFileTypeInfo } from '@/utils/file-type'
import { formatDate } from '@/utils/date-format'

const emit = defineEmits(['navigate', 'preview'])

const historyStore = useHistoryStore()

onMounted(() => {
  historyStore.load(true)
})

function fileIcon(name) {
  const info = getFileTypeInfo(name)
  const map = { Picture, VideoPlay, Headset, Document }
  const icon = map[info.icon]
  return icon || Document
}

function handleClick(item) {
  if (item.isDirectory) {
    emit('navigate', item)
  } else {
    emit('preview', item)
  }
}

async function handleRemove(item) {
  await historyStore.remove(item.id)
}

async function handleClear() {
  try {
    await ElMessageBox.confirm('确定清空全部访问历史吗？', '清空历史', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await historyStore.clear()
  ElMessage.success('已清空')
}
</script>

<style scoped>
.history-list {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px 6px;
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.history-content {
  flex: 1;
  overflow-y: auto;
  padding: 0 6px 8px;
  min-height: 0;
}

.history-empty {
  padding: 26px 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-muted);
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--radius-input);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.history-item:hover {
  background: var(--bg-secondary);
}

.history-icon {
  flex-shrink: 0;
  font-size: 15px;
  color: var(--text-secondary);
}

.history-main {
  flex: 1;
  min-width: 0;
}

.history-name {
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 1px;
  font-size: 11px;
  color: var(--text-muted);
}

.history-server {
  max-width: 120px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-remove {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-muted);
  opacity: 0;
  transition: opacity var(--transition-fast), color var(--transition-fast);
}

.history-item:hover .history-remove {
  opacity: 1;
}

.history-remove:hover {
  color: var(--error);
}
</style>
