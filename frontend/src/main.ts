import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/theme-chalk/base.css'
import './assets/main.css'
import './assets/repair.css'
import './assets/dispatch-map.css'
import './assets/phase5.css'
import './assets/design-system.css'
import './assets/experience.css'
import App from './App.vue'
import { createAppRouter } from './router'
import { useAuthStore } from './store/auth'
import { TOKEN_KEY } from './utils/session'

const pinia = createPinia()
const router = createAppRouter(undefined, pinia)
createApp(App).use(pinia).use(router).mount('#app')
window.addEventListener('storage', (event) => {
  if (event.storageArea === localStorage && (event.key === TOKEN_KEY || event.key === null)) {
    useAuthStore(pinia).syncFromStorage()
    void router.replace('/login')
  }
})
