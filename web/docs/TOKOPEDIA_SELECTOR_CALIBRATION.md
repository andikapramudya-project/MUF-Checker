# Kalibrasi Selector Tokopedia

## Status saat ini

Page adapter belum diuji langsung pada DOM Tokopedia terbaru. Integrasi harus dianggap **memerlukan kalibrasi langsung**, bukan siap produksi.

Seluruh deteksi halaman dipusatkan di:

```text
apps/firefox-addon/src/content/page-adapter.ts
```

## Urutan kalibrasi aman

1. Login sendiri ke Tokopedia.
2. Buka halaman inquiry MUF:
   `https://www.tokopedia.com/angsuran/mandiri-utama-finance`
3. Buka halaman diagnostics add-on.
4. Periksa indikator:
   - field nomor kontrak;
   - tombol inquiry;
   - loading;
   - wadah hasil;
   - tanggal jatuh tempo;
   - cicilan;
   - kontrak tidak ditemukan;
   - login/verifikasi;
   - tahap pembayaran.
5. Cocokkan berdasarkan label, role, `aria-label`, `name`, dan teks stabil.
6. Gunakan selector CSS stabil hanya sebagai fallback.
7. Jangan memakai class acak hasil build.
8. Uji dengan akun dan nomor kontrak yang pengguna memang berhak akses.

Diagnostics hanya melaporkan keberadaan struktur dan URL yang disanitasi. Jangan menambahkan dump HTML, localStorage Tokopedia, header jaringan, cookie, token, OTP, nomor kontrak lengkap, atau nama lengkap.

## Kriteria lolos

- Satu tab digunakan ulang.
- Input lama dibersihkan.
- Tombol yang ditekan berada dalam allowlist inquiry.
- Hasil lama tidak dianggap hasil baru.
- Tanggal Indonesia terbaca dengan tahun yang benar.
- Login/CAPTCHA/verifikasi membuat antrean berhenti.
- Kata atau URL tahap pembayaran membuat antrean berhenti.
- Tidak ada tombol **Bayar**, checkout, metode pembayaran, atau konfirmasi pembayaran yang pernah ditekan.

## Bila struktur berubah

Kembalikan status **STRUKTUR HALAMAN BERUBAH** dan hentikan antrean. Jangan menebak data atau memperlebar selector hingga dapat menekan elemen yang tidak jelas.
