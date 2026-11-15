import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    css: false,
    // Tests must not depend on a developer's local .env; mock-auth tests opt in explicitly.
    env: {
      VITE_USE_MOCK_AUTH: 'false',
      VITE_USE_MOCK_JOBS: 'false',
      VITE_USE_MOCK_APPLICATIONS: 'false',
    },
  },
});
