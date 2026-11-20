import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In dev, /api is proxied to the gateway so the cookie is same-origin.
// In production, nginx does the same (see nginx.conf).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": process.env.GATEWAY_URL ?? "http://localhost:8080" },
  },
});
