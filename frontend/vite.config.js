import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxy /api to the Spring Boot backend so the SPA and API share an origin in dev.
//
// Port and backend are overridable, so a second dev instance can run alongside the first --
// one per section, each against its own database. The defaults are the single-instance values.
//
//   npm run dev
//   ORAR_PORT=5281 ORAR_API=http://localhost:8081 npm run dev
export default defineConfig({
  plugins: [react()],
  server: {
    // 5173 is often taken by the legacy Laravel app's Vite; use a distinct port.
    port: Number(process.env.ORAR_PORT) || 5280,
    strictPort: false,
    proxy: {
      '/api': process.env.ORAR_API || 'http://localhost:8080',
    },
  },
});
