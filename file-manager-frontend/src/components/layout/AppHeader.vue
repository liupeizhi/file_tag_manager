<template>
  <div class="app-header glass-strong">
    <div class="logo">
      <el-icon size="24" :color="`var(--theme-primary)`"><FolderOpened /></el-icon>
      <span>文件标签浏览器</span>
    </div>
    
    <div class="server-select">
      <ThemeSwitcher />
      
      <div 
        v-if="serverStore.currentServer" 
        class="current-server"
        :title="serverStore.currentServer.url"
      >
        <el-icon><Monitor /></el-icon>
        <span>{{ serverStore.currentServer.name }}</span>
      </div>
      
      <el-select
        v-model="serverStore.currentServer"
        placeholder="选择服务器"
        @change="handleServerChange"
      >
        <el-option
          v-for="server in serverStore.servers"
          :key="server.id"
          :label="server.name"
          :value="server"
        />
      </el-select>
      
      <el-button class="glass-button" @click="goAdmin">
        <el-icon><Setting /></el-icon>
        管理后台
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useServerStore } from '@/store/server'
import { useFileStore } from '@/store/file'
import { Monitor, Setting, FolderOpened } from '@element-plus/icons-vue'
import ThemeSwitcher from '@/components/common/ThemeSwitcher.vue'

const router = useRouter()
const serverStore = useServerStore()
const fileStore = useFileStore()

onMounted(async () => {
  await serverStore.loadServers()
  
  if (serverStore.currentServer) {
    fileStore.setPath('/')
    await fileStore.loadTree(serverStore.currentServer.id, '/')
    await fileStore.loadFileList(serverStore.currentServer.id, '/')
  }
})

function handleServerChange(server) {
  serverStore.setCurrentServer(server)
  fileStore.setPath('/')
  if (server) {
    fileStore.loadTree(server.id, '/')
    fileStore.loadFileList(server.id, '/')
  }
}

function goAdmin() {
  router.push('/admin/servers')
}
</script>

<style scoped>
.app-header {
  height: 60px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--theme-surface);
  border-bottom: 1px solid var(--theme-border);
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-text);
}

.server-select {
  display: flex;
  gap: 12px;
  align-items: center;
}

.current-server {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: color-mix(in srgb, var(--theme-primary) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--theme-primary) 30%, transparent);
  border-radius: 8px;
  color: var(--theme-primary);
  font-size: 14px;
  font-weight: 500;
  cursor: help;
  transition: all var(--transition-fast);
}

.current-server:hover {
  background: color-mix(in srgb, var(--theme-primary) 15%, transparent);
  border-color: var(--theme-primary);
  transform: translateY(-1px);
}
</style>