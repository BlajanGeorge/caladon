import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

// The procedural placeholder sprites live in ../web/assets so that web/assets/preview.html keeps
// working standalone; the SPA imports them through the @assets alias.
const assets = fileURLToPath(new URL('../web/assets', import.meta.url))

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@assets': assets } },
  server: {
    port: 5173,
    fs: { allow: ['.', assets] },
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
  test: { environment: 'node' },
})
