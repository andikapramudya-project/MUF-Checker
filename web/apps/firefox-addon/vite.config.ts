import { resolve } from "node:path";
import { defineConfig } from "vite";

const entries = {
  background: { source: "src/background/event-page.ts", output: "background/event-page.js" },
  tokopedia: { source: "src/content/tokopedia-content.ts", output: "content/tokopedia-content.js" },
  bridge: { source: "src/dashboard-bridge/dashboard-bridge.ts", output: "dashboard-bridge/dashboard-bridge.js" },
  diagnostics: { source: "src/diagnostics/diagnostics.ts", output: "diagnostics/diagnostics.js" },
  popup: { source: "src/popup/popup.ts", output: "popup/popup.js" }
} as const;

export default defineConfig(({ mode }) => {
  const selected = entries[mode as keyof typeof entries];
  if (!selected) throw new Error(`Mode build add-on tidak dikenali: ${mode}`);
  return {
    publicDir: false,
    build: {
      outDir: "dist",
      emptyOutDir: mode === "background",
      target: "firefox120",
      sourcemap: false,
      minify: false,
      lib: {
        entry: resolve(selected.source),
        name: `MufAddon${mode}`,
        formats: ["iife"],
        fileName: () => selected.output
      },
      rollupOptions: {
        output: { inlineDynamicImports: true }
      }
    }
  };
});
