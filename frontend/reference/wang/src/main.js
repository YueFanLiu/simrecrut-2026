// Install the RuoYi frontend services before mounting the application.
import { createApp } from "vue";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import store from "./store";
import router from "./router";
import directive from "./directive";
import "./style.css";
const app = createApp(App);
app.use(store).use(router).use(ElementPlus);
directive(app);
app.mount("#app");
