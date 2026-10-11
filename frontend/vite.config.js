import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  css: {
    postcss: {
      plugins: [{
        postcssPlugin: 'simrecrut-font-family',
        Declaration: {
          // Element Plus includes a localized font name in its default CSS.
          '--el-font-family': (declaration) => {
            declaration.value = '"Segoe UI", Arial, sans-serif'
          },
        },
      }],
    },
  },
})
