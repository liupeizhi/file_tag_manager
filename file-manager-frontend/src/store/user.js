import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { useThemeStore } from '@/store/theme'

export const useUserStore = defineStore('user', {
  state: () => ({
    user: null,
    initialized: false
  }),

  getters: {
    isLoggedIn: (state) => !!state.user,
    isAdmin: (state) => state.user?.role === 'ADMIN',
    username: (state) => state.user?.username || '',
    nickname: (state) => state.user?.nickname || state.user?.username || '',
    userId: (state) => state.user?.id || null
  },

  actions: {
    async login(credentials) {
      const response = await authApi.login(credentials)
      if (response.code === 200) {
        this.user = response.data
        // 主题是按账号保存的, 登录后必须重新拉取,
        // 否则新浏览器/新设备登录会看不到之前设置的主题
        useThemeStore().fetchTheme()
      }
      return response
    },

    async register(userData) {
      const response = await authApi.register(userData)
      return response
    },

    async logout() {
      try {
        await authApi.logout()
      } finally {
        // 即使请求失败也要清掉本地登录态, 否则界面会停留在"已登录"的假象里
        this.user = null
      }
    },

    async fetchCurrentUser() {
      try {
        const response = await authApi.getCurrentUser()
        if (response.code === 200 && response.data) {
          this.user = response.data
          // 会话已存在时, 启动阶段的首次拉取可能早于鉴权完成, 这里补一次
          useThemeStore().fetchTheme()
        } else {
          this.user = null
        }
      } catch (error) {
        this.user = null
      } finally {
        this.initialized = true
      }
    },

    setUser(userData) {
      this.user = userData
    },

    clearUser() {
      this.user = null
    }
  }
})