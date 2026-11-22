const statusElement = document.querySelector<HTMLElement>("#status")!;

void browser.runtime.sendMessage({ type: "GET_ADDON_STATUS" }).then((checkpoint) => {
  statusElement.textContent = checkpoint?.state === "running" ? "Pemeriksaan sedang berjalan." : "Add-on siap.";
}).catch(() => {
  statusElement.textContent = "Status add-on tidak tersedia.";
});

document.querySelector("#open")?.addEventListener("click", () => void browser.runtime.sendMessage({ type: "OPEN_TOKOPEDIA" }));
document.querySelector("#diagnostics")?.addEventListener("click", () => void browser.runtime.sendMessage({ type: "OPEN_DIAGNOSTICS" }));
