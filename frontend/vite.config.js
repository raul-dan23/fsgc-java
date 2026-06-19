import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxy /api to the Spring Boot backend so the SPA and API share an origin in dev.
export default defineConfig({
  plugins: [react()],
  server: {
    // 5173 is often taken by the legacy Laravel app's Vite; use a distinct port.
    port: 5280,
    strictPort: false,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
