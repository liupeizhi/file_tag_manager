import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getFavorites, toggleFavorite, removeFavorite as removeFavoriteApi, clearFavorites as clearFavoritesApi } from '@/api/favorite'

function keyOf(serverId, path) {
  return `${serverId}|${path}`
}

export const useFavoriteStore = defineStore('favorite', () => {
  const items = ref([])
  const loading = ref(false)
  const loaded = ref(false)

  /** O(1) 判断某条路径是否已收藏, 供文件列表逐行渲染心形使用 */
  const keys = computed(() => new Set(items.value.map((i) => keyOf(i.serverId, i.path))))

  function isFavorite(serverId, path) {
    if (serverId == null || !path) return false
    return keys.value.has(keyOf(serverId, path))
  }

  async function load(force = false) {
    if (loaded.value && !force) return
    loading.value = true
    try {
      items.value = (await getFavorites()) || []
    } catch (e) {
      items.value = []
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  /**
   * 切换收藏。返回切换后的状态(true=已收藏)。
   * 失败时抛出让调用方提示, 但本地列表保持不变。
   */
  async function toggle(row) {
    if (!row || row.serverId == null || !row.path) {
      // 调用方可能只传 {serverId, path, name, isDirectory}
      throw new Error('缺少 serverId 或 path')
    }
    const result = await toggleFavorite({
      serverId: row.serverId,
      path: row.path,
      name: row.name,
      isDirectory: !!row.isDirectory
    })
    if (!result) return false

    if (result.favorited && result.favorite) {
      const rest = items.value.filter(
        (i) => !(i.serverId === result.favorite.serverId && i.path === result.favorite.path)
      )
      items.value = [result.favorite, ...rest]
    } else {
      items.value = items.value.filter(
        (i) => !(i.serverId === row.serverId && i.path === row.path)
      )
    }
    loaded.value = true
    return !!result.favorited
  }

  async function remove(id) {
    try {
      await removeFavoriteApi(id)
      items.value = items.value.filter((i) => i.id !== id)
    } catch (e) {
      // 静默失败
    }
  }

  async function clear() {
    try {
      await clearFavoritesApi()
      items.value = []
    } catch (e) {
      // 静默失败
    }
  }

  return { items, loading, loaded, isFavorite, load, toggle, remove, clear }
})
