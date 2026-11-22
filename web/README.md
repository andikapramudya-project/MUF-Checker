# Cek Status Angsuran MUF

Aplikasi lokal-first untuk membantu memeriksa indikasi pembayaran berdasarkan tanggal jatuh tempo yang tampil pada kanal inquiry Tokopedia.

Project ini terdiri dari:

- dashboard PWA React yang dapat di-host di Netlify;
- add-on WebExtensions untuk Firefox Android/desktop;
- modul antrean, parser tanggal Indonesia, dan analisis status bersama;
- fixture sintetis khusus pengujian.

> Status integrasi Tokopedia: **memerlukan kalibrasi langsung**. Page adapter sudah dibuat secara defensif, tetapi belum boleh dianggap berhasil membaca halaman nyata sebelum diuji pada DOM Tokopedia terbaru.

## Prinsip privasi

- Nomor kontrak dan hasil disimpan di perangkat pengguna.
- Dashboard tidak memiliki backend, database, analytics, atau Netlify Function.
- Add-on tidak membaca cookie, token, password, atau OTP.
- Add-on hanya bekerja pada tahap inquiry dan berhenti bila menemukan tahap pembayaran.
- Data fixture/simulasi tidak menjadi hasil produksi.

## Struktur

```text
apps/
  dashboard/       PWA React + IndexedDB
  firefox-addon/   add-on Manifest V2 untuk Firefox
packages/
  shared/          protokol pesan dan utilitas bersama
  inquiry-core/    parser, analisis jatuh tempo, dan antrean
docs/              panduan instalasi, deployment, dan kalibrasi
fixtures/          HTML sintetis untuk test
```

## Menjalankan dashboard

Persyaratan: Node.js 22+ dan pnpm 10.

```bash
pnpm install
pnpm dev
```

Buka URL lokal yang ditampilkan Vite. Simulasi pengembangan **tidak aktif secara default**. Jika benar-benar diperlukan untuk pengembangan:

```bash
VITE_ENABLE_SIMULATION=true pnpm dev
```

Mode tersebut hanya tersedia pada development build, menampilkan banner besar, dan tidak menyimpan hasil ke riwayat nyata.

## Build dan pemeriksaan

```bash
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

Hasil dashboard berada di `apps/dashboard/dist`. Hasil add-on berada di `apps/firefox-addon/dist`.

## Menghubungkan dashboard dengan add-on

1. Deploy dashboard ke Netlify.
2. Atur `DASHBOARD_ORIGINS` saat build add-on dengan origin persis, tanpa wildcard dan tanpa path:

   ```bash
   DASHBOARD_ORIGINS=https://nama-situs.netlify.app pnpm build:addon
   ```

3. Pasang build add-on yang telah ditandatangani Mozilla.
4. Buka dashboard dari origin yang sama.

Firefox tidak mendukung komunikasi halaman web melalui `externally_connectable`. Karena itu project memakai content-script bridge pada origin yang diizinkan secara eksplisit.

## Dokumentasi

- [Instalasi Firefox Android](docs/INSTALL_FIREFOX_ANDROID.md)
- [Deployment GitHub ke Netlify](docs/NETLIFY_DEPLOYMENT.md)
- [Kalibrasi selector Tokopedia](docs/TOKOPEDIA_SELECTOR_CALIBRATION.md)
- [Privasi dan batasan](docs/PRIVACY_AND_LIMITATIONS.md)
- [Pemecahan masalah](docs/TROUBLESHOOTING.md)

## Penafian

Hasil adalah indikasi operasional berdasarkan jatuh tempo yang tampil pada kanal inquiry. Hasil bukan konfirmasi pembayaran resmi dari MUF atau Tokopedia. Project ini bukan produk resmi MUF maupun Tokopedia.
