/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// One config for dev server, production build and Vitest (Vitest reads the `test` block).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Same-origin in dev too: the browser talks to Vite, Vite forwards /api to Spring Boot.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    // Spring Boot serves the SPA from the jar's classpath:/static/.
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    exclude: ['node_modules/**', 'e2e/**', '**/blueprints/**'],
    css: false,
  },
});
