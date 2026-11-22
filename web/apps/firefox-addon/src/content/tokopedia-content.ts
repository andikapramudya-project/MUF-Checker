import { TokopediaPageAdapter } from "./page-adapter";

const adapter = new TokopediaPageAdapter();

browser.runtime.onMessage.addListener(async (message) => {
  if (message?.type === "GET_DIAGNOSTICS") return { ok: true, report: adapter.diagnostics() };
  if (message?.type !== "RUN_INQUIRY") return undefined;
  return runInquiry(
    String(message.contractNumber ?? ""),
    String(message.maskedContract ?? ""),
    Number(message.timeoutMs) || 30000,
    Boolean(message.includeAmount)
  );
});

async function runInquiry(contractNumber: string, maskedContract: string, timeoutMs: number, includeAmount: boolean) {
  if (adapter.paymentStage()) return { ok: false, message: "Pemeriksaan dihentikan karena halaman telah mencapai batas tahap inquiry.", code: "PAYMENT_STAGE" };
  const actionRequired = adapter.actionRequired();
  if (actionRequired) return { ok: false, actionRequired: true, ...actionRequired };
  const field = adapter.findContractField();
  const button = adapter.findInquiryButton();
  if (!field || !button) {
    return {
      ok: true,
      result: {
        maskedContract,
        inquiryAt: new Date().toISOString(),
        source: "Tokopedia",
        simulation: false,
        errorCode: "STRUCTURE_CHANGED",
        errorMessage: !field ? "Field nomor kontrak tidak ditemukan." : "Tombol inquiry tidak ditemukan."
      }
    };
  }
  const before = adapter.fingerprint();
  adapter.fillContract(field, contractNumber);
  if (adapter.paymentStage()) return { ok: false, message: "Pemeriksaan dihentikan karena halaman telah mencapai batas tahap inquiry.", code: "PAYMENT_STAGE" };
  button.click();
  const changed = await waitForFreshResult(before, timeoutMs);
  if (!changed) return { ok: false, message: "Hasil inquiry tidak muncul dalam batas waktu.", code: "TIMEOUT" };
  const afterAction = adapter.actionRequired();
  if (afterAction) return { ok: false, actionRequired: true, ...afterAction };
  if (adapter.paymentStage()) return { ok: false, message: "Pemeriksaan dihentikan karena halaman telah mencapai batas tahap inquiry.", code: "PAYMENT_STAGE" };
  const result = adapter.readResult(maskedContract, includeAmount);
  if (!result.dueDate && !result.errorCode) {
    result.errorCode = "STRUCTURE_CHANGED";
    result.errorMessage = "Tanggal jatuh tempo tidak dapat dibaca.";
  }
  adapter.fillContract(field, "");
  return { ok: true, result };
}

async function waitForFreshResult(before: string, timeoutMs: number) {
  const startedAt = Date.now();
  return new Promise<boolean>((resolve) => {
    const observer = new MutationObserver(check);
    const timer = window.setInterval(check, 350);
    function finish(value: boolean) {
      observer.disconnect();
      window.clearInterval(timer);
      resolve(value);
    }
    function check() {
      const current = adapter.fingerprint();
      if ((current && current !== before) || adapter.actionRequired()) finish(true);
      else if (Date.now() - startedAt >= timeoutMs) finish(false);
    }
    observer.observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true });
    check();
  });
}
