import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ command }) => ({
  plugins: [react()],
  ...(command === 'serve' && {
    resolve: {
      alias: {
        '/src/main.tsx': '/src/main.local.tsx',
      },
    },
  }),
  build: {
    rollupOptions: {
      output: {
        entryFileNames: 'js/[name]-[hash].js',
        chunkFileNames: 'js/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
  },
}));
