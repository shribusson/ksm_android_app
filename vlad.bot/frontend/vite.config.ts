import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://backend:8000',
      '/upload': 'http://backend:8000',
      '/static': 'http://backend:8000',
    },
    host: true, // needed for docker
  }
})
