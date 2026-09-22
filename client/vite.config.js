import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// API_TARGET lets you point the dev server at a different backend (default: the local Spring Boot API).
const target = process.env.API_TARGET || 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': target,
      // Google sign-in starts and finishes on the API, so proxy those paths too.
      '/oauth2': target,
      '/login': target,
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
