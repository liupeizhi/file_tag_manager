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
        v-if="userStore.isLoggedIn"
        :model-value="null"
        placeholder=""
        class="server-selector"
        @change="handleServerChange"
      >
        <template #prefix>
          <el-icon><Switch /></el-icon>
        </template>
        <el-option
          v-for="server in serverStore.servers"
          :key="server.id"
          :label="server.name"
          :value="server"
        />
      </el-select>
      
      <el-dropdown
        v-if="userStore.isLoggedIn"
        @command="handleDropdownCommand"
      >
        <div class="user-info">
          <el-icon><User /></el-icon>
          <span>{{ userStore.nickname }}</span>
          <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-if="userStore.isAdmin"
              command="admin"
              :icon="Setting"
            >
              管理后台
            </el-dropdown-item>
            <el-dropdown-item
              command="logout"
              :icon="SwitchButton"
              divided
            >
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      
      <el-button
        v-if="!userStore.isLoggedIn"
        type="primary"
        @click="goLogin"
      >
        登录
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useServerStore } from '@/store/server'
import { useUserStore } from '@/store/user'
import { useFileStore } from '@/store/file'
import { Monitor, Setting, FolderOpened, User, SwitchButton, ArrowDown, Switch } from '@element-plus/icons-vue'
import ThemeSwitcher from '@/components/common/ThemeSwitcher.vue'

const router = useRouter()
const serverStore = useServerStore()
const userStore = useUserStore()
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

function handleDropdownCommand(command) {
  if (command === 'admin') {
    router.push('/admin/servers')
  } else if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}

function goLogin() {
  router.push('/login')
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
  white-space: nowrap;
}

.current-server span {
  white-space: nowrap;
}

.current-server:hover {
  background: color-mix(in srgb, var(--theme-primary) 15%, transparent);
  border-color: var(--theme-primary);
  transform: translateY(-1px);
}

.user-info {
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
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
}

.user-info span {
  white-space: nowrap;
}

.user-info:hover {
  background: color-mix(in srgb, var(--theme-primary) 15%, transparent);
  border-color: var(--theme-primary);
  transform: translateY(-1px);
}

.server-selector {
  width: 40px;
}

.server-selector :deep(.el-input__wrapper) {
  background: color-mix(in srgb, var(--theme-primary) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--theme-primary) 30%, transparent);
  box-shadow: none;
  transition: all var(--transition-fast);
  padding: 0 12px;
}

.server-selector :deep(.el-input__wrapper:hover) {
  border-color: var(--theme-primary);
}

.server-selector :deep(.el-input__inner) {
  display: none;
}

.server-selector :deep(.el-input__prefix) {
  color: var(--theme-primary);
  font-size: 18px;
}

.server-selector :deep(.el-select__suffix) {
  display: none;
}

.dropdown-icon {
  font-size: 12px;
  margin-left: 4px;
}
</style>