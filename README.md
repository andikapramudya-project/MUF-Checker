# MUF-Checker

Pemeriksaan indikasi status angsuran MUF melalui tampilan Tokopedia. Android native dan dashboard web/add-on berada dalam satu repositori.

## Struktur dan build

- `app/`: Android native 0.8.5, paket `id.local.mufchecker`.
- `web/`: dashboard React, add-on Firefox, modul parser dan antrean versi 2.0.0.
- `tests/`: pengujian native dan fixture sintetis.

Android: gunakan PowerShell 7, JDK 17+, Android SDK Platform 36 dan Build Tools 36.0.0.

```powershell
.\scripts\build.ps1 -Unsigned
```

Build unsigned tidak bisa dipasang sebagai pembaruan APK bertanda tangan. Untuk pembaruan, gunakan kunci asli di luar repositori melalui `MUF_KEYSTORE`, `MUF_KEY_ALIAS`, `MUF_STORE_PASSWORD`, dan bila berbeda `MUF_KEY_PASSWORD`, lalu jalankan tanpa `-Unsigned`. Tidak ada keystore atau kata sandi bawaan dalam paket.

Dashboard/add-on:

```sh
cd web
pnpm install --frozen-lockfile
pnpm check
pnpm dev
```

Node.js 22+ dan pnpm 10. Petunjuk integrasi tersedia pada `web/README.md`. Integrasi nyata perlu kalibrasi terhadap halaman tujuan; penataan ini tidak menjalankan inquiry, login, atau transaksi. Perhitungan aplikasi merupakan indikasi, bukan konfirmasi pembayaran resmi. Pengguna tetap memulai pemeriksaan secara eksplisit.

## Rilis dan repositori

Rilis lokal yang tersedia berada dalam `releases/`; cocokkan SHA-256 pada `releases/SHA256SUMS.txt`. Rilis diabaikan oleh Git agar diunggah terpisah melalui GitHub Releases.

Gunakan folder ini sebagai root repositori baru. Kredensial, riwayat Git lama, data pribadi, cache, rancangan agent, dan draft tidak disertakan. Metadata yang masih bertanda `REPLACE_WITH_...` perlu diisi sesuai hosting tujuan.

## Pemeriksaan paket sumber — 22 November 2026

Android unsigned build; 2.478 pemeriksaan Java dan 19 pemeriksaan SQLite lulus. Web lint, typecheck, 18 tes, dashboard dan add-on build lulus. APK lokal lolos verifikasi tanda tangan; DEX/resource cocok dengan build sumber.
