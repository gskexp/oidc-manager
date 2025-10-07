import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 8090,
    proxy: {
      "/api": {
        target: "http://localhost:3001",
        changeOrigin: true
      },
      "/redirect-back-endpoint": {
        target: "http://localhost:3001",
        changeOrigin: true
      }
    }
  }
});