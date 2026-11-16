<template>
  <el-tooltip :content="tooltipText" placement="bottom">
    <el-button circle class="theme-button icon-button" @click="cycle">
      <el-icon :size="16">
        <Sunny v-if="themeStore.isDark" />
        <Moon v-else />
      </el-icon>
    </el-button>
  </el-tooltip>
</template>

<script setup>
import { computed } from 'vue'
import { useThemeStore } from '@/store/theme'
import { Sunny, Moon } from '@element-plus/icons-vue'

const themeStore = useThemeStore()

const tooltipText = computed(() => {
  if (themeStore.brightness === 'system') return '跟随系统 (点击切换到深色)'
  return themeStore.isDark ? '切换到浅色模式' : '切换到深色模式'
})

function cycle() {
  if (themeStore.brightness === 'system') {
    themeStore.setBrightness('dark')
  } else if (themeStore.isDark) {
    themeStore.setBrightness('light')
  } else {
    themeStore.setBrightness('system')
  }
}
</script>

<style scoped>
.icon-button {
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-secondary);
}

.icon-button:hover {
  border-color: var(--border-color);
  background: var(--bg-secondary);
  color: var(--text-primary);
}
</style>
