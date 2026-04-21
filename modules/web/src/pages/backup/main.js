import { createApp } from 'vue'
import App from '@/App.vue'
import backupRouter from '@/router/backupRouter'
import store from '@/store'
import 'element-plus/theme-chalk/dark/css-vars.css'

createApp(App).use(store).use(backupRouter).mount('#app')

watch(
  () => useBookStore().isNight,
  isNight => {
    if (isNight) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  },
)
