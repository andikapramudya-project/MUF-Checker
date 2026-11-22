# Privasi dan Batasan

## Data yang disimpan

- Dashboard: hasil dan riwayat di IndexedDB perangkat.
- Add-on: checkpoint antrean dan log yang disanitasi di storage lokal Firefox.
- Nomor yang ditampilkan di riwayat disamarkan.
- Nominal hanya boleh disimpan bila pengguna mengaktifkan pengaturannya.

## Data yang tidak dikumpulkan

- cookie dan token;
- password, OTP, atau jawaban CAPTCHA;
- localStorage Tokopedia;
- telemetry dan analytics pihak ketiga;
- data ke Netlify Functions, database, atau server aplikasi.

Menghapus data situs/browser, menggunakan mode privat, atau menghapus add-on dapat menghilangkan data lokal.

## Batas hasil

Status dihitung dari selisih bulan antara periode pilihan dan tanggal jatuh tempo yang tampil:

- jatuh tempo setelah periode: **TERINDIKASI SUDAH BAYAR**;
- bulan sama: **BELUM DAPAT DIPASTIKAN**;
- jatuh tempo sebelum periode: **TERINDIKASI BELUM DIBUKUKAN**.

Perpindahan lebih dari satu bulan memerlukan verifikasi tambahan. Jika tanggal tidak terbaca, aplikasi tidak menyimpulkan pembayaran.

Hasil bukan bukti pembayaran, bukan laporan resmi MUF/Tokopedia, dan dapat dipengaruhi keterlambatan pembukuan, restrukturisasi, pembayaran di muka, perubahan jadwal, atau perubahan halaman.

## Tanggung jawab penggunaan

Gunakan hanya untuk data yang secara sah boleh diakses oleh pengguna. Patuhi ketentuan layanan Tokopedia dan kebijakan organisasi. Jangan mempercepat atau memparalelkan antrean untuk menghindari pembatasan layanan.
