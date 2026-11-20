import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'CREATORHUB_')
  const proxy = {
    '/api': {
      target: process.env.CREATORHUB_API_TARGET || env.CREATORHUB_API_TARGET || 'http://127.0.0.1:8080',
      changeOrigin: true,
    },
  }
  return {
    plugins: [vue()],
    server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy },
    preview: { host: '127.0.0.1', strictPort: true, proxy },
  }
})
