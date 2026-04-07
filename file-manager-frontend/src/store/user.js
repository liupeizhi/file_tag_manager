import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'

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
      }
      return response
    },

    async register(userData) {
      const response = await authApi.register(userData)
      return response
    },

    async logout() {
      await authApi.logout()
      this.user = null
    },

    async fetchCurrentUser() {
      try {
        const response = await authApi.getCurrentUser()
        if (response.code === 200 && response.data) {
          this.user = response.data
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