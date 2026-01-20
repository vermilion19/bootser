import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3301,
    proxy: {
      // '/restaurants'로 시작하는 요청이 오면 'http://localhost:6000'으로 토스한다.
      '/restaurants': {
        target: 'http://localhost:6000',
        changeOrigin: true,
        secure: false,
      },
      '/coin/v1': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        secure: false,
      }
    },
  },
})
