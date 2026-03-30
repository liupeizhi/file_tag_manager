# 主题切换功能设计文档

## 概述

为文件管理系统添加皮肤切换功能，支持多种颜色主题，确保整体UI协调美观。

## 需求

- 提供预设主题套装（浅色4套 + 深色4套）
- 支持自定义主色调
- 深色/浅色作为独立主题
- 主题切换入口在顶部Header
- 实时预览效果
- 持久化保存用户选择

## 技术方案

### 架构设计

采用 **CSS变量 + Pinia Store** 方案，与现有CSS变量系统无缝集成。

### 主题配置

```typescript
interface ThemeColor {
  primary: string       // 主色调，用于按钮、链接等
  secondary: string     // 辅助色，用于次要元素
  gradient: string      // 渐变色，用于背景（CSS linear-gradient格式）
  text: string          // 主要文本色
  glassBg: string       // 玻璃效果背景色（rgba格式）
  glassBorder: string   // 玻璃边框色（rgba格式）
  glassShadow: string   // 玻璃阴影色（rgba格式）
}

interface Theme {
  id: string
  name: string
  type: 'light' | 'dark'
  colors: ThemeColor
  icon?: string         // 主题图标emoji
  isCustom?: boolean    // 是否自定义主题
}
```

### 预设主题

**浅色主题：**

| ID | 名称 | 主色调 | 渐变色 | 描述 |
|----|------|--------|--------|------|
| ocean-light | 🌊 海洋蓝 | #007AFF | #667eea → #764ba2 | 蓝色系，清新专业 |
| forest-light | 🌲 森林绿 | #34C759 | #11998e → #38ef7d | 绿色系，自然清新 |
| sakura-light | 🌸 樱花粉 | #FF6B9D | #f093fb → #f5576c | 粉色系，温柔浪漫 |
| sunset-light | ☀️ 日落橙 | #FF9500 | #FA8BFF → #2BD2FF | 橙色系，温暖活力 |

**深色主题：**

| ID | 名称 | 主色调 | 渐变色 | 描述 |
|----|------|--------|--------|------|
| ocean-dark | 🌌 深海蓝 | #0A84FF | #1a1a2e → #16213e | 深蓝色，科技感 |
| midnight-dark | 🌃 午夜紫 | #BF5AF2 | #2d1b69 → #11998e | 深紫色，神秘优雅 |
| night-dark | 🌑 暗夜黑 | #8E8E93 | #1c1c1e → #2c2c2e | 纯黑色，极简专业 |
| cyber-dark | 🎮 电竞青 | #64D2FF | #0f0c29 → #302b63 | 青色系，赛博朋克 |

### 自定义主题

用户可以：
1. 选择主色调（颜色选择器）
2. 系统自动生成协调的渐变色和其他辅助色
3. 保存为自定义主题（最多保存3个）

**自动生成规则：**
- secondary: 主色调的60%亮度版本
- gradient: 主色调 → 互补色渐变
- glassBg: 浅色模式 rgba(255,255,255,0.72)，深色模式 rgba(30,30,30,0.82)
- glassBorder: rgba格式的主色调20%透明度
- glassShadow: rgba格式的主色调10%透明度

## 组件设计

### 1. ThemeStore (Pinia)

**文件位置：** `src/store/theme.js`

**State:**
```javascript
{
  currentTheme: Theme,           // 当前主题
  customThemes: Theme[],         // 自定义主题列表（最多3个）
  showThemePanel: boolean        // 主题面板显示状态
}
```

**Actions:**
```javascript
- setTheme(themeId)              // 设置主题
- setCustomTheme(color, type)    // 设置自定义主题
- addCustomTheme(theme)          // 添加自定义主题
- removeCustomTheme(themeId)     // 删除自定义主题
- applyTheme(theme)              // 应用主题（更新CSS变量）
- loadTheme()                    // 从localStorage加载主题
- saveTheme()                    // 保存主题到localStorage
```

**Getters:**
```javascript
- allThemes                      // 所有可用主题（预设+自定义）
- themeType                      // 当前主题类型（light/dark）
```

### 2. ThemeSelector 组件

**文件位置：** `src/components/theme/ThemeSelector.vue`

**结构：**
```
┌─────────────────────────────────┐
│  主题设置                    [×] │
├─────────────────────────────────┤
│  浅色主题                        │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐│
│  │ 🌊  │ │ 🌲  │ │ 🌸  │ │ ☀️  ││
│  │海洋蓝│ │森林绿│ │樱花粉│ │日落橙││
│  └─────┘ └─────┘ └─────┘ └─────┘│
│                                 │
│  深色主题                        │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐│
│  │ 🌌  │ │ 🌃  │ │ 🌑  │ │ 🎮  ││
│  │深海蓝│ │午夜紫│ │暗夜黑│ │电竞青││
│  └─────┘ └─────┘ └─────┘ └─────┘│
│                                 │
│  自定义主题                      │
│  ┌─────────────────────────────┐│
│  │ 🎨 主色调: [颜色选择器]     ││
│  │ ○ 浅色  ● 深色              ││
│  │ [应用] [保存]               ││
│  └─────────────────────────────┘│
└─────────────────────────────────┘
```

**Props:** 无

**Events:**
- `close` - 关闭面板

### 3. Header 集成

**文件位置：** `src/components/layout/AppHeader.vue`

在Header右侧添加主题按钮：
```html
<el-button circle @click="toggleThemePanel">
  <el-icon><Brush /></el-icon>
</el-button>

<ThemeSelector v-show="showThemePanel" @close="showThemePanel = false" />
```

## 样式实现

### CSS变量动态更新

**更新方法：**
```javascript
function applyTheme(theme) {
  const root = document.documentElement
  const { colors } = theme
  
  root.style.setProperty('--theme-primary', colors.primary)
  root.style.setProperty('--theme-secondary', colors.secondary)
  root.style.setProperty('--theme-gradient', colors.gradient)
  root.style.setProperty('--theme-text', colors.text)
  root.style.setProperty('--theme-glass-bg', colors.glassBg)
  root.style.setProperty('--theme-glass-border', colors.glassBorder)
  root.style.setProperty('--theme-glass-shadow', colors.glassShadow)
}
```

### 深色模式特殊处理

深色主题需要额外调整Element Plus组件样式：
```css
.dark-theme .el-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(255,255,255,0.05);
}

.dark-theme .el-input__wrapper {
  background: rgba(255,255,255,0.1) !important;
}
```

## 数据持久化

**存储键：** `file-manager-theme`

**存储内容：**
```json
{
  "currentThemeId": "ocean-light",
  "customThemes": [
    {
      "id": "custom-1",
      "name": "自定义主题1",
      "type": "dark",
      "colors": { ... },
      "isCustom": true
    }
  ]
}
```

## 实现步骤

1. 创建 ThemeStore
2. 创建预设主题配置
3. 创建 ThemeSelector 组件
4. 更新 main.css 支持动态变量
5. 在 AppHeader 集成主题按钮
6. 实现自定义主题功能
7. 测试所有主题切换

## 注意事项

- 主题切换时确保所有组件颜色协调
- 测试主题在所有页面的显示效果
- 验证自定义主题的颜色对比度是否符合可访问性标准
- 确保主题切换动画流畅（0.3s transition）