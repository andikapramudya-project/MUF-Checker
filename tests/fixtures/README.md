# Fixture sesi sintetis

`v070-ready-50.properties` dibuat dengan kelas BulkQueue hasil build 0.7.0 yang masih memiliki batas 50, sebelum build 0.7.1. Seluruh nomor dan nama merupakan contoh. ID serta waktu dibuat tetap setelah serialisasi agar fixture dapat diulang.

Berisi 25 hasil sukses, satu ERROR, satu kontrak dijeda saat pengisian, serta 23 kontrak menunggu. Tes membaca format asli tersebut dengan kode baru, memeriksa hasil, mask, waktu dan identitas sesi, kemudian melanjutkan dan mencoba ulang ERROR tanpa mengulang hasil sukses.

`v071-ready-100.properties` dibuat dengan kelas build 0.7.1 yang dipertahankan sebelum build 0.7.2. Berisi 100 kontrak sintetis: 41 hasil sukses, kontrak ke-42 dijeda saat pengisian, serta sisanya menunggu. Metadata dibuat tetap. Tes mempertahankan hasil, waktu dan mask, melanjutkan kontrak ke-42 setelah Mulai eksplisit, lalu menyimpan hasil baru bersama seluruh hasil lama.
