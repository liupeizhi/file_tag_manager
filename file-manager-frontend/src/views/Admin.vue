<template>
  <div class="admin-container">
    <div class="admin-sidebar">
      <div class="sidebar-header">
        <span class="logo-mark"><el-icon size="17"><Setting /></el-icon></span>
        <h2>管理后台</h2>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="admin-menu"
      >
        <el-menu-item index="/admin/servers">
          <el-icon><Monitor /></el-icon>
          <span>服务器管理</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer">
        <button class="back-button" @click="goHome">
          <el-icon><Back /></el-icon>
          返回首页
        </button>
      </div>
    </div>
    <div class="admin-main">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Monitor, Back, User, Setting } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const isAdmin = computed(() => userStore.isAdmin)

function goHome() {
  router.push('/')
}
</script>

<style scoped>
.admin-container {
  height: 100vh;
  display: flex;
  gap: 12px;
  padding: 12px;
  background: var(--canvas-bg);
}

.admin-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: var(--theme-surface);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 16px;
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

.sidebar-header h2 {
  color: var(--theme-text);
  font-size: 14px;
  font-weight: 600;
  margin: 0;
}

.admin-menu {
  border-right: none;
  background: transparent;
  flex: 1;
  padding: 0 8px;
}

.admin-menu :deep(.el-menu-item) {
  height: 34px;
  line-height: 34px;
  border-radius: var(--radius-input);
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 2px;
}

.admin-menu :deep(.el-menu-item:hover) {
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.admin-menu :deep(.el-menu-item.is-active) {
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-weight: 600;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid var(--border-light);
}

.back-button {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  background: transparent;
  border: none;
  border-radius: var(--radius-input);
  color: var(--text-secondary);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.back-button:hover {
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.admin-main {
  flex: 1;
  min-width: 0;
  background: var(--theme-surface);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>