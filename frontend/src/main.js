import { createApp } from 'vue'
import 'element-plus/dist/index.css'
import './assets/styles.css'
import App from './App.vue'
import { pinia } from './store/index.js'
import router from './router/index.js'

createApp(App).use(pinia).use(router).mount('#app')
