import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Long-lived vendor libraries get their own chunks, so an app-only deploy doesn't
// invalidate them in browser caches and no single chunk crosses Vite's size warning.
const VENDOR_CHUNKS: Record<string, string[]> = {
  react: ['react', 'react-dom', 'react-router', 'react-router-dom', 'scheduler'],
  motion: ['framer-motion', 'motion-dom', 'motion-utils'],
  query: ['@tanstack/react-query', '@tanstack/query-core', 'axios'],
}

function vendorChunk(id: string): string | undefined {
  const match = id.match(/node_modules\/((?:@[^/]+\/)?[^/]+)\//)
  if (!match) return undefined
  return Object.keys(VENDOR_CHUNKS).find((chunk) => VENDOR_CHUNKS[chunk].includes(match[1]))
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: vendorChunk,
      },
    },
  },
})
