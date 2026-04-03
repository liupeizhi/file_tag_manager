import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { getFileTree, getFileList, uploadFile, deleteFile, renameFile, createFolder, syncFiles } from '@/api/file'

export const useFileStore = defineStore('file', () => {
  const treeData = ref([])
  const fileList = ref([])
  const currentPath = ref('/')
  const loading = ref(false)
  
  async function loadTree(serverId, path = '/') {
    loading.value = true
    try {
      treeData.value = await getFileTree(serverId, path)
    } finally {
      loading.value = false
    }
  }
  
  async function loadFileList(serverId, path = '/', filters = {}) {
    loading.value = true
    try {
      const result = await getFileList({
        serverId,
        path,
        size: 1000,  // 获取足够多的文件
        ...filters
      })
      const newList = result.content || []
      
      // 强制触发响应式更新
      fileList.value = []
      await new Promise(resolve => setTimeout(resolve, 0))
      fileList.value = newList
    } catch (error) {
      console.error('加载文件列表失败:', error)
      fileList.value = []
    } finally {
      loading.value = false
    }
  }
  
  async function upload(serverId, path, file, onProgress) {
    await uploadFile(serverId, path, file, onProgress)
  }
  
  async function remove(serverId, path) {
    await deleteFile(serverId, path)
  }
  
  async function rename(serverId, oldPath, newPath) {
    await renameFile(serverId, oldPath, newPath)
  }
  
  async function createDir(serverId, path) {
    await createFolder(serverId, path)
  }
  
  async function sync(serverId, path = '/') {
    await syncFiles(serverId, path)
  }
  
  function setPath(path) {
    currentPath.value = path
  }
  
  return {
    treeData,
    fileList,
    currentPath,
    loading,
    loadTree,
    loadFileList,
    upload,
    remove,
    rename,
    createDir,
    sync,
    setPath
  }
})