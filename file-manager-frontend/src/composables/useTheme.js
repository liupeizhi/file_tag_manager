import { ref, watch } from 'vue'

const themes = {
  ocean: {
    name: '海洋',
    primary: '#007AFF',
    secondary: '#5AC8FA',
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    glass: {
      background: 'rgba(255, 255, 255, 0.72)',
      border: 'rgba(255, 255, 255, 0.5)',
      shadow: 'rgba(0, 122, 255, 0.1)',
      text: '#1d1d1f'
    }
  },
  sunset: {
    name: '日落',
    primary: '#FF6B6B',
    secondary: '#FFA07A',
    gradient: 'linear-gradient(135deg, #ff6b6b 0%, #ffa07a 100%)',
    background: 'linear-gradient(135deg, #ff6b6b 0%, #ffa07a 100%)',
    glass: {
      background: 'rgba(255, 255, 255, 0.72)',
      border: 'rgba(255, 107, 107, 0.3)',
      shadow: 'rgba(255, 107, 107, 0.15)',
      text: '#1d1d1f'
    }
  },
  forest: {
    name: '森林',
    primary: '#34C759',
    secondary: '#30D158',
    gradient: 'linear-gradient(135deg, #34c759 0%, #30d158 100%)',
    background: 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)',
    glass: {
      background: 'rgba(255, 255, 255, 0.72)',
      border: 'rgba(52, 199, 89, 0.3)',
      shadow: 'rgba(52, 199, 89, 0.15)',
      text: '#1d1d1f'
    }
  },
  purple: {
    name: '紫罗兰',
    primary: '#AF52DE',
    secondary: '#BF5AF2',
    gradient: 'linear-gradient(135deg, #af52de 0%, #bf5af2 100%)',
    background: 'linear-gradient(135deg, #a855f7 0%, #ec4899 100%)',
    glass: {
      background: 'rgba(255, 255, 255, 0.72)',
      border: 'rgba(175, 82, 222, 0.3)',
      shadow: 'rgba(175, 82, 222, 0.15)',
      text: '#1d1d1f'
    }
  },
  dark: {
    name: '深色',
    primary: '#0A84FF',
    secondary: '#5AC8FA',
    gradient: 'linear-gradient(135deg, #1c1c1e 0%, #2c2c2e 100%)',
    background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)',
    glass: {
      background: 'rgba(28, 28, 30, 0.72)',
      border: 'rgba(255, 255, 255, 0.1)',
      shadow: 'rgba(0, 0, 0, 0.3)',
      text: '#f5f5f7'
    }
  },
  rose: {
    name: '玫瑰',
    primary: '#FF2D55',
    secondary: '#FF375F',
    gradient: 'linear-gradient(135deg, #ff2d55 0%, #ff375f 100%)',
    background: 'linear-gradient(135deg, #ff416c 0%, #ff4b2b 100%)',
    glass: {
      background: 'rgba(255, 255, 255, 0.72)',
      border: 'rgba(255, 45, 85, 0.3)',
      shadow: 'rgba(255, 45, 85, 0.15)',
      text: '#1d1d1f'
    }
  }
}

const currentTheme = ref(localStorage.getItem('theme') || 'ocean')

function setTheme(themeName) {
  if (themes[themeName]) {
    currentTheme.value = themeName
    localStorage.setItem('theme', themeName)
    applyTheme(themes[themeName])
  }
}

function applyTheme(theme) {
  const root = document.documentElement
  
  root.style.setProperty('--theme-primary', theme.primary)
  root.style.setProperty('--theme-secondary', theme.secondary)
  root.style.setProperty('--theme-gradient', theme.gradient)
  root.style.setProperty('--theme-background', theme.background)
  root.style.setProperty('--theme-glass-bg', theme.glass.background)
  root.style.setProperty('--theme-glass-border', theme.glass.border)
  root.style.setProperty('--theme-glass-shadow', theme.glass.shadow)
  root.style.setProperty('--theme-text', theme.glass.text)
  
  if (theme.glass.text === '#f5f5f7') {
    root.classList.add('dark-theme')
  } else {
    root.classList.remove('dark-theme')
  }
}

watch(currentTheme, (newTheme) => {
  applyTheme(themes[newTheme])
}, { immediate: true })

export function useTheme() {
  return {
    themes,
    currentTheme,
    setTheme
  }
}