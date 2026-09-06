import react from '@vitejs/plugin-react';
import {defineConfig} from 'vite';

// Served from /admin by the api server, so it is developed under that path too, with /api
// proxied to a running server. Declared rather than pulling in @types/node for one line.
declare const process: {env: Record<string, string | undefined>};
const backend = process.env.BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  // Must match ADMIN_BASE_PATH in src/navigation/navigationItems.tsx.
  base: '/admin',
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    open: '/admin/',
    proxy: {
      '/api': {target: backend, changeOrigin: true},
    },
  },
});
