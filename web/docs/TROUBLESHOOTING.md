# Pemecahan Masalah

## Add-on belum terhubung

- Pastikan dashboard dibuka dari origin yang dicantumkan dalam `DASHBOARD_ORIGINS` saat build.
- Origin harus persis dan tidak memakai wildcard.
- Build ulang dan instal ulang add-on setelah URL Netlify berubah.
- Muat ulang dashboard setelah add-on aktif.

## Struktur halaman belum dikenali

Jalankan diagnostics dan ikuti [panduan kalibrasi](TOKOPEDIA_SELECTOR_CALIBRATION.md). Jangan menganggap fixture test sebagai bukti integrasi nyata.

## Tokopedia meminta tindakan pengguna

Antrean sengaja dijeda. Buka tab Tokopedia, selesaikan login, OTP, CAPTCHA, atau verifikasi sendiri, lalu tekan **Lanjutkan**. Add-on tidak dapat dan tidak boleh melewati verifikasi.

## Pemeriksaan dihentikan pada tahap pembayaran

Ini adalah pengaman yang diharapkan. Kembali ke halaman inquiry MUF. Add-on tidak dirancang untuk checkout atau pembayaran.

## Riwayat hilang

Riwayat hanya disimpan lokal. Data dapat hilang setelah penghapusan data situs, reset browser, mode privat, atau perpindahan perangkat.

## Dashboard production menampilkan MODE SIMULASI

Jangan gunakan deployment tersebut. Pastikan `VITE_ENABLE_SIMULATION=false`, hapus cache build, dan deploy ulang. Production build normal tidak menjalankan generator simulasi.

## Build add-on gagal karena origin

Gunakan format:

```text
https://nama-situs.netlify.app
```

Jangan gunakan path, wildcard, atau origin tanpa `https://` untuk production.
