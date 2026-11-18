import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import { installI18n } from './i18n/index.js'
import directive from './directive/index.js'
import 'element-plus/dist/index.css'
import './assets/styles.css'
import App from './App.vue'
import { pinia } from './store/index.js'
import router from './router/index.js'

// Share the page design while registering RuoYi forms and permission directives.
const app = createApp(App).use(pinia).use(router).use(ElementPlus)
installI18n(app, router)
directive(app)
app.mount('#app')
