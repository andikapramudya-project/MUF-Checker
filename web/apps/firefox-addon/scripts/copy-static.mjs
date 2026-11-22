import { cp, mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const dist = resolve(root, "dist");
await mkdir(dist, { recursive: true });
await cp(resolve(root, "public"), dist, { recursive: true, force: true });

const manifest = JSON.parse(await readFile(resolve(root, "manifest.template.json"), "utf8"));
const configured = (process.env.DASHBOARD_ORIGINS ?? "http://localhost:5173/*,http://127.0.0.1:5173/*")
  .split(",").map((value) => value.trim()).filter(Boolean);
const origins = configured.map((value) => {
  const normalized = value.endsWith("/*") ? value : `${value.replace(/\/$/, "")}/*`;
  const check = new URL(normalized.replace(/\/\*$/, "/"));
  if (!["http:", "https:"].includes(check.protocol) || check.hostname.includes("*")) {
    throw new Error(`Origin dashboard tidak aman: ${value}`);
  }
  return normalized;
});
manifest.permissions.push(...origins);
manifest.content_scripts.push({
  matches: origins,
  js: ["dashboard-bridge/dashboard-bridge.js"],
  run_at: "document_start"
});
await writeFile(resolve(dist, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
