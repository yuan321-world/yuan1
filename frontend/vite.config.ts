import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const apiTargetValue = process.env.E2E_API_TARGET ?? 'http://127.0.0.1:8080'
const apiTargetUrl = new URL(apiTargetValue)
if (apiTargetUrl.protocol !== 'http:' || !['127.0.0.1', 'localhost', '[::1]', '::1'].includes(apiTargetUrl.hostname)
  || apiTargetUrl.pathname !== '/' || apiTargetUrl.search || apiTargetUrl.hash || apiTargetUrl.username || apiTargetUrl.password) {
  throw new Error('E2E_API_TARGET must point to a local HTTP loopback host')
}
const apiTarget = apiTargetUrl.toString().replace(/\/$/, '')

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy: { '/api': { target: apiTarget, changeOrigin: false } } },
  preview: { host: '127.0.0.1', port: 4173, strictPort: true, proxy: { '/api': { target: apiTarget, changeOrigin: false } } },
})

