import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const repositoryRoot = fileURLToPath(new URL('../', import.meta.url))

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  // Preserve the shared RuoYi API imports and local backend proxy.
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    proxy: {
      '/dev-api': {
        target: process.env.VITE_BACKEND_TARGET || `http://127.0.0.1:${loadEnv(mode, repositoryRoot, 'JAVA_PORT').JAVA_PORT || '8081'}`,
        changeOrigin: true,
        rewrite: path => path.replace(/^\/dev-api/, ''),
      },
    },
  },
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
}))
