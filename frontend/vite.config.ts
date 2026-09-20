import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Pin the dev port so the backend's OAuth2 success redirect
  // (http://localhost:5173/cookie) always lands on this app. Without
  // strictPort, a taken 5173 silently moves to 5174 and the flow breaks.
  server: {
    port: 5173,
    strictPort: true,
  },
})
