import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// API 프록시 대상 (Docker: gateway-service:6000, Local: localhost:6000)
const apiTarget = process.env.VITE_API_URL || 'http://localhost:6000'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true, // 외부 접근 허용 (Docker 환경)
    proxy: {
      '/api/v1': {
        target: apiTarget,
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
