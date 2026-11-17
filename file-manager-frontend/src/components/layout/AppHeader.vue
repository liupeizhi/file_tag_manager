<template>
  <div class="app-header">
    <div class="logo">
      <span class="logo-mark"><el-icon size="17"><FolderOpened /></el-icon></span>
      <span>文件标签浏览器</span>
    </div>
    
    <div class="header-actions">
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

      <el-tooltip content="标签关系图" placement="bottom">
        <el-button circle class="icon-button graph-button" @click="tagGraphRef?.open()">
          <el-icon :size="16"><Connection /></el-icon>
        </el-button>
      </el-tooltip>

      <el-tooltip content="主题设置" placement="bottom">
        <el-button circle class="icon-button" @click="router.push('/theme')">
          <el-icon :size="16"><Brush /></el-icon>
        </el-button>
      </el-tooltip>
      
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
              command="theme"
              :icon="Brush"
            >
              主题设置
            </el-dropdown-item>
            <el-dropdown-item
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

    <TagGraphDialog ref="tagGraphRef" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useServerStore } from '@/store/server'
import { useUserStore } from '@/store/user'
import { useFileStore } from '@/store/file'
import { Monitor, Setting, FolderOpened, User, SwitchButton, ArrowDown, Switch, Brush, Connection } from '@element-plus/icons-vue'
import ThemeSwitcher from '@/components/common/ThemeSwitcher.vue'
import TagGraphDialog from '@/components/graph/TagGraphDialog.vue'

const router = useRouter()
const serverStore = useServerStore()
const userStore = useUserStore()
const fileStore = useFileStore()
const tagGraphRef = ref(null)

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

async function handleDropdownCommand(command) {
  if (command === 'theme') {
    router.push('/theme')
  } else if (command === 'admin') {
    router.push('/admin/servers')
  } else if (command === 'logout') {
    // 必须等登出完成再跳转: 否则路由守卫此时仍认为用户处于登录态,
    // 会把 /login 又弹回首页, 表现为"退出后没跳转到登录页"
    await userStore.logout()
    router.push('/login')
  }
}

function goLogin() {
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  flex-shrink: 0;
  height: 52px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--theme-surface);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.logo {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text);
}

.logo-mark {
  width: 26px;
  height: 26px;
  border-radius: var(--radius-input);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.icon-button {
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-secondary);
}

.icon-button:hover {
  border-color: var(--border-color);
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.current-server {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-input);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: help;
  transition: all var(--transition-fast);
  white-space: nowrap;
}

.current-server span {
  white-space: nowrap;
}

.current-server:hover {
  border-color: var(--text-muted);
  color: var(--text-primary);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-input);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
}

.user-info span {
  white-space: nowrap;
}

.user-info:hover {
  border-color: var(--text-muted);
  color: var(--text-primary);
}

.server-selector {
  width: 40px;
}

.server-selector :deep(.el-input__wrapper) {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-input);
  box-shadow: none;
  transition: all var(--transition-fast);
  padding: 0 12px;
}

.server-selector :deep(.el-input__wrapper:hover) {
  border-color: var(--text-muted);
}

.server-selector :deep(.el-input__inner) {
  display: none;
}

.server-selector :deep(.el-input__prefix) {
  color: var(--text-secondary);
  font-size: 16px;
}

.server-selector :deep(.el-select__suffix) {
  display: none;
}

.dropdown-icon {
  font-size: 12px;
  margin-left: 2px;
}
</style>