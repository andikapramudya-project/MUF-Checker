import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["icon.svg"],
      manifest: {
        name: "Cek Status Angsuran MUF",
        short_name: "Cek Angsuran",
        description: "Pemeriksaan indikasi pembayaran berdasarkan jatuh tempo pada kanal inquiry.",
        theme_color: "#004A8F",
        background_color: "#F5F7FA",
        display: "standalone",
        icons: [{ src: "icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any maskable" }]
      },
      workbox: {
        navigateFallback: "index.html",
        runtimeCaching: []
      }
    })
  ]
});
