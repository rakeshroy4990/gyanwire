import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const uiPort = Number(env.UI_PORT || 5180);
  const apiPort = Number(env.PORT || 3010);

  return {
    root: 'client',
    // Load VITE_* from project-root .env (not client/.env).
    envDir: process.cwd(),
    publicDir: 'public',
    plugins: [vue()],
    server: {
      port: uiPort,
      strictPort: true,
      proxy: {
        '/api': {
          target: `http://localhost:${apiPort}`,
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: '../dist',
      emptyOutDir: true,
    },
  };
});
