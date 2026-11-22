import { BRIDGE_CHANNEL, isBridgeEnvelope, isProtocolMessage } from "@muf/shared";

const pageOrigin = window.location.origin;
let port = connect();

function connect() {
  const next = browser.runtime.connect({ name: "muf-dashboard" });
  next.onMessage.addListener((message) => {
    if (!isProtocolMessage(message)) return;
    window.postMessage({ channel: BRIDGE_CHANNEL, direction: "FROM_ADDON", message }, pageOrigin);
  });
  next.onDisconnect.addListener(() => {
    window.setTimeout(() => { port = connect(); }, 1000);
  });
  return next;
}

window.addEventListener("message", (event) => {
  if (event.source !== window || event.origin !== pageOrigin) return;
  if (!isBridgeEnvelope(event.data, "TO_ADDON")) return;
  port.postMessage(event.data.message);
});
