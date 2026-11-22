export const INQUIRY_URL = "https://www.tokopedia.com/angsuran/mandiri-utama-finance";

export async function getInquiryTab() {
  const tabs = await browser.tabs.query({ url: `${INQUIRY_URL}*` });
  if (tabs[0]?.id) return tabs[0];
  return browser.tabs.create({ url: INQUIRY_URL, active: false });
}

export async function focusInquiryTab() {
  const tab = await getInquiryTab();
  if (tab.id) await browser.tabs.update(tab.id, { active: true });
  if (tab.windowId) await browser.windows.update(tab.windowId, { focused: true });
}

export async function waitForReady(tabId: number, timeoutMs: number) {
  const tab = await browser.tabs.get(tabId);
  if (tab.status === "complete") return;
  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => {
      browser.tabs.onUpdated.removeListener(listener);
      reject(new Error("Halaman Tokopedia tidak siap dalam batas waktu."));
    }, timeoutMs);
    const listener = (id: number, change: { status?: string }) => {
      if (id === tabId && change.status === "complete") {
        clearTimeout(timer);
        browser.tabs.onUpdated.removeListener(listener);
        resolve();
      }
    };
    browser.tabs.onUpdated.addListener(listener);
  });
}
