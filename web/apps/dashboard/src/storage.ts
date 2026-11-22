import type { CheckRecord, QueueItem } from "@muf/shared";

const DB_NAME = "muf-inquiry-dashboard";
const DB_VERSION = 1;
const HISTORY = "history";
const QUEUE = "queue";
const SETTINGS_KEY = "muf:settings:v2";

export interface Settings {
  maskSensitive: boolean;
  saveAmount: boolean;
  delayMs: number;
  timeoutMs: number;
  maxRetries: number;
  simulation: boolean;
}

export const defaultSettings: Settings = {
  maskSensitive: true,
  saveAmount: false,
  delayMs: 3000,
  timeoutMs: 30000,
  maxRetries: 1,
  simulation: false
};

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(HISTORY)) db.createObjectStore(HISTORY, { keyPath: "id" });
      if (!db.objectStoreNames.contains(QUEUE)) db.createObjectStore(QUEUE, { keyPath: "id" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function getAll<T>(storeName: string): Promise<T[]> {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const request = db.transaction(storeName, "readonly").objectStore(storeName).getAll();
    request.onsuccess = () => resolve(request.result as T[]);
    request.onerror = () => reject(request.error);
  });
}

async function replaceAll<T>(storeName: string, records: T[]) {
  const db = await openDatabase();
  await new Promise<void>((resolve, reject) => {
    const transaction = db.transaction(storeName, "readwrite");
    const store = transaction.objectStore(storeName);
    store.clear();
    records.forEach((record) => store.put(record));
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
  });
}

export async function loadHistory() {
  const records = await getAll<CheckRecord>(HISTORY);
  return records.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}
export const saveHistory = (records: CheckRecord[]) => replaceAll(HISTORY, records);
export const loadQueue = () => getAll<QueueItem>(QUEUE);
export const saveQueue = (records: QueueItem[]) => replaceAll(QUEUE, records);

export async function clearDatabase() {
  const db = await openDatabase();
  await Promise.all([HISTORY, QUEUE].map((storeName) => new Promise<void>((resolve, reject) => {
    const transaction = db.transaction(storeName, "readwrite");
    transaction.objectStore(storeName).clear();
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
  })));
}

export function loadSettings(): Settings {
  try {
    return { ...defaultSettings, ...JSON.parse(localStorage.getItem(SETTINGS_KEY) ?? "{}"), simulation: false };
  } catch {
    return defaultSettings;
  }
}
export const saveSettings = (settings: Settings) =>
  localStorage.setItem(SETTINGS_KEY, JSON.stringify({ ...settings, simulation: false }));
