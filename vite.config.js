import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 本地联调：所有 /api 请求转发到 Agent 编排层后端（默认 8081）
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
});
