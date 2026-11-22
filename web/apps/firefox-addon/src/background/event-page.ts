import { isAllowedOrigin } from "@muf/inquiry-core";
import { isProtocolMessage, makeMessage, PROTOCOL_VERSION, type ProtocolMessage, type QueueItem } from "@muf/shared";
import { controlQueue, startQueue } from "./queue-controller";
import { loadCheckpoint } from "./storage";
import { focusInquiryTab, INQUIRY_URL } from "./tab-controller";

const dashboardPorts = new Set<browser.runtime.Port>();

function dashboardOrigins() {
  const scripts = browser.runtime.getManifest().content_scripts ?? [];
  const bridge = scripts.find((script) => script.js?.includes("dashboard-bridge/dashboard-bridge.js"));
  return bridge?.matches ?? [];
}

function senderAllowed(port: browser.runtime.Port) {
  const url = port.sender?.url;
  return Boolean(url && isAllowedOrigin(url, dashboardOrigins()));
}

function broadcast(message: ProtocolMessage) {
  for (const port of dashboardPorts) {
    try { port.postMessage(message); } catch { dashboardPorts.delete(port); }
  }
}

async function handle(message: unknown) {
  if (!isProtocolMessage(message)) return makeMessage("STATUS", { ok: false, error: "Format pesan tidak valid." });
  if (message.protocolVersion !== PROTOCOL_VERSION) return makeMessage("STATUS", { ok: false, error: "Versi protokol tidak kompatibel." }, message.requestId);
  if (message.messageType === "HANDSHAKE") return makeMessage("HANDSHAKE_ACK", { ok: true, addonVersion: browser.runtime.getManifest().version }, message.requestId);
  if (message.messageType === "GET_STATUS") return makeMessage("STATUS", await loadCheckpoint(), message.requestId);
  if (message.messageType === "OPEN_TOKOPEDIA") {
    await focusInquiryTab();
    return makeMessage("STATUS", { ok: true }, message.requestId);
  }
  if (message.messageType === "START_QUEUE") {
    const payload = message.payload as { items?: QueueItem[]; settings?: Record<string, unknown> };
    if (!Array.isArray(payload.items)) return makeMessage("STATUS", { ok: false, error: "Antrean tidak valid." }, message.requestId);
    await startQueue(payload.items, payload.settings ?? {}, broadcast);
    return makeMessage("STATUS", { accepted: true }, message.requestId);
  }
  if (["PAUSE", "RESUME", "STOP", "SKIP", "RETRY"].includes(message.messageType)) {
    await controlQueue(message.messageType as "PAUSE" | "RESUME" | "STOP" | "SKIP" | "RETRY", broadcast);
    return makeMessage("STATUS", { ok: true }, message.requestId);
  }
  return makeMessage("STATUS", { ok: false, error: "Perintah tidak dikenali." }, message.requestId);
}

browser.runtime.onConnect.addListener((port) => {
  if (port.name !== "muf-dashboard" || !senderAllowed(port)) {
    port.disconnect();
    return;
  }
  dashboardPorts.add(port);
  port.onDisconnect.addListener(() => dashboardPorts.delete(port));
  port.onMessage.addListener((message) => void handle(message).then((response) => port.postMessage(response)));
  void loadCheckpoint().then((checkpoint) => port.postMessage(makeMessage("STATUS", checkpoint)));
});

browser.runtime.onMessage.addListener(async (message) => {
  if (message?.type === "GET_ADDON_STATUS") return loadCheckpoint();
  if (message?.type === "OPEN_TOKOPEDIA") {
    await focusInquiryTab();
    return { ok: true };
  }
  if (message?.type === "OPEN_DIAGNOSTICS") {
    await browser.tabs.create({ url: browser.runtime.getURL("diagnostics.html") });
    return { ok: true };
  }
  if (message?.type === "INQUIRY_URL") return { url: INQUIRY_URL };
  return undefined;
});
