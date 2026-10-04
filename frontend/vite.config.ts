/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import type { Plugin } from "vite";

// Adds <meta name="google-site-verification"> when the token is set at build
// time, so the site can be verified in Google Search Console.
function siteVerification(): Plugin {
  const token = process.env.VITE_GOOGLE_SITE_VERIFICATION?.trim();
  return {
    name: "google-site-verification",
    transformIndexHtml: () =>
      token ? [{ tag: "meta", attrs: { name: "google-site-verification", content: token }, injectTo: "head" }] : [],
  };
}

// In development the API runs on :8080 and is proxied under /api so the
// session cookie is same-origin.
export default defineConfig({
  plugins: [react(), tailwindcss(), siteVerification()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.VITE_API_PROXY ?? "http://localhost:8080",
        changeOrigin: false,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          react: ["react", "react-dom", "react-router-dom"],
          charts: ["recharts"],
        },
      },
    },
  },
  test: {
    environment: "node",
  },
});
