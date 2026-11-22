# Instalasi Firefox Android

## Batas kompatibilitas

Add-on menggunakan Manifest V2 karena Firefox Android belum mendukung background service worker Manifest V3 secara penuh. Add-on dirancang agar juga dapat diuji di Firefox desktop.

Instalasi permanen pada Firefox rilis memerlukan paket XPI yang ditandatangani Mozilla. Folder `dist` atau paket unsigned hanya cocok untuk pengembangan.

## Build dengan origin dashboard

PowerShell:

```powershell
$env:DASHBOARD_ORIGINS='https://nama-situs.netlify.app'
pnpm build:addon
```

Beberapa origin dapat dipisahkan dengan koma. Jangan menggunakan `*`. Origin harus persis, misalnya `https://nama-situs.netlify.app`, tanpa path tambahan.

## Uji sementara melalui komputer

1. Aktifkan Developer options dan USB debugging pada Android.
2. Sambungkan perangkat, lalu pastikan ADB mengenalinya.
3. Jalankan Firefox Android setidaknya satu kali.
4. Dari folder project, gunakan `web-ext` dengan target Firefox Android dan source directory `apps/firefox-addon/dist`.
5. Pilih aplikasi Firefox yang benar ketika diminta.

Instalasi sementara hilang ketika sesi debugging berakhir.

## Instalasi XPI bertanda tangan

1. Kirim source add-on ke Mozilla Add-ons untuk proses signing, atau gunakan alur self-distribution Mozilla.
2. Unduh XPI bertanda tangan ke ponsel.
3. Pada Firefox Android buka **Settings > About Firefox**.
4. Ketuk logo Firefox lima kali untuk membuka menu pengembang.
5. Kembali ke Settings, pilih **Install Extension from File**, lalu pilih XPI.

Menu dan dukungan dapat berubah menurut versi/kanal Firefox Android.

## Setelah terpasang

1. Login sendiri ke Tokopedia pada Firefox.
2. Buka dashboard Netlify pada origin yang diizinkan.
3. Pastikan indikator add-on menyatakan tersambung.
4. Lakukan diagnostics sebelum memakai nomor kontrak nyata.
5. Jika login, CAPTCHA, OTP, verifikasi perangkat, atau pembatasan muncul, selesaikan sendiri pada tab Tokopedia lalu tekan **Lanjutkan**.

Add-on tidak mencoba melewati verifikasi dan tidak boleh melanjutkan ke pembayaran.

## Referensi resmi

- [Developing extensions for Firefox for Android](https://extensionworkshop.com/documentation/develop/developing-extensions-for-firefox-for-android/)
- [Install self-distributed extensions](https://extensionworkshop.com/documentation/publish/install-self-distributed/)
