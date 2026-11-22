const output = document.querySelector<HTMLElement>("#output")!;
let report: unknown;

document.querySelector("#run")?.addEventListener("click", async () => {
  output.textContent = "Memeriksa tab Tokopedia…";
  const tabs = await browser.tabs.query({ url: "https://www.tokopedia.com/angsuran/mandiri-utama-finance*" });
  const tab = tabs[0];
  if (!tab?.id) {
    output.textContent = "Tab inquiry Tokopedia belum terbuka.";
    return;
  }
  try {
    const response = await browser.tabs.sendMessage(tab.id, { type: "GET_DIAGNOSTICS" });
    report = { generatedAt: new Date().toISOString(), addonVersion: browser.runtime.getManifest().version, ...response.report };
    output.textContent = JSON.stringify(report, null, 2);
  } catch {
    output.textContent = "Content script belum siap. Muat ulang tab Tokopedia lalu coba lagi.";
  }
});

document.querySelector("#copy")?.addEventListener("click", async () => {
  if (report) await navigator.clipboard.writeText(JSON.stringify(report, null, 2));
});
