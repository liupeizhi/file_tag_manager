<template>
  <div class="favorite-list">
    <div class="favorite-header">
      <span>收藏</span>
      <el-tooltip content="清空收藏" placement="bottom" :disabled="!favoriteStore.items.length">
        <el-button
          link
          :disabled="!favoriteStore.items.length"
          @click="handleClear"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </el-tooltip>
    </div>

    <div class="favorite-content" v-loading="favoriteStore.loading">
      <div v-if="!favoriteStore.items.length" class="favorite-empty">
        还没有收藏任何文件
      </div>

      <div
        v-for="item in favoriteStore.items"
        :key="item.id"
        class="favorite-item"
        @click="handleClick(item)"
      >
        <el-icon class="favorite-icon" :color="item.isDirectory ? '#F9A825' : undefined">
          <Folder v-if="item.isDirectory" />
          <component v-else :is="fileIcon(item.name)" />
        </el-icon>

        <div class="favorite-main">
          <div class="favorite-name" :title="item.path">{{ item.name }}</div>
          <div class="favorite-meta">
            <span v-if="item.serverName">{{ item.serverName }}</span>
          </div>
        </div>

        <el-tooltip content="取消收藏" placement="left">
          <svg class="fav-heart" viewBox="0 0 24 24" @click.stop="handleUnfavorite(item)">
            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
          </svg>
        </el-tooltip>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Folder, Document, Picture, VideoPlay, Headset } from '@element-plus/icons-vue'
import { useFavoriteStore } from '@/store/favorite'
import { getFileTypeInfo } from '@/utils/file-type'

const emit = defineEmits(['navigate', 'preview'])

const favoriteStore = useFavoriteStore()

onMounted(() => {
  favoriteStore.load(true)
})

function fileIcon(name) {
  const info = getFileTypeInfo(name)
  const map = { Picture, VideoPlay, Headset, Document }
  return map[info.icon] || Document
}

function handleClick(item) {
  if (item.isDirectory) {
    emit('navigate', item)
  } else {
    emit('preview', item)
  }
}

async function handleUnfavorite(item) {
  await favoriteStore.remove(item.id)
  ElMessage.success('已取消收藏')
}

async function handleClear() {
  try {
    await ElMessageBox.confirm('确定清空全部收藏吗？', '清空收藏', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await favoriteStore.clear()
  ElMessage.success('已清空')
}
</script>

<style scoped>
.favorite-list {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.favorite-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px 6px;
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.favorite-content {
  flex: 1;
  overflow-y: auto;
  padding: 0 6px 8px;
  min-height: 0;
}

.favorite-empty {
  padding: 26px 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-muted);
}

.favorite-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--radius-input);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.favorite-item:hover {
  background: var(--bg-secondary);
}

.favorite-icon {
  flex-shrink: 0;
  font-size: 15px;
  color: var(--text-secondary);
}

.favorite-main {
  flex: 1;
  min-width: 0;
}

.favorite-name {
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.favorite-meta {
  margin-top: 1px;
  font-size: 11px;
  color: var(--text-muted);
}

.fav-heart {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  fill: #f56c6c;
  stroke: #f56c6c;
  stroke-width: 2;
  cursor: pointer;
  opacity: 0;
  transition: opacity var(--transition-fast), transform var(--transition-fast);
}

.favorite-item:hover .fav-heart {
  opacity: 1;
}

.fav-heart:hover {
  transform: scale(1.15);
}
</style>
