import type { QueueItem } from "@muf/shared";

export interface QueueCheckpoint {
  items: QueueItem[];
  cursor: number;
  state: "idle" | "running" | "paused" | "stopped" | "action_required";
  settings: {
    delayMs: number;
    timeoutMs: number;
    maxRetries: number;
    saveAmount: boolean;
  };
}

export const defaultCheckpoint: QueueCheckpoint = {
  items: [],
  cursor: 0,
  state: "idle",
  settings: { delayMs: 3000, timeoutMs: 30000, maxRetries: 1, saveAmount: false }
};

export async function loadCheckpoint(): Promise<QueueCheckpoint> {
  const result = await browser.storage.local.get("queueCheckpoint");
  return { ...defaultCheckpoint, ...(result.queueCheckpoint as Partial<QueueCheckpoint> | undefined) };
}

export async function saveCheckpoint(value: QueueCheckpoint) {
  await browser.storage.local.set({ queueCheckpoint: value });
}

export async function addSanitizedLog(event: string, detail?: string) {
  const result = await browser.storage.local.get("diagnosticLogs");
  const logs = Array.isArray(result.diagnosticLogs) ? result.diagnosticLogs as unknown[] : [];
  logs.push({ at: new Date().toISOString(), event, detail: detail?.slice(0, 160) });
  await browser.storage.local.set({ diagnosticLogs: logs.slice(-100) });
}
