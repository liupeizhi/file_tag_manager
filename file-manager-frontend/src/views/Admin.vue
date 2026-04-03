<template>
  <div class="admin-container">
    <div class="admin-sidebar">
      <div class="sidebar-header">
        <h2>管理后台</h2>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/admin/servers">
          <el-icon><Monitor /></el-icon>
          <span>服务器管理</span>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer">
        <el-button type="primary" text @click="goHome">
          <el-icon><Back /></el-icon>
          返回首页
        </el-button>
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
import { Monitor, Back } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const activeMenu = computed(() => route.path)

function goHome() {
  router.push('/')
}
</script>

<style scoped>
.admin-container {
  height: 100vh;
  display: flex;
}

.admin-sidebar {
  width: 220px;
  background: #304156;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #3a4a5e;
}

.sidebar-header h2 {
  color: #fff;
  font-size: 18px;
  margin: 0;
}

.admin-sidebar .el-menu {
  border-right: none;
  flex: 1;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid #3a4a5e;
}

.sidebar-footer .el-button {
  color: #bfcbd9;
  width: 100%;
  justify-content: flex-start;
}

.admin-main {
  flex: 1;
  overflow: auto;
  background: #f5f7fa;
}
</style>