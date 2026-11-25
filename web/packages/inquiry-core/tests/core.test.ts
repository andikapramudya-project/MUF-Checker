import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { maskContract, normalizeContract, parseContractList, type QueueItem } from "@muf/shared";
import {
  analyzeDueDate, isAllowedOrigin, isPaymentStage, isRealAndSimulationSeparated,
  parseIndonesianDate, parseVisibleInquiryText, SequentialQueue
} from "../src/index";

describe("analisis jatuh tempo", () => {
  it.each([
    [{ year: 2026, month: 8 }, "04-09-2026", "TERINDIKASI_SUDAH_BAYAR"],
    [{ year: 2026, month: 8 }, "4 Agustus 2026", "BELUM_DAPAT_DIPASTIKAN"],
    [{ year: 2026, month: 8 }, "04 Jul 2026", "TERINDIKASI_BELUM_DIBUKUKAN"],
    [{ year: 2026, month: 12 }, "Januari 2027", "TERINDIKASI_SUDAH_BAYAR"]
  ] as const)("menganalisis %j dan %s", (period, due, status) => {
    expect(analyzeDueDate(period, due).status).toBe(status);
  });
  it("tidak menyimpulkan tanggal kosong", () => {
    expect(analyzeDueDate({ year: 2026, month: 8 }, null).status).toBe("HASIL_TIDAK_TERBACA");
  });
});

describe("input dan parser", () => {
  it.each(["04-09-2026", "4 September 2026", "04 Sep 2026", "SEP 2026", "September 2026"])("membaca %s", (value) => {
    expect(parseIndonesianDate(value)?.month).toBe(9);
  });
  it("membaca beberapa pemisah, duplikat, dan nomor invalid", () => {
    const parsed = parseContractList("070229001234, 070229001235;\n070229001234 abc");
    expect(parsed.valid).toHaveLength(2);
    expect(parsed.duplicates).toHaveLength(1);
    expect(parsed.invalid).toEqual(["abc"]);
  });
  it("tidak mengubah digit saat normalisasi dan menyamarkan", () => {
    expect(normalizeContract("0702-2900.1234")).toBe("070229001234");
    expect(maskContract("070229001234")).toBe("07022900****");
  });
});

describe("antrean, checkpoint, origin, dan pengaman", () => {
  const items: QueueItem[] = [
    { id: "1", contractNumber: "070229001234", maskedContract: "07022900****", period: { year: 2026, month: 8 }, status: "MENUNGGU", attempts: 0 },
    { id: "2", contractNumber: "070229001235", maskedContract: "07022900****", period: { year: 2026, month: 8 }, status: "MENUNGGU", attempts: 0 }
  ];
  it("berjalan sekuensial serta pause dan resume", () => {
    const queue = new SequentialQueue(structuredClone(items));
    queue.start();
    expect(queue.current()?.id).toBe("1");
    queue.pause();
    expect(queue.canRun()).toBe(false);
    queue.resume();
    queue.skip();
    expect(queue.current()?.id).toBe("2");
  });
  it("memulihkan checkpoint setelah reload", () => {
    const original = new SequentialQueue(structuredClone(items), 1, "paused").snapshot();
    const restored = new SequentialQueue(original.items, original.cursor, original.state);
    expect(restored.current()?.id).toBe("2");
    expect(restored.state).toBe("paused");
  });
  it("menolak origin asing", () => {
    expect(isAllowedOrigin("https://muf-check.netlify.app", ["https://muf-check.netlify.app/*"])).toBe(true);
    expect(isAllowedOrigin("https://asing.invalid", ["https://muf-check.netlify.app/*"])).toBe(false);
  });
  it("mencegah tahap pembayaran", () => {
    expect(isPaymentStage("Konfirmasi Pembayaran")).toBe(true);
    expect(isPaymentStage("Cek tagihan")).toBe(false);
  });
  it("memisahkan hasil simulasi dan nyata", () => {
    expect(isRealAndSimulationSeparated([{ simulation: false }], [{ simulation: true }])).toBe(true);
  });
  it("membaca fixture sintetis", () => {
    const html = readFileSync(resolve(import.meta.dirname, "../../../fixtures/inquiry-success.html"), "utf8");
    const parsed = parseVisibleInquiryText(html.replace(/<[^>]+>/g, " "));
    expect(parsed.dueDate).toBe("04 September 2026");
    expect(parsed.installmentCurrent).toBe(12);
  });
});
