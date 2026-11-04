import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import en from 'element-plus/es/locale/lang/en'
import directive from './directive/index.js'
import 'element-plus/dist/index.css'
import './assets/styles.css'
import App from './App.vue'
import { pinia } from './store/index.js'
import router from './router/index.js'

// Share the page design while registering RuoYi forms and permission directives.
const app = createApp(App).use(pinia).use(router).use(ElementPlus, { locale: en })
directive(app)
app.mount('#app')
