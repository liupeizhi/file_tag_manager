<template>
  <el-tooltip :content="themeStore.isDark ? '切换到浅色模式' : '切换到深色模式'" placement="bottom">
    <el-button circle class="theme-button" @click="themeStore.toggleTheme">
      <el-icon :size="18">
        <Sunny v-if="themeStore.isDark" />
        <Moon v-else />
      </el-icon>
    </el-button>
  </el-tooltip>
</template>

<script setup>
import { useThemeStore } from '@/store/theme'
import { Sunny, Moon } from '@element-plus/icons-vue'

const themeStore = useThemeStore()
</script>

<style scoped>
.theme-button {
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface);
  color: var(--theme-text);
}

.theme-button:hover {
  border-color: var(--theme-primary);
  color: var(--theme-primary);
}
</style>