<template>
  <div class="app-header">
    <div class="logo">
      <el-icon size="24"><FolderOpened /></el-icon>
      <span>文件标签浏览器</span>
    </div>
    
    <div class="server-select">
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
      
      <el-button type="primary" @click="showServerDialog = true">
        <el-icon><Plus /></el-icon>
        添加服务器
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useServerStore } from '@/store/server'
import { useFileStore } from '@/store/file'
import ServerDialog from '@/components/server/ServerDialog.vue'

const serverStore = useServerStore()
const fileStore = useFileStore()
const showServerDialog = ref(false)

onMounted(() => {
  serverStore.loadServers()
})

function handleServerChange(server) {
  serverStore.setCurrentServer(server)
  fileStore.setPath('/')
  if (server) {
    fileStore.loadTree(server.id, '/')
    fileStore.loadFileList(server.id, '/')
  }
}
</script>

<style scoped>
.app-header {
  height: 60px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
}

.server-select {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>