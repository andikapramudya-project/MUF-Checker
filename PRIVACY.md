# Privasi

MUF Checker membantu inquiry pribadi melalui aksesibilitas Tokopedia.

- Hanya melakukan otomasi pada Tokopedia selama pemeriksaan aktif. Tidak memiliki server, analitik, iklan, atau izin INTERNET. Tokopedia melakukan komunikasi inquiry dengan layanannya sendiri.
- Menyimpan sesi, nomor antrean, hasil, timestamp, durasi dan diagnosis di penyimpanan privat perangkat; pencadangan Android dinonaktifkan.
- Nama ditampilkan sesuai masking sumber. Tidak menebak huruf tersembunyi, mengisi OTP/PIN/sandi, memilih metode, atau mengonfirmasi pembayaran.
- Clipboard input pengguna tidak diganti. Salin diagnosis sesi hanya dilakukan melalui tombol yang ditekan pengguna dan tidak mencakup nomor kontrak, nama atau nominal.
- Ekspor diagnosis .txt memakai pemilih lokasi Android. File memuat seluruh nomor urut, metadata teknis dan durasi sesi; tidak menyertakan result JSON, nomor kontrak, nama, nominal, screenshot atau dump layar. File ekspor di lokasi pilihan pengguna tetap ada sampai pengguna menghapusnya.
- File ekspor sementara berada di cache privat dan dihapus setelah pemilihan selesai/dibatalkan. Pemeriksaan tidak mengunggah file diagnosis ke layanan luar secara otomatis.
- WAKE_LOCK dipakai terbatas untuk penjagaan layar ketika antrean aktif di Tokopedia. Dilepas ketika berhenti, dijeda, layanan terputus atau Tokopedia tidak di depan; tombol daya tetap berfungsi. Tidak meminta izin mengubah pengaturan layar sistem secara permanen.
- Hapus sesi menghapus snapshot dan diagnosis sesi tersebut. Hapus seluruh sesi menghapus seluruh data pemeriksaan lokal. Sesi aktif perlu dihentikan terlebih dahulu.
- Migrasi dari 0.4.0 mempertahankan sesi terakhir yang masih tersedia. Waktu lama yang tidak dicatat tidak dibuat-buat.
- Repositori dan Release hanya berisi kode, dokumentasi, tes sintetis dan APK. Keystore, credential, hasil inquiry serta lampiran pribadi tidak disertakan.

Status pembayaran merupakan indikasi dari jatuh tempo, bukan bukti transaksi. Gunakan hanya nomor kontrak yang Anda berwenang periksa.
