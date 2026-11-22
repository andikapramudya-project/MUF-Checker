import { useEffect, useMemo, useRef, useState } from "react";
import { analyzeDueDate, parseIndonesianDate } from "@muf/inquiry-core";
import {
  CHANNEL, formatPeriod, formatWita, maskContract, parseContractList,
  type AnalysisResult, type AnalysisStatus, type CheckRecord, type Period,
  type ProtocolMessage, type QueueItem
} from "@muf/shared";
import { controlAddon, handshake, startAddonQueue, subscribeAddon } from "./bridge";
import {
  clearDatabase, loadHistory, loadQueue, loadSettings, saveHistory, saveQueue,
  saveSettings, type Settings
} from "./storage";

type View = "cek" | "antrean" | "riwayat" | "pengaturan";
type Connection = "checking" | "connected" | "missing" | "offline";
const simulationAvailable = import.meta.env.DEV && import.meta.env.VITE_ENABLE_SIMULATION === "true";

function currentPeriod(): Period {
  const parts = new Intl.DateTimeFormat("en-US", { year: "numeric", month: "numeric", timeZone: "Asia/Makassar" }).formatToParts();
  return {
    year: Number(parts.find((part) => part.type === "year")?.value),
    month: Number(parts.find((part) => part.type === "month")?.value)
  };
}

function periodInput(period: Period) {
  return `${period.year}-${String(period.month).padStart(2, "0")}`;
}

function parsePeriod(value: string): Period {
  const [year, month] = value.split("-").map(Number);
  return { year: year || currentPeriod().year, month: month || currentPeriod().month };
}

function errorAnalysis(code?: string): AnalysisResult | null {
  if (code === "NOT_FOUND") return {
    status: "KONTRAK_TIDAK_DITEMUKAN",
    label: "KONTRAK TIDAK DITEMUKAN",
    reason: "Kanal inquiry menampilkan bahwa kontrak tidak ditemukan. Periksa kembali nomor kontrak."
  };
  if (code === "SERVICE_ERROR") return {
    status: "LAYANAN_BERMASALAH",
    label: "LAYANAN BERMASALAH",
    reason: "Kanal inquiry sedang bermasalah. Belum ada kesimpulan pembayaran."
  };
  if (code === "STRUCTURE_CHANGED") return {
    status: "STRUKTUR_HALAMAN_BERUBAH",
    label: "STRUKTUR HALAMAN BERUBAH",
    reason: "Struktur halaman Tokopedia belum dikenali. Jalankan diagnostics atau lakukan kalibrasi selector."
  };
  return null;
}

export default function App() {
  const [view, setView] = useState<View>("cek");
  const [rawInput, setRawInput] = useState("");
  const [period, setPeriod] = useState<Period>(currentPeriod);
  const [history, setHistory] = useState<CheckRecord[]>([]);
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const [simulationResults, setSimulationResults] = useState<CheckRecord[]>([]);
  const [settings, setSettings] = useState<Settings>(loadSettings);
  const [connection, setConnection] = useState<Connection>(navigator.onLine ? "checking" : "offline");
  const [queueState, setQueueState] = useState<"idle" | "running" | "paused" | "action_required">("idle");
  const [notice, setNotice] = useState("");
  const [hydrated, setHydrated] = useState(false);
  const simulationStop = useRef(false);

  const parsed = useMemo(() => parseContractList(rawInput), [rawInput]);
  const completed = queue.filter((item) => ["SELESAI", "PERLU_VERIFIKASI", "GAGAL", "DILEWATI", "DIHENTIKAN"].includes(item.status)).length;
  const active = queue.find((item) => ["MEMPERSIAPKAN", "MENGISI", "MENGIRIM", "MENUNGGU_HASIL", "MEMBACA", "MENGANALISIS"].includes(item.status));
  const simulationEnabled = simulationAvailable && settings.simulation;

  useEffect(() => {
    void Promise.all([loadHistory(), loadQueue()]).then(([savedHistory, savedQueue]) => {
      setHistory(savedHistory.filter((record) => !record.inquiry.simulation));
      setQueue(savedQueue);
      setHydrated(true);
    }).catch(() => {
      setNotice("Penyimpanan lokal tidak dapat dibuka.");
      setHydrated(true);
    });
  }, []);

  useEffect(() => {
    if (hydrated) void saveHistory(history);
  }, [history, hydrated]);
  useEffect(() => {
    if (hydrated) void saveQueue(queue);
  }, [queue, hydrated]);
  useEffect(() => saveSettings(settings), [settings]);

  useEffect(() => {
    const unsubscribe = subscribeAddon(handleAddonMessage);
    const onOnline = () => void checkConnection();
    const onOffline = () => setConnection("offline");
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    void checkConnection();
    return () => {
      unsubscribe();
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, []);

  async function checkConnection() {
    if (!navigator.onLine) {
      setConnection("offline");
      return;
    }
    setConnection("checking");
    try {
      await handshake();
      setConnection("connected");
    } catch {
      setConnection("missing");
    }
  }

  function handleAddonMessage(message: ProtocolMessage) {
    if (message.messageType === "QUEUE_PROGRESS") {
      const payload = message.payload as { item?: QueueItem; state?: string; message?: string; done?: boolean };
      if (payload.item) {
        setQueue((items) => items.map((item) => item.id === payload.item?.id ? { ...item, ...payload.item } : item));
      }
      if (payload.message) setNotice(payload.message);
      if (payload.state === "paused") setQueueState("paused");
      else if (payload.state === "action_required") setQueueState("action_required");
      else if (payload.done || payload.state === "idle") setQueueState("idle");
      else setQueueState("running");
    }
    if (message.messageType === "QUEUE_RESULT") {
      const payload = message.payload as { item?: QueueItem; inquiry?: CheckRecord["inquiry"] };
      if (!payload.item || !payload.inquiry) return;
      const analysis = errorAnalysis(payload.inquiry.errorCode) ?? analyzeDueDate(payload.item.period, payload.inquiry.dueDate);
      const record: CheckRecord = {
        id: crypto.randomUUID(),
        contractNumber: payload.item.contractNumber,
        maskedContract: payload.item.maskedContract,
        period: payload.item.period,
        inquiry: { ...payload.inquiry, source: CHANNEL, simulation: false },
        analysis,
        queueStatus: analysis.status === "HASIL_TIDAK_TERBACA" ? "PERLU_VERIFIKASI" : "SELESAI",
        createdAt: payload.inquiry.inquiryAt
      };
      setHistory((items) => [record, ...items]);
      setQueue((items) => items.map((item) => item.id === payload.item?.id ? { ...item, status: record.queueStatus, result: record } : item));
    }
  }

  function createQueue(contracts: string[]) {
    return contracts.map<QueueItem>((contractNumber) => ({
      id: crypto.randomUUID(),
      contractNumber,
      maskedContract: maskContract(contractNumber),
      period,
      status: "MENUNGGU",
      attempts: 0
    }));
  }

  async function start(contracts = parsed.valid) {
    if (!contracts.length) {
      setNotice("Masukkan setidaknya satu nomor kontrak yang valid.");
      return;
    }
    if (!period.year || !period.month) {
      setNotice("Pilih periode pemeriksaan.");
      return;
    }
    const items = createQueue(contracts);
    setQueue(items);
    setView("antrean");
    if (simulationEnabled) {
      simulationStop.current = false;
      setSimulationResults([]);
      setQueueState("running");
      const module = await import("./dev-simulation");
      await module.simulate(items, (item, record) => {
        setSimulationResults((records) => [record, ...records]);
        setQueue((current) => current.map((entry) => entry.id === item.id ? { ...entry, status: "SELESAI" } : entry));
      }, () => simulationStop.current);
      setQueueState("idle");
      return;
    }
    if (connection !== "connected") {
      setNotice("Add-on pemeriksaan belum terpasang atau belum tersambung.");
      return;
    }
    try {
      await startAddonQueue(items, {
        delayMs: settings.delayMs,
        timeoutMs: settings.timeoutMs,
        maxRetries: settings.maxRetries,
        saveAmount: settings.saveAmount
      });
      setQueueState("running");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Antrean tidak dapat dikirim ke add-on.");
    }
  }

  async function control(command: "PAUSE" | "RESUME" | "STOP" | "SKIP" | "RETRY" | "OPEN_TOKOPEDIA") {
    if (simulationEnabled) {
      if (command === "STOP") {
        simulationStop.current = true;
        setQueueState("idle");
      }
      setNotice("Kontrol penuh antrean diuji pada add-on; simulasi pengembangan hanya mendukung hentikan.");
      return;
    }
    try {
      await controlAddon(command);
      if (command === "PAUSE") setQueueState("paused");
      if (command === "RESUME" || command === "RETRY") setQueueState("running");
      if (command === "STOP") setQueueState("idle");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Perintah tidak dapat dikirim.");
    }
  }

  async function clearAll() {
    if (!window.confirm("Hapus seluruh riwayat dan checkpoint antrean pada perangkat ini?")) return;
    await clearDatabase();
    setHistory([]);
    setQueue([]);
    setSimulationResults([]);
    setNotice("Seluruh riwayat lokal telah dihapus.");
  }

  function deleteRecord(id: string) {
    setHistory((records) => records.filter((record) => record.id !== id));
    setSimulationResults((records) => records.filter((record) => record.id !== id));
  }

  function exportCsv() {
    const rows = [["Nomor Kontrak", "Periode", "Jatuh Tempo", "Status", "Kanal", "Waktu"]];
    history.forEach((record) => rows.push([
      record.maskedContract, formatPeriod(record.period), record.inquiry.dueDate ?? "",
      record.analysis.label, record.inquiry.source, formatWita(record.createdAt)
    ]));
    const content = rows.map((row) => row.map((value) => `"${value.replaceAll('"', '""')}"`).join(",")).join("\n");
    const link = document.createElement("a");
    link.href = URL.createObjectURL(new Blob(["\ufeff", content], { type: "text/csv;charset=utf-8" }));
    link.download = "riwayat-cek-angsuran-muf.csv";
    link.click();
    URL.revokeObjectURL(link.href);
  }

  return (
    <div className="app">
      <header className="header">
        <div className="brand-line"><span aria-hidden="true" /><strong>ALAT BANTU INQUIRY</strong></div>
        <h1>CEK STATUS ANGSURAN MUF</h1>
        <p>Pemeriksaan indikasi pembayaran berdasarkan jatuh tempo yang tampil pada kanal inquiry Tokopedia.</p>
        <div className={`connection ${connection}`}>
          <span className="connection-dot" />
          <strong>{connectionText(connection)}</strong>
          <button onClick={() => void checkConnection()}>Periksa</button>
        </div>
      </header>

      {simulationEnabled && <div className="simulation-banner" role="status">MODE SIMULASI · KHUSUS PENGEMBANGAN · BUKAN HASIL TOKOPEDIA</div>}
      {notice && <div className="notice" role="status"><span>{notice}</span><button aria-label="Tutup pesan" onClick={() => setNotice("")}>×</button></div>}

      <main>
        {view === "cek" && (
          <section aria-labelledby="check-title">
            <h2 id="check-title">Masukkan nomor kontrak</h2>
            <p className="section-copy">Satu nomor kontrak per baris. Koma, titik koma, tab, dan spasi juga didukung.</p>
            <label>Periode angsuran
              <input type="month" value={periodInput(period)} onChange={(event) => setPeriod(parsePeriod(event.target.value))} />
            </label>
            <label>Nomor kontrak
              <textarea
                value={rawInput}
                onChange={(event) => setRawInput(event.target.value)}
                rows={9}
                inputMode="numeric"
                placeholder="Masukkan nomor kontrak di sini"
                aria-describedby="input-summary"
              />
            </label>
            <div className="input-summary" id="input-summary">
              <span><strong>{parsed.valid.length}</strong> valid</span>
              <span><strong>{parsed.duplicates.length}</strong> duplikat</span>
              <span className={parsed.invalid.length ? "has-error" : ""}><strong>{parsed.invalid.length}</strong> tidak valid</span>
            </div>
            {parsed.invalid.length > 0 && <p className="validation">Nomor tidak valid tidak akan dikirim ke add-on.</p>}
            <button className="primary" disabled={!parsed.valid.length} onClick={() => void start()}>CEK SEMUA</button>
            <p className="privacy-note">Data hanya disimpan pada perangkat ini. Add-on tidak membaca password, OTP, cookie, atau token.</p>
          </section>
        )}

        {view === "antrean" && (
          <section aria-labelledby="queue-title">
            <div className="section-head">
              <div><h2 id="queue-title">Antrean pemeriksaan</h2><p>{queue.length ? `Memeriksa ${Math.min(completed + (active ? 1 : 0), queue.length)} dari ${queue.length} kontrak.` : "Belum ada antrean."}</p></div>
              <span className={`state-pill ${queueState}`}>{queueStateLabel(queueState)}</span>
            </div>
            <div className="progress" aria-label={`${completed} dari ${queue.length} selesai`}><span style={{ width: `${queue.length ? completed / queue.length * 100 : 0}%` }} /></div>
            {active && <div className="active-contract">Sedang memeriksa <strong>{active.maskedContract}</strong></div>}
            {queueState === "action_required" && (
              <div className="action-required">
                <strong>Tokopedia memerlukan tindakan Anda.</strong>
                <p>Selesaikan login atau verifikasi pada tab Tokopedia, kemudian tekan Lanjutkan.</p>
                <button onClick={() => void control("OPEN_TOKOPEDIA")}>Buka Tokopedia</button>
              </div>
            )}
            <div className="controls">
              {queueState === "running" && <button onClick={() => void control("PAUSE")}>Jeda</button>}
              {(queueState === "paused" || queueState === "action_required") && <button className="primary small" onClick={() => void control("RESUME")}>Lanjutkan</button>}
              <button onClick={() => void control("SKIP")} disabled={!queue.length}>Lewati</button>
              <button onClick={() => void control("RETRY")} disabled={!queue.length}>Coba Ulang</button>
              <button className="danger" onClick={() => void control("STOP")} disabled={!queue.length}>Hentikan</button>
            </div>
            {queue.length === 0 ? <Empty text="Belum ada antrean pemeriksaan." /> : (
              <div className="queue-list">
                {queue.map((item, index) => (
                  <article className="queue-row" key={item.id}>
                    <span className="queue-index">{index + 1}</span>
                    <div><strong>{settings.maskSensitive ? item.maskedContract : item.contractNumber}</strong><small>{formatPeriod(item.period)}</small></div>
                    <StatusPill status={item.status} />
                  </article>
                ))}
              </div>
            )}
            {simulationResults.length > 0 && (
              <div className="simulation-results">
                <h3>Hasil simulasi pengembangan</h3>
                {simulationResults.map((record) => <ResultCard key={record.id} record={record} hideFull={settings.maskSensitive} onDelete={() => deleteRecord(record.id)} onRetry={() => void start(record.contractNumber ? [record.contractNumber] : [])} />)}
              </div>
            )}
          </section>
        )}

        {view === "riwayat" && (
          <section aria-labelledby="history-title">
            <div className="section-head">
              <div><h2 id="history-title">Riwayat pemeriksaan</h2><p>Riwayat nyata tersimpan lokal dengan IndexedDB.</p></div>
              <button onClick={exportCsv} disabled={!history.length}>Ekspor CSV</button>
            </div>
            {history.length === 0 ? <Empty text="Belum ada hasil pemeriksaan." /> : (
              <div className="results">
                {history.map((record) => (
                  <ResultCard key={record.id} record={record} hideFull={settings.maskSensitive} onDelete={() => deleteRecord(record.id)} onRetry={() => void start(record.contractNumber ? [record.contractNumber] : [])} />
                ))}
              </div>
            )}
          </section>
        )}

        {view === "pengaturan" && (
          <section aria-labelledby="settings-title">
            <h2 id="settings-title">Pengaturan</h2>
            <Toggle label="Sembunyikan data sensitif" description="Tampilkan nomor kontrak tersamarkan." checked={settings.maskSensitive} onChange={(value) => setSettings({ ...settings, maskSensitive: value })} />
            <Toggle label="Simpan nominal tagihan" description="Nonaktif secara default untuk meminimalkan data." checked={settings.saveAmount} onChange={(value) => setSettings({ ...settings, saveAmount: value })} />
            {simulationAvailable && <Toggle label="Mode simulasi pengembangan" description="Tidak tersedia pada production build." checked={settings.simulation} onChange={(value) => setSettings({ ...settings, simulation: value })} />}
            <label>Jeda antar-inquiry
              <select value={settings.delayMs} onChange={(event) => setSettings({ ...settings, delayMs: Number(event.target.value) })}>
                <option value="3000">3 detik</option><option value="5000">5 detik</option><option value="8000">8 detik</option>
              </select>
            </label>
            <label>Timeout inquiry
              <select value={settings.timeoutMs} onChange={(event) => setSettings({ ...settings, timeoutMs: Number(event.target.value) })}>
                <option value="30000">30 detik</option><option value="45000">45 detik</option><option value="60000">60 detik</option>
              </select>
            </label>
            <label>Retry maksimal
              <select value={settings.maxRetries} onChange={(event) => setSettings({ ...settings, maxRetries: Number(event.target.value) })}>
                <option value="0">Tanpa retry</option><option value="1">1 kali</option><option value="2">2 kali</option>
              </select>
            </label>
            <div className="settings-note">
              <strong>Privasi</strong>
              <p>Tidak ada analytics, backend, atau pengiriman data konsumen ke Netlify. Kanal inquiry: Tokopedia.</p>
            </div>
            <button className="danger wide" onClick={() => void clearAll()}>Hapus Seluruh Riwayat</button>
          </section>
        )}
      </main>

      <nav className="bottom-nav" aria-label="Navigasi utama">
        <Nav active={view === "cek"} label="Cek" icon="⌕" onClick={() => setView("cek")} />
        <Nav active={view === "antrean"} label="Antrean" icon="≡" onClick={() => setView("antrean")} />
        <Nav active={view === "riwayat"} label="Riwayat" icon="◷" onClick={() => setView("riwayat")} />
        <Nav active={view === "pengaturan"} label="Pengaturan" icon="⚙" onClick={() => setView("pengaturan")} />
      </nav>
    </div>
  );
}

function ResultCard({ record, hideFull, onDelete, onRetry }: { record: CheckRecord; hideFull: boolean; onDelete: () => void; onRetry: () => void }) {
  const tone = statusTone(record.analysis.status);
  const parsedDate = parseIndonesianDate(record.inquiry.dueDate);
  const dueDate = parsedDate
    ? `${String(parsedDate.day ?? 1).padStart(2, "0")}-${String(parsedDate.month).padStart(2, "0")}-${parsedDate.year}`
    : "Belum ada hasil";
  const contract = hideFull ? record.maskedContract : record.contractNumber ?? record.maskedContract;
  const copied = [
    "CEK STATUS ANGSURAN MUF",
    `Nomor Kontrak: ${record.maskedContract}`,
    `Periode Diperiksa: ${formatPeriod(record.period)}`,
    `Jatuh Tempo Tampil: ${dueDate}`,
    `Hasil: ${record.analysis.label}`,
    `Alasan: ${record.analysis.reason}`,
    `Kanal Inquiry: ${record.inquiry.simulation ? "Simulasi pengembangan" : record.inquiry.source}`,
    `Waktu Pemeriksaan: ${formatWita(record.createdAt)}`,
    "",
    "Catatan: Hasil merupakan indikasi operasional dan bukan konfirmasi pembayaran resmi dari MUF atau Tokopedia."
  ].join("\n");
  return (
    <article className={`result-card ${tone}`}>
      {record.inquiry.simulation && <div className="simulation-label">SIMULASI · BUKAN HASIL TOKOPEDIA</div>}
      <div className="result-title">
        <span className="result-icon" aria-hidden="true">{tone === "paid" ? "✓" : tone === "unpaid" ? "×" : "!"}</span>
        <div><small>{contract}</small><h3>{record.analysis.label}</h3></div>
      </div>
      <dl>
        <div><dt>Jatuh tempo</dt><dd>{dueDate}</dd></div>
        <div><dt>Periode</dt><dd>{formatPeriod(record.period)}</dd></div>
        <div><dt>Cicilan</dt><dd>{record.inquiry.installmentCurrent ? `${record.inquiry.installmentCurrent} dari ${record.inquiry.installmentTotal}` : "Tidak tersedia"}</dd></div>
        <div><dt>Kanal inquiry</dt><dd>{record.inquiry.simulation ? "Simulasi" : "Tokopedia"}</dd></div>
      </dl>
      <details><summary>Detail</summary><p>{record.analysis.reason}</p>{record.analysis.warning && <p className="warning">{record.analysis.warning}</p>}<p>Waktu: {formatWita(record.createdAt)}</p><p>Hasil merupakan indikasi operasional berdasarkan jatuh tempo yang tampil pada kanal inquiry dan bukan konfirmasi pembayaran resmi dari MUF atau Tokopedia.</p></details>
      <div className="result-actions">
        <button onClick={() => void navigator.clipboard.writeText(copied)}>Salin</button>
        <button onClick={onRetry}>Periksa Ulang</button>
        <button className="danger-text" onClick={onDelete}>Hapus</button>
      </div>
    </article>
  );
}

function Toggle({ label, description, checked, onChange }: { label: string; description: string; checked: boolean; onChange: (value: boolean) => void }) {
  return <label className="toggle-row"><div><strong>{label}</strong><small>{description}</small></div><input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} /><span /></label>;
}

function Nav({ active, label, icon, onClick }: { active: boolean; label: string; icon: string; onClick: () => void }) {
  return <button className={active ? "active" : ""} onClick={onClick}><span aria-hidden="true">{icon}</span><strong>{label}</strong></button>;
}

function Empty({ text }: { text: string }) {
  return <div className="empty"><span aria-hidden="true">◎</span><p>{text}</p></div>;
}

function StatusPill({ status }: { status: QueueItem["status"] }) {
  const labels: Partial<Record<QueueItem["status"], string>> = {
    MENUNGGU: "Menunggu", MEMPERSIAPKAN: "Mempersiapkan", MENGISI: "Mengisi",
    MENGIRIM: "Mengirim", MENUNGGU_HASIL: "Menunggu hasil", MEMBACA: "Membaca",
    MENGANALISIS: "Menganalisis", SELESAI: "Selesai", PERLU_VERIFIKASI: "Perlu verifikasi",
    LOGIN_DIPERLUKAN: "Perlu login", CAPTCHA: "Verifikasi", RATE_LIMIT: "Dibatasi",
    GAGAL: "Gagal", DILEWATI: "Dilewati", DIHENTIKAN: "Dihentikan"
  };
  return <span className={`status-pill ${status.toLowerCase()}`}>{labels[status] ?? status}</span>;
}

function connectionText(connection: Connection) {
  if (connection === "connected") return "Add-on tersambung";
  if (connection === "offline") return "Offline";
  if (connection === "checking") return "Memeriksa add-on";
  return "Add-on belum tersambung";
}

function queueStateLabel(state: "idle" | "running" | "paused" | "action_required") {
  if (state === "running") return "Berjalan";
  if (state === "paused") return "Dijeda";
  if (state === "action_required") return "Perlu tindakan";
  return "Siap";
}

function statusTone(status: AnalysisStatus) {
  if (status === "TERINDIKASI_SUDAH_BAYAR") return "paid";
  if (status === "TERINDIKASI_BELUM_DIBUKUKAN") return "unpaid";
  return "uncertain";
}
