import { makeMessage, type ProtocolMessage, type QueueItem } from "@muf/shared";
import { addSanitizedLog, loadCheckpoint, saveCheckpoint, type QueueCheckpoint } from "./storage";
import { focusInquiryTab, getInquiryTab, waitForReady } from "./tab-controller";

type Broadcast = (message: ProtocolMessage) => void;
let processing = false;
let cancelDelay: (() => void) | undefined;

export async function startQueue(items: QueueItem[], settings: Partial<QueueCheckpoint["settings"]>, broadcast: Broadcast) {
  const existing = await loadCheckpoint();
  const checkpoint: QueueCheckpoint = {
    items,
    cursor: 0,
    state: "running",
    settings: { ...existing.settings, ...settings }
  };
  await saveCheckpoint(checkpoint);
  void processQueue(broadcast);
}

export async function controlQueue(command: "PAUSE" | "RESUME" | "STOP" | "SKIP" | "RETRY", broadcast: Broadcast) {
  const checkpoint = await loadCheckpoint();
  if (command === "PAUSE") checkpoint.state = "paused";
  if (command === "RESUME") checkpoint.state = "running";
  if (command === "STOP") checkpoint.state = "stopped";
  if (command === "SKIP") {
    const item = checkpoint.items[checkpoint.cursor];
    if (item) item.status = "DILEWATI";
    checkpoint.cursor += 1;
  }
  if (command === "RETRY") {
    const item = checkpoint.items[checkpoint.cursor];
    if (item) {
      item.status = "MENUNGGU";
      item.attempts = 0;
    }
    checkpoint.state = "running";
  }
  cancelDelay?.();
  await saveCheckpoint(checkpoint);
  broadcast(makeMessage("QUEUE_PROGRESS", { item: checkpoint.items[checkpoint.cursor], state: checkpoint.state }));
  if (["RESUME", "SKIP", "RETRY"].includes(command)) void processQueue(broadcast);
}

async function processQueue(broadcast: Broadcast) {
  if (processing) return;
  processing = true;
  try {
    let checkpoint = await loadCheckpoint();
    const tab = await getInquiryTab();
    if (!tab.id) throw new Error("Tab Tokopedia tidak tersedia.");
    await waitForReady(tab.id, checkpoint.settings.timeoutMs);
    while (checkpoint.cursor < checkpoint.items.length) {
      checkpoint = await loadCheckpoint();
      if (checkpoint.state !== "running") return;
      const item = checkpoint.items[checkpoint.cursor];
      if (!item) break;
      item.status = "MENGIRIM";
      item.attempts += 1;
      await saveCheckpoint(checkpoint);
      broadcast(makeMessage("QUEUE_PROGRESS", { item, cursor: checkpoint.cursor, total: checkpoint.items.length, state: checkpoint.state }));
      try {
        const response = await sendInquiry(tab.id, item, checkpoint);
        if (response.actionRequired) {
          item.status = response.code === "RATE_LIMIT" ? "RATE_LIMIT" : response.code === "CAPTCHA" ? "CAPTCHA" : "LOGIN_DIPERLUKAN";
          checkpoint.state = "action_required";
          await saveCheckpoint(checkpoint);
          await focusInquiryTab();
          broadcast(makeMessage("QUEUE_PROGRESS", { item, state: checkpoint.state, message: "Tokopedia memerlukan tindakan Anda. Selesaikan login atau verifikasi pada tab Tokopedia, kemudian tekan Lanjutkan." }));
          return;
        }
        if (!response.ok) throw new Error(response.message ?? "Inquiry gagal.");
        item.status = response.result?.errorCode ? "PERLU_VERIFIKASI" : "SELESAI";
        await saveCheckpoint(checkpoint);
        broadcast(makeMessage("QUEUE_RESULT", { item, inquiry: response.result }));
        checkpoint.cursor += 1;
        await saveCheckpoint(checkpoint);
        await delay(checkpoint.settings.delayMs);
      } catch (error) {
        const message = error instanceof Error ? error.message : "Inquiry gagal.";
        await addSanitizedLog("inquiry_error", message);
        if (item.attempts <= checkpoint.settings.maxRetries) {
          item.status = "MENUNGGU";
          await saveCheckpoint(checkpoint);
          await delay(checkpoint.settings.delayMs);
          continue;
        }
        item.status = "GAGAL";
        item.error = message;
        checkpoint.cursor += 1;
        await saveCheckpoint(checkpoint);
        broadcast(makeMessage("QUEUE_PROGRESS", { item, cursor: checkpoint.cursor, total: checkpoint.items.length, state: checkpoint.state, message }));
      }
    }
    checkpoint.state = "idle";
    await saveCheckpoint(checkpoint);
    broadcast(makeMessage("QUEUE_PROGRESS", { done: true, total: checkpoint.items.length, state: "idle" }));
  } finally {
    processing = false;
  }
}

async function sendInquiry(tabId: number, item: QueueItem, checkpoint: QueueCheckpoint): Promise<any> {
  return Promise.race([
    browser.tabs.sendMessage(tabId, {
      type: "RUN_INQUIRY",
      contractNumber: item.contractNumber,
      maskedContract: item.maskedContract,
      timeoutMs: checkpoint.settings.timeoutMs,
      includeAmount: checkpoint.settings.saveAmount
    }),
    new Promise((_, reject) => setTimeout(() => reject(new Error("Hasil inquiry tidak muncul dalam batas waktu.")), checkpoint.settings.timeoutMs + 3000))
  ]);
}

async function delay(ms: number) {
  await new Promise<void>((resolve) => {
    const timer = setTimeout(resolve, Math.max(2000, ms));
    cancelDelay = () => { clearTimeout(timer); resolve(); };
  });
  cancelDelay = undefined;
}
