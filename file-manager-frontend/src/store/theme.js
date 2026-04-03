import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const themes = {
  light: {
    id: 'light',
    name: '浅色模式',
    colors: {
      primary: '#007AFF',
      background: '#f5f7fa',
      surface: '#ffffff',
      border: '#e4e7ed',
      text: '#1d1d1f',
      textSecondary: '#606266'
    }
  },
  dark: {
    id: 'dark',
    name: '深色模式',
    colors: {
      primary: '#0A84FF',
      background: '#1a1a1a',
      surface: '#2d2d2d',
      border: '#3d3d3d',
      text: '#f5f5f7',
      textSecondary: '#a0a0a0'
    }
  }
}

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref('light')
  
  const isDark = computed(() => currentTheme.value === 'dark')
  
  function toggleTheme() {
    currentTheme.value = currentTheme.value === 'light' ? 'dark' : 'light'
    applyTheme()
    saveTheme()
  }
  
  function setTheme(themeId) {
    if (themes[themeId]) {
      currentTheme.value = themeId
      applyTheme()
      saveTheme()
    }
  }
  
  function applyTheme() {
    const theme = themes[currentTheme.value]
    if (!theme) return
    
    const root = document.documentElement
    const { colors } = theme
    
    root.style.setProperty('--theme-primary', colors.primary)
    root.style.setProperty('--theme-background', colors.background)
    root.style.setProperty('--theme-surface', colors.surface)
    root.style.setProperty('--theme-border', colors.border)
    root.style.setProperty('--theme-text', colors.text)
    root.style.setProperty('--theme-text-secondary', colors.textSecondary)
    
    document.body.classList.remove('light-theme', 'dark-theme')
    document.body.classList.add(`${currentTheme.value}-theme`)
  }
  
  function saveTheme() {
    localStorage.setItem('file-manager-theme', currentTheme.value)
  }
  
  function loadTheme() {
    const saved = localStorage.getItem('file-manager-theme')
    if (saved && themes[saved]) {
      currentTheme.value = saved
    }
    applyTheme()
  }
  
  return {
    currentTheme,
    isDark,
    toggleTheme,
    setTheme,
    loadTheme
  }
})