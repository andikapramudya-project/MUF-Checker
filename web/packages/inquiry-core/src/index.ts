import type { AnalysisResult, Period, QueueItem } from "@muf/shared";

const months: Record<string, number> = {
  jan: 1, januari: 1, feb: 2, februari: 2, mar: 3, maret: 3, apr: 4, april: 4,
  mei: 5, jun: 6, juni: 6, jul: 7, juli: 7, agu: 8, agt: 8, agustus: 8,
  sep: 9, september: 9, okt: 10, oktober: 10, nov: 11, november: 11,
  des: 12, desember: 12
};

export interface ParsedDate {
  day?: number;
  month: number;
  year: number;
  iso: string;
}

export function parseIndonesianDate(input?: string | null): ParsedDate | null {
  if (!input) return null;
  const text = input.trim().toLowerCase().replace(/\s+/g, " ");
  let match = text.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/);
  if (match) return makeDate(Number(match[1]), Number(match[2]), Number(match[3]));
  match = text.match(/^(?:(\d{1,2})\s+)?([a-z]+)\s+(\d{4})$/);
  if (!match) return null;
  const month = months[match[2] ?? ""];
  if (!month) return null;
  return makeDate(match[1] ? Number(match[1]) : undefined, month, Number(match[3]));
}

function makeDate(day: number | undefined, month: number, year: number): ParsedDate | null {
  if (year < 2000 || year > 2100 || month < 1 || month > 12) return null;
  const safeDay = day ?? 1;
  const date = new Date(Date.UTC(year, month - 1, safeDay));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== safeDay) return null;
  return { day, month, year, iso: `${year}-${String(month).padStart(2, "0")}-${String(safeDay).padStart(2, "0")}` };
}

export function analyzeDueDate(period: Period, dueDate?: string | null): AnalysisResult {
  const due = parseIndonesianDate(dueDate);
  if (!due) return {
    status: "HASIL_TIDAK_TERBACA",
    label: "HASIL TIDAK TERBACA",
    reason: "Tanggal jatuh tempo tidak ditemukan atau tidak dapat dibaca. Perlu verifikasi."
  };
  const difference = (due.year * 12 + due.month) - (period.year * 12 + period.month);
  const duePeriod = new Intl.DateTimeFormat("id-ID", { month: "long", year: "numeric", timeZone: "Asia/Makassar" })
    .format(new Date(Date.UTC(due.year, due.month - 1, 1)));
  const selectedPeriod = new Intl.DateTimeFormat("id-ID", { month: "long", year: "numeric", timeZone: "Asia/Makassar" })
    .format(new Date(Date.UTC(period.year, period.month - 1, 1)));
  if (difference >= 1) return {
    status: "TERINDIKASI_SUDAH_BAYAR",
    label: "TERINDIKASI SUDAH BAYAR",
    reason: `Jatuh tempo berikutnya sudah berpindah ke ${duePeriod}. Angsuran periode ${selectedPeriod} kemungkinan sudah dilakukan atau sudah dibukukan.`,
    warning: difference > 1
      ? "Jatuh tempo berpindah lebih dari satu periode. Perlu verifikasi tambahan karena dapat berkaitan dengan pembayaran beberapa periode, pembayaran di muka, restrukturisasi, atau perubahan jadwal."
      : undefined,
    monthDifference: difference
  };
  if (difference === 0) return {
    status: "BELUM_DAPAT_DIPASTIKAN",
    label: "BELUM DAPAT DIPASTIKAN",
    reason: "Jatuh tempo yang tampil masih berada pada periode yang diperiksa. Pembayaran belum dapat dipastikan hanya berdasarkan perubahan jatuh tempo.",
    monthDifference: difference
  };
  return {
    status: "TERINDIKASI_BELUM_DIBUKUKAN",
    label: "TERINDIKASI BELUM DIBUKUKAN",
    reason: "Jatuh tempo masih berada sebelum periode yang diperiksa. Pembayaran kemungkinan belum dilakukan, belum dibukukan, atau masih terdapat tunggakan sebelumnya.",
    monthDifference: difference
  };
}

export class SequentialQueue {
  constructor(
    public items: QueueItem[],
    public cursor = 0,
    public state: "idle" | "running" | "paused" | "stopped" = "idle"
  ) {}
  start() { this.state = "running"; }
  pause() { if (this.state === "running") this.state = "paused"; }
  resume() { if (this.state === "paused") this.state = "running"; }
  stop() { this.state = "stopped"; }
  current() { return this.items[this.cursor]; }
  skip() {
    const item = this.current();
    if (item) item.status = "DILEWATI";
    this.cursor += 1;
  }
  complete(item: QueueItem) {
    this.items[this.cursor] = item;
    this.cursor += 1;
  }
  snapshot() { return { items: this.items, cursor: this.cursor, state: this.state }; }
  canRun() { return this.state === "running" && this.cursor < this.items.length; }
}

export function isPaymentStage(text: string, url = "") {
  return /(bayar\s*sekarang|pilih\s*pembayaran|metode\s*pembayaran|konfirmasi\s*pembayaran|checkout|\/payment|\/transaction)/i.test(`${url} ${text}`);
}

export function parseVisibleInquiryText(text: string) {
  const dueDate = text.match(/(?:\b\d{1,2}[-/]\d{1,2}[-/]\d{4}\b)|(?:\b(?:\d{1,2}\s+)?(?:jan(?:uari)?|feb(?:ruari)?|mar(?:et)?|apr(?:il)?|mei|jun(?:i)?|jul(?:i)?|agu(?:stus)?|agt|sep(?:tember)?|okt(?:ober)?|nov(?:ember)?|des(?:ember)?)\s+\d{4}\b)/i)?.[0];
  const installment = text.match(/(?:cicilan|angsuran)\s*(?:ke[-\s]*)?(\d+)\s*(?:dari|\/)\s*(\d+)/i);
  const amount = text.match(/rp\s*([\d.]+)/i)?.[1]?.replaceAll(".", "");
  return {
    dueDate,
    installmentCurrent: installment?.[1] ? Number(installment[1]) : undefined,
    installmentTotal: installment?.[2] ? Number(installment[2]) : undefined,
    amount: amount ? Number(amount) : undefined,
    notFound: /(kontrak\s*tidak\s*ditemukan|data\s*tidak\s*ditemukan|nomor\s*tidak\s*terdaftar)/i.test(text)
  };
}

export function isAllowedOrigin(origin: string, allowed: string[]) {
  return allowed.some((value) => {
    try { return new URL(value.replace(/\/\*$/, "/")).origin === new URL(origin).origin; }
    catch { return false; }
  });
}

export function isRealAndSimulationSeparated(real: { simulation: boolean }[], simulated: { simulation: boolean }[]) {
  return real.every((item) => !item.simulation) && simulated.every((item) => item.simulation);
}
