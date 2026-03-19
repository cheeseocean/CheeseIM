import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/auth': {
        target: 'http://localhost:18083',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/auth/, '/api/auth'),
      },
      '/api/im': {
        target: 'http://localhost:18083',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/im/, '/api/im'),
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: []
  }
});
