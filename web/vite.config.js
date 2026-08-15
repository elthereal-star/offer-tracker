import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    // 构建产物直接打进 Spring Boot 的静态资源目录，实现单 jar 部署
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalizedId = id.replaceAll('\\', '/')
          if (normalizedId.includes('/node_modules/vue/') || normalizedId.includes('/node_modules/@vue/')) {
            return 'vue-vendor'
          }
          if (normalizedId.includes('/node_modules/axios/')) return 'http-vendor'
          if (
            normalizedId.includes('/node_modules/vue-draggable-plus/') ||
            normalizedId.includes('/node_modules/sortablejs/')
          ) {
            return 'drag-vendor'
          }
        }
      }
    }
  }
})
