import {
  BRIDGE_CHANNEL, isBridgeEnvelope, makeMessage,
  type MessageType, type ProtocolMessage, type QueueItem
} from "@muf/shared";

type Listener = (message: ProtocolMessage) => void;
const listeners = new Set<Listener>();
const pending = new Map<string, { resolve: (payload: unknown) => void; reject: (error: Error) => void; timer: number }>();
let initialized = false;

function initialize() {
  if (initialized) return;
  initialized = true;
  window.addEventListener("message", (event) => {
    if (event.source !== window || event.origin !== window.location.origin) return;
    if (!isBridgeEnvelope(event.data, "FROM_ADDON")) return;
    const message = event.data.message;
    const waiting = pending.get(message.requestId);
    if (waiting) {
      window.clearTimeout(waiting.timer);
      pending.delete(message.requestId);
      waiting.resolve(message.payload);
    }
    listeners.forEach((listener) => listener(message));
  });
}

export function sendToAddon(message: ProtocolMessage, timeoutMs = 2500): Promise<unknown> {
  initialize();
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => {
      pending.delete(message.requestId);
      reject(new Error("Add-on pemeriksaan belum terpasang atau belum tersambung."));
    }, timeoutMs);
    pending.set(message.requestId, { resolve, reject, timer });
    window.postMessage({ channel: BRIDGE_CHANNEL, direction: "TO_ADDON", message }, window.location.origin);
  });
}

export function subscribeAddon(listener: Listener) {
  initialize();
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export const handshake = () => sendToAddon(makeMessage("HANDSHAKE", { dashboardVersion: "2.0.0" }));
export const startAddonQueue = (items: QueueItem[], settings: Record<string, unknown>) =>
  sendToAddon(makeMessage("START_QUEUE", { items, settings }), 5000);
export const controlAddon = (messageType: Extract<MessageType, "PAUSE" | "RESUME" | "STOP" | "SKIP" | "RETRY" | "OPEN_TOKOPEDIA">) =>
  sendToAddon(makeMessage(messageType, {}), 5000);
