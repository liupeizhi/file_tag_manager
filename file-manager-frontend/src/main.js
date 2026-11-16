import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './assets/styles/main.css'
import { useThemeStore } from './store/theme'

const app = createApp(App)
const pinia = createPinia()

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 初始化主题(本地偏好, 保证首屏无闪烁)
const themeStore = useThemeStore(pinia)
themeStore.init()
// 服务端主题在确认登录后由 userStore 拉取, 避免未鉴权时发起 403 请求

app.mount('#app')