import { isPaymentStage, parseVisibleInquiryText } from "@muf/inquiry-core";
import type { InquiryPayload } from "@muf/shared";

const loginPattern = /(masuk\s*ke\s*akun|login|nomor\s*handphone\s*atau\s*email)/i;
const verificationPattern = /(captcha|verifikasi\s*perangkat|kode\s*otp)/i;
const rateLimitPattern = /(terlalu\s*banyak\s*permintaan|rate\s*limit|coba\s*lagi\s*nanti)/i;
const serviceErrorPattern = /(layanan\s*sedang\s*bermasalah|terjadi\s*gangguan|silakan\s*coba\s*lagi)/i;
const datePattern = /(?:\b\d{1,2}[-/]\d{1,2}[-/]\d{4}\b)|(?:\b(?:\d{1,2}\s+)?(?:jan(?:uari)?|feb(?:ruari)?|mar(?:et)?|apr(?:il)?|mei|jun(?:i)?|jul(?:i)?|agu(?:stus)?|agt|sep(?:tember)?|okt(?:ober)?|nov(?:ember)?|des(?:ember)?)\s+\d{4}\b)/i;
const notFoundPattern = /(kontrak\s*tidak\s*ditemukan|data\s*tidak\s*ditemukan|nomor\s*tidak\s*terdaftar)/i;

export class TokopediaPageAdapter {
  bodyText() {
    return document.body?.innerText ?? "";
  }

  paymentStage() {
    return isPaymentStage(this.bodyText(), location.href);
  }

  actionRequired() {
    const text = this.bodyText();
    if (rateLimitPattern.test(text)) return { code: "RATE_LIMIT", message: "Pembatasan inquiry terdeteksi. Pemeriksaan dihentikan." };
    if (verificationPattern.test(text)) return { code: "CAPTCHA", message: "CAPTCHA atau verifikasi terdeteksi. Pemeriksaan dijeda." };
    if (loginPattern.test(text)) return { code: "LOGIN", message: "Tokopedia memerlukan login." };
    return null;
  }

  findContractField(): HTMLInputElement | null {
    const fields = [...document.querySelectorAll<HTMLInputElement>("input:not([type='hidden']):not([type='password'])")];
    return fields.find((field) => {
      const labels = field.labels ? [...field.labels].map((label) => label.innerText).join(" ") : "";
      const context = `${labels} ${field.name} ${field.id} ${field.placeholder} ${field.getAttribute("aria-label") ?? ""}`;
      return /(nomor|no\.?)\s*(kontrak|pelanggan)|contract/i.test(context);
    }) ?? null;
  }

  findInquiryButton(): HTMLElement | null {
    const actions = [...document.querySelectorAll<HTMLElement>("button, [role='button']")];
    return actions.find((action) => {
      const text = `${action.innerText} ${action.getAttribute("aria-label") ?? ""}`.trim();
      const disabled = action.getAttribute("aria-disabled") === "true" || (action instanceof HTMLButtonElement && action.disabled);
      return !disabled && !isPaymentStage(text) && /^(cek(\s+(tagihan|status))?|lihat\s+tagihan|inquiry)$/i.test(text);
    }) ?? null;
  }

  resultText() {
    const candidates = [...document.querySelectorAll<HTMLElement>("[role='status'], [aria-live], section, article")];
    const result = candidates.find((node) => datePattern.test(node.innerText) || notFoundPattern.test(node.innerText) || serviceErrorPattern.test(node.innerText));
    return result?.innerText ?? "";
  }

  fingerprint() {
    return this.resultText().replace(/\s+/g, " ").slice(0, 1800);
  }

  diagnostics() {
    const body = this.bodyText();
    const result = this.resultText();
    return {
      urlRecognized: location.hostname === "www.tokopedia.com" && location.pathname.startsWith("/angsuran/mandiri-utama-finance"),
      contractField: Boolean(this.findContractField()),
      inquiryButton: Boolean(this.findInquiryButton()),
      loadingState: Boolean(document.querySelector("[aria-busy='true'], [role='progressbar']")),
      resultContainer: Boolean(result),
      dueDate: datePattern.test(result),
      installment: /(?:cicilan|angsuran)/i.test(result),
      contractNotFound: notFoundPattern.test(result),
      login: loginPattern.test(body),
      verification: verificationPattern.test(body) || rateLimitPattern.test(body),
      paymentStage: this.paymentStage()
    };
  }

  readResult(maskedContract: string, includeAmount: boolean): InquiryPayload {
    const text = this.resultText();
    const parsed = parseVisibleInquiryText(text);
    return {
      maskedContract,
      dueDate: parsed.dueDate,
      installmentCurrent: parsed.installmentCurrent,
      installmentTotal: parsed.installmentTotal,
      amount: includeAmount ? parsed.amount : undefined,
      inquiryAt: new Date().toISOString(),
      source: "Tokopedia",
      simulation: false,
      errorCode: parsed.notFound ? "NOT_FOUND" : serviceErrorPattern.test(text) ? "SERVICE_ERROR" : undefined
    };
  }

  fillContract(field: HTMLInputElement, value: string) {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
    setter?.call(field, "");
    field.dispatchEvent(new Event("input", { bubbles: true }));
    setter?.call(field, value);
    field.dispatchEvent(new Event("input", { bubbles: true }));
    field.dispatchEvent(new Event("change", { bubbles: true }));
  }
}
