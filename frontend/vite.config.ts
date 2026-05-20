/// <reference types="vitest" />
import path from 'node:path';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    // Dev-mode parity with the nginx reverse proxy in frontend/Dockerfile —
    // /api/* and WebSocket upgrades are forwarded to the Quarkus backend so the
    // application code can always call the same paths regardless of environment.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      '/admin/ws': { target: 'ws://localhost:8080', ws: true },
      '/users/ws': { target: 'ws://localhost:8080', ws: true },
      '/advisor/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  esbuild: {
    // .claude/rules/frontend_coding.md §18 — strip console logs from production.
    drop: ['console', 'debugger'],
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test-setup.ts', 'src/main.tsx'],
    },
  },
});
