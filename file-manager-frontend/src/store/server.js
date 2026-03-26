import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getServers, addServer, updateServer, deleteServer, testConnection } from '@/api/server'

export const useServerStore = defineStore('server', () => {
  const servers = ref([])
  const currentServer = ref(null)
  const loading = ref(false)
  
  async function loadServers() {
    loading.value = true
    try {
      servers.value = await getServers()
      if (servers.value.length > 0 && !currentServer.value) {
        currentServer.value = servers.value[0]
      }
    } finally {
      loading.value = false
    }
  }
  
  async function add(data) {
    await addServer(data)
    await loadServers()
  }
  
  async function update(id, data) {
    await updateServer(id, data)
    await loadServers()
  }
  
  async function remove(id) {
    await deleteServer(id)
    if (currentServer.value?.id === id) {
      currentServer.value = servers.value[0] || null
    }
    await loadServers()
  }
  
  async function test(id) {
    return await testConnection(id)
  }
  
  function setCurrentServer(server) {
    currentServer.value = server
  }
  
  return {
    servers,
    currentServer,
    loading,
    loadServers,
    add,
    update,
    remove,
    test,
    setCurrentServer
  }
})