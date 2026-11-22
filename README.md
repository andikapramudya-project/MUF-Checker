<h1 align="center">MUF Checker</h1>

<p align="center">Pemeriksaan indikasi status angsuran MUF melalui tampilan Tokopedia.</p>

<p align="center">
  <img alt="Versi" src="https://img.shields.io/badge/versi-0.8.5-1b7f5a?style=flat-square" />
  <img alt="Platform" src="https://img.shields.io/badge/Android-8.0%2B-3ddc84?style=flat-square" />
  <img alt="Bahasa" src="https://img.shields.io/badge/Java-native-b07219?style=flat-square" />
  <img alt="Data" src="https://img.shields.io/badge/data-lokal-555?style=flat-square" />
  <img alt="Kapasitas" src="https://img.shields.io/badge/maks.-100%20kontrak%2Fsesi-555?style=flat-square" />
</p>

<p align="center">
  <a href="#pemasangan">Pemasangan</a> ·
  <a href="#cara-penggunaan">Penggunaan</a> ·
  <a href="#arti-hasil">Arti hasil</a> ·
  <a href="#pengembangan">Pengembangan</a> ·
  <a href="CHANGELOG.md">Catatan rilis</a> ·
  <a href="PRIVACY.md">Privasi</a>
</p>

---

## Ringkasan

MUF Checker membantu memeriksa banyak nomor kontrak MUF sekaligus. Aplikasi bekerja melalui layanan Aksesibilitas Android pada aplikasi Tokopedia milik pengguna sendiri, membaca tanggal jatuh tempo yang tampil, lalu menyimpulkan **indikasi** apakah angsuran periode terpilih sudah dibayar. Hasil disimpan di perangkat dan dapat dicari, difilter, dan diekspor sebagai diagnosis.

> Hasil adalah indikasi operasional, **bukan** bukti transaksi atau konfirmasi pembayaran resmi dari MUF maupun Tokopedia. Proyek ini bukan produk resmi keduanya.

## Fitur utama

- **Antrean hingga 100 kontrak** per sesi; nol di depan dipertahankan dan duplikat diabaikan.
- **Tombol Mulai eksplisit.** Antrean tidak mengisi, mengetuk, atau menggulir sebelum pengguna menekan Mulai pada panel.
- **Jeda, STOP, dan lanjut.** Kontrak yang belum selesai kembali menunggu; hasil sukses tidak diulang.
- **Tabel hasil native** (No · Kontrak · Nama · Status) dengan pencarian, filter status, dan rincian per kontrak.
- **Penyimpanan lokal** dengan transaksi atomik. Hasil disimpan sebelum antrean berpindah ke kontrak berikutnya.
- **Penanganan ERROR yang jelas.** Pesan seperti pembatasan cabang atau kontrak tidak ditemukan dicatat apa adanya, tanpa dianggap lunas maupun belum bayar.
- **Ekspor diagnosis `.txt`** tanpa nomor kontrak, nama, nominal, maupun result JSON.

## Isi repositori

| Direktori | Isi |
| --- | --- |
| `app/` | Aplikasi Android native 0.8.5 (`id.local.mufchecker`). |
| `web/` | Dashboard PWA React dan add-on Firefox (modul parser dan antrean, versi 2.0.0). |
| `tests/` | Pengujian Java/host dan instrumentasi Android dengan data sintetis. |
| `scripts/` | Skrip build Android dan APK smoke test. |

> Dashboard/add-on di `web/` masih memerlukan kalibrasi langsung terhadap halaman Tokopedia. Panduannya ada di [`web/README.md`](web/README.md).

## Persyaratan

- Perangkat Android 8.0 (API 26) atau lebih baru.
- Akun Tokopedia milik sendiri yang sudah login. Aplikasi tidak mengisi sandi, PIN, atau OTP.
- Layanan Aksesibilitas MUF Checker diaktifkan.

## Pemasangan

APK tidak disimpan di Git. APK didistribusikan melalui halaman **Releases** repositori ini, dan checksum SHA-256 dicatat pada `SHA256SUMS.txt`.

- Pasang sebagai **pembaruan tanpa mencopot** versi lama.
- Pembaruan hanya berhasil bila ditandatangani dengan kunci yang sama dengan versi terpasang. Build unsigned tidak dapat dipasang sebagai pembaruan.

## Cara penggunaan

1. Login Tokopedia, lalu aktifkan Aksesibilitas MUF Checker.
2. Pada **Periksa**, pilih bulan dan tahun, lalu tempel satu kontrak per baris (8–20 angka).
3. Tekan **Siapkan pemeriksaan**. Aplikasi beralih ke Tokopedia; buka *Top-up & Tagihan → Angsuran Kredit → Mandiri Utama Finance*.
4. Setelah form terverifikasi, tekan **Mulai** pada panel mengambang. Biarkan Tokopedia tetap di depan selama pemeriksaan.
5. Buka **Hasil** untuk memilih sesi, mencari kontrak atau nama, memfilter status, dan membuka rincian.

<details>
<summary>Kontrol antrean dan pengaturan</summary>

- **Jeda** menyelesaikan kontrak aktif, lalu berhenti.
- **STOP** berhenti segera; kontrak yang belum selesai kembali menunggu.
- **Lanjutkan** menyiapkan panel untuk baris yang belum selesai.
- **Coba ulang ERROR** adalah tindakan eksplisit pengguna; tidak ada inquiry ulang otomatis.
- **Batal sebelum Mulai** mengembalikan formulir ke keadaan dapat diedit dan menyimpan riwayat persiapan.
- Antrean yang sudah mulai diperiksa tidak dapat ditimpa sesi baru.
- **Pengaturan:** mode bawaan halaman siap, mode tautan MUF, penjagaan layar, serta lihat/salin/ekspor diagnosis `.txt` melalui pemilih lokasi Android. Ekspor sesi aktif memerlukan Jeda. Sesi dapat dihapus melalui menu ⋮; sesi aktif dilindungi.

Nama berbintang disimpan sesuai sumber; aplikasi tidak menebak karakter yang tersembunyi.
</details>

## Arti hasil

| Jatuh tempo terhadap periode terpilih | Status |
| --- | --- |
| Setelah periode | ✓ SUDAH BAYAR |
| Sama dengan atau sebelum periode | ○ BELUM BAYAR |
| Inquiry, tanggal, atau identitas tidak terverifikasi | ! ERROR |
| Pesan eksplisit pembayaran dibatasi / hubungi cabang | ! ERROR — pembatasan cabang |

`Bayar` pada tabel adalah label ringkas untuk indikasi *Sudah bayar*. Status didasarkan pada asumsi bahwa Jatuh Tempo mewakili angsuran berikutnya yang belum diselesaikan. Tanggal sumber tidak digeser, dan angsuran berasal dari Tagihan, di luar biaya admin.

## Keandalan

- Penyedia MUF dan nomor kontrak diverifikasi persis sebelum satu ketukan *Bayar*.
- Batas dua pengisian, satu inquiry, kecocokan identitas, label Jatuh Tempo, dan dua pembacaan stabil minimal 400 ms.
- Penantian hasil atau rincian dibatasi 25 detik; form diam setelah inquiry dibatasi 10 detik pengamatan dan dicatat sebagai ERROR "Tidak ada respons".
- Login atau verifikasi manual, layar padam, layanan terputus, dan dua kegagalan teknis berturut-turut menjeda antrean.

## Privasi

- Data berada di penyimpanan privat perangkat; pencadangan Android dinonaktifkan.
- Tidak ada izin `INTERNET`, server, iklan, analitik, OCR, maupun API internal MUF/Tokopedia. Komunikasi inquiry dilakukan oleh Tokopedia sendiri.
- Aplikasi tidak memilih metode pembayaran dan tidak mengonfirmasi pembayaran.
- Gunakan hanya nomor kontrak yang Anda berwenang periksa.

Rincian lengkap: [PRIVACY.md](PRIVACY.md).

## Pengembangan

**Android** (PowerShell 7, JDK 17+, Android SDK Platform 36, Build Tools 36.0.0):

```powershell
.\scripts\build.ps1 -Unsigned
```

<details>
<summary>Build bertanda tangan, pengujian, dan instrumentasi</summary>

Untuk pembaruan yang dapat dipasang di atas versi lama, gunakan kunci asli yang disimpan di luar repositori. Atur `MUF_KEYSTORE`, `MUF_STORE_PASSWORD`, dan bila perlu `MUF_KEY_ALIAS` (bawaan `prototype`), lalu jalankan `.\scripts\build.ps1` tanpa `-Unsigned`. Build berhenti bila kunci tidak ditemukan dan tidak membuat kunci pengganti. Keystore, kredensial, dan hasil pemeriksaan tidak boleh di-commit.

Build menjalankan **2.478 pemeriksaan Java**, kompilasi resource dan Java, dex, zipalign, signing, verifikasi signature, serta SHA-256. Tes SQLite host memuat **19 pemeriksaan** transaksi dan migrasi. Angka ini tercatat pada 22 November 2026.

Instrumentasi Android dibangun terpisah dan tidak menjadi bagian APK produk maupun menjalankan inquiry:

```powershell
.\scripts\build-android-smoke.ps1
```

Seed hanya boleh dijalankan pada emulator dengan instalasi kosong. Sumbernya ada di `tests/android/UiSmokeInstrumentation.java`.
</details>

**Dashboard dan add-on Firefox** (Node.js 22+, pnpm 10):

```sh
cd web
pnpm install --frozen-lockfile
pnpm check     # lint, typecheck, test, build
pnpm dev
```

## Batas validasi

Pengujian emulator dan UI (termasuk Poco X7 5G/HyperOS) tidak menggantikan validasi inquiry Tokopedia langsung. Setelah memasang pembaruan, cocokkan lebih dulu lima kontrak yang diketahui, lalu uji antrean yang lebih besar, Jeda/STOP/lanjut, dan pesan pembatasan cabang.

## Dokumentasi

| Dokumen | Isi |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | Riwayat perubahan per versi |
| [PRIVACY.md](PRIVACY.md) | Data yang disimpan dan yang tidak |
| [web/README.md](web/README.md) | Dashboard dan add-on Firefox |
| [web/docs/](web/docs) | Instalasi, deployment, kalibrasi selector, pemecahan masalah |

## Terbaru: 0.8.5

ERROR kontrak yang terbaca jelas kini dilewati setelah checkpoint tersimpan, form MUF yang diam dibatasi 10 detik pengamatan, dan pesan singkat dari event aksesibilitas ditampung sebagai kategori tetap. Detail lengkap ada di [CHANGELOG.md](CHANGELOG.md).
