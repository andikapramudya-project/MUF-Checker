export const PROTOCOL_VERSION = "2.0.0";
export const BRIDGE_CHANNEL = "muf-inquiry-bridge";
export const CHANNEL = "Tokopedia" as const;

export type AnalysisStatus =
  | "TERINDIKASI_SUDAH_BAYAR"
  | "BELUM_DAPAT_DIPASTIKAN"
  | "TERINDIKASI_BELUM_DIBUKUKAN"
  | "PERLU_VERIFIKASI"
  | "KONTRAK_TIDAK_DITEMUKAN"
  | "HASIL_TIDAK_TERBACA"
  | "LAYANAN_BERMASALAH"
  | "STRUKTUR_HALAMAN_BERUBAH";

export type QueueStatus =
  | "MENUNGGU"
  | "MEMPERSIAPKAN"
  | "LOGIN_DIPERLUKAN"
  | "MENGISI"
  | "MENGIRIM"
  | "MENUNGGU_HASIL"
  | "MEMBACA"
  | "MENGANALISIS"
  | "SELESAI"
  | "PERLU_VERIFIKASI"
  | "CAPTCHA"
  | "RATE_LIMIT"
  | "GAGAL"
  | "DILEWATI"
  | "DIHENTIKAN";

export interface Period {
  year: number;
  month: number;
}

export interface InquiryPayload {
  maskedContract: string;
  maskedConsumerName?: string;
  dueDate?: string;
  installmentCurrent?: number;
  installmentTotal?: number;
  amount?: number;
  inquiryAt: string;
  source: typeof CHANNEL;
  errorCode?: string;
  errorMessage?: string;
  simulation: boolean;
}

export interface AnalysisResult {
  status: AnalysisStatus;
  label: string;
  reason: string;
  warning?: string;
  monthDifference?: number;
}

export interface CheckRecord {
  id: string;
  maskedContract: string;
  contractNumber?: string;
  period: Period;
  inquiry: InquiryPayload;
  analysis: AnalysisResult;
  queueStatus: QueueStatus;
  createdAt: string;
}

export interface QueueItem {
  id: string;
  contractNumber: string;
  maskedContract: string;
  period: Period;
  status: QueueStatus;
  attempts: number;
  result?: CheckRecord;
  error?: string;
}

export type MessageType =
  | "HANDSHAKE"
  | "HANDSHAKE_ACK"
  | "START_QUEUE"
  | "QUEUE_PROGRESS"
  | "QUEUE_RESULT"
  | "PAUSE"
  | "RESUME"
  | "STOP"
  | "SKIP"
  | "RETRY"
  | "OPEN_TOKOPEDIA"
  | "GET_STATUS"
  | "STATUS";

export interface ProtocolMessage<T = unknown> {
  protocolVersion: typeof PROTOCOL_VERSION;
  messageType: MessageType;
  requestId: string;
  timestamp: string;
  payload: T;
}

export interface BridgeEnvelope {
  channel: typeof BRIDGE_CHANNEL;
  direction: "TO_ADDON" | "FROM_ADDON";
  message: ProtocolMessage;
}

export function makeMessage<T>(messageType: MessageType, payload: T, requestId: string = crypto.randomUUID()): ProtocolMessage<T> {
  return { protocolVersion: PROTOCOL_VERSION, messageType, requestId, timestamp: new Date().toISOString(), payload };
}

export function isProtocolMessage(value: unknown): value is ProtocolMessage {
  if (!value || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;
  return item.protocolVersion === PROTOCOL_VERSION
    && typeof item.messageType === "string"
    && typeof item.requestId === "string"
    && typeof item.timestamp === "string"
    && "payload" in item;
}

export function isBridgeEnvelope(value: unknown, direction: BridgeEnvelope["direction"]): value is BridgeEnvelope {
  if (!value || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;
  return item.channel === BRIDGE_CHANNEL && item.direction === direction && isProtocolMessage(item.message);
}

export function normalizeContract(value: string): string {
  return value.trim().replace(/[._-]+/g, "");
}

export function isPlausibleContract(value: string): boolean {
  return /^\d{6,24}$/.test(normalizeContract(value));
}

export function maskContract(value: string): string {
  const normalized = normalizeContract(value);
  if (normalized.length <= 4) return "*".repeat(normalized.length);
  const visible = Math.min(8, Math.max(2, normalized.length - 4));
  return `${normalized.slice(0, visible)}${"*".repeat(normalized.length - visible)}`;
}

export function parseContractList(raw: string) {
  const seen = new Set<string>();
  const valid: string[] = [];
  const duplicates: string[] = [];
  const invalid: string[] = [];
  const blanks = raw.split(/\r?\n/).filter((line) => !line.trim()).length;
  for (const part of raw.split(/[\s,;]+/)) {
    const value = normalizeContract(part);
    if (!value) continue;
    if (!isPlausibleContract(value)) invalid.push(part.trim());
    else if (seen.has(value)) duplicates.push(value);
    else {
      seen.add(value);
      valid.push(value);
    }
  }
  return { valid, duplicates, invalid, blanks };
}

export function formatPeriod(period: Period): string {
  return new Intl.DateTimeFormat("id-ID", { month: "long", year: "numeric", timeZone: "Asia/Makassar" })
    .format(new Date(Date.UTC(period.year, period.month - 1, 1)));
}

export function formatWita(value: string | Date): string {
  const date = typeof value === "string" ? new Date(value) : value;
  const formatted = new Intl.DateTimeFormat("id-ID", {
    day: "numeric", month: "long", year: "numeric", hour: "2-digit", minute: "2-digit",
    hour12: false, timeZone: "Asia/Makassar"
  }).format(date).replace(" pukul ", ", ").replace(":", ".");
  return `${formatted} WITA`;
}
