import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getHistory, recordHistory, removeHistory as removeHistoryApi, clearHistory as clearHistoryApi } from '@/api/history'

const MAX_ENTRIES = 100

export const useHistoryStore = defineStore('history', () => {
  const items = ref([])
  const loading = ref(false)
  const loaded = ref(false)

  async function load(force = false) {
    if (loaded.value && !force) return
    loading.value = true
    try {
      items.value = (await getHistory()) || []
    } catch (e) {
      items.value = []
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  /**
   * 记录一次访问。
   * 后端按 (服务器, 路径) 去重并只保留最近 100 条,
   * 这里同步做本地乐观更新, 避免每次导航都重新拉一遍列表。
   * 失败时静默 —— 历史记录不能影响正常浏览。
   */
  async function record(entry) {
    if (!entry || !entry.serverId || !entry.path) return
    try {
      const saved = await recordHistory({
        serverId: entry.serverId,
        path: entry.path,
        name: entry.name,
        isDirectory: !!entry.isDirectory
      })
      if (!saved) return
      const rest = items.value.filter(
        (i) => !(i.serverId === saved.serverId && i.path === saved.path)
      )
      items.value = [saved, ...rest].slice(0, MAX_ENTRIES)
      loaded.value = true
    } catch (e) {
      // 静默失败
    }
  }

  async function remove(id) {
    try {
      await removeHistoryApi(id)
      items.value = items.value.filter((i) => i.id !== id)
    } catch (e) {
      // 静默失败
    }
  }

  async function clear() {
    try {
      await clearHistoryApi()
      items.value = []
    } catch (e) {
      // 静默失败
    }
  }

  return { items, loading, loaded, load, record, remove, clear }
})
