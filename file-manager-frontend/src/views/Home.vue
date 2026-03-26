<template>
  <div class="home-container">
    <AppHeader />
    <div class="main-content">
      <div class="sidebar">
        <FileTree />
      </div>
      <div class="content">
        <FileList />
      </div>
    </div>
    <ServerDialog v-model="showServerDialog" />
    <FilePreview v-model="showPreview" :file="previewFile" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import FileTree from '@/components/file/FileTree.vue'
import FileList from '@/components/file/FileList.vue'
import ServerDialog from '@/components/server/ServerDialog.vue'
import FilePreview from '@/components/preview/FilePreview.vue'

const showServerDialog = ref(false)
const showPreview = ref(false)
const previewFile = ref(null)
</script>

<style scoped>
.home-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.sidebar {
  width: 250px;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f5f7fa;
}
</style>