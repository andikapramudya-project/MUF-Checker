# Catatan rilis

## 0.8.6 — 23 November 2026

- Kapsul status berwarna pada tabel Hasil dan rincian; **Belum bayar** memakai kuning tua tersendiri, terpisah dari Error (merah) dan Proses/Tunggu (abu). Warna `AMBER` yang sebelumnya sama dengan teks utama kini benar-benar kuning tua, sehingga "Aksesibilitas belum tersambung" tampil sebagai peringatan.
- Kartu Sudah/Belum/Error dapat diketuk untuk memfilter; chip filter menampilkan jumlah (misalnya "Belum 8"); angka kartu menghitung naik 300 ms saat berubah di tab Hasil.
- Panel mengambang: garis progres tipis di tepi bawah dan animasi muncul 200 ms. Hanya presentasi; jadwal dan penjagaan inquiry tidak berubah.
- Tab Periksa: nomor baris yang tidak valid disebut (maks. lima) dan perkiraan sisa waktu muncul setelah dua kontrak selesai.
- Token gerak 100/150/200/300 ms: efek pantul dan skala pada navigasi dihapus, dialog masuk 200 ms ease-out dan keluar 150 ms ease-in. Semua animasi tetap mengikuti setelan animasi Android.
- Pembacaan ulang data saat tidak ada antrean aktif turun dari tiap 0,9 detik menjadi tiap 3 detik. Versi di footer dibaca dari paket aplikasi.
- Menambah 2 pemeriksaan host untuk perkiraan sisa waktu. VersionCode 20/versionName 0.8.6; schema, serializer, logika inquiry, dan sertifikat pembaruan tidak berubah.

## 0.8.5 — 4 November 2026

- Pesan pembatasan cabang, nomor kontrak/pelanggan tidak ditemukan/tidak valid, serta tagihan tidak tersedia disimpan sebagai ERROR kontrak dan dilewati setelah checkpoint. Dua ERROR kontrak berurutan tidak menghentikan antrean; alasan pembatasan tetap eksplisit.
- Menampung kategori pesan singkat dari event Tokopedia yang cocok dengan inquiry aktif setelah gestur diterima Android. Event lama, aplikasi lain, pesan setelah hasil terbuka dan latch kedaluwarsa ditolak. Teks sumber mentah tidak disimpan.
- Batas 10 detik pada pengamatan beruntun form MUF diam dengan kontrak persis cocok, tanpa indikator memuat atau rincian. Loading, form/identitas berubah, root tidak terbaca dan jeda pengamatan lebih dari 2 detik mereset penghitung. Kondisi ini tetap ERROR “Tidak ada respons”, tanpa menyimpulkan kontrak diblokir.
- Penantian hasil/rincian normal tetap 25 detik. Dua kegagalan teknis, login/verifikasi, kesiapan form dan kegagalan penyimpanan tetap menjaga antrean. Tidak mengulang inquiry otomatis atau hasil sukses.
- Menambahkan 70 regresi host dan uji keputusan service/event/checkpoint/native loading Android pada database terisolasi. VersionCode 19/versionName 0.8.5; UI, schema, serializer dan sertifikat pembaruan tetap.

## 0.8.4 — 2 November 2026

- Memperbaiki kolom nomor kontrak/periode/mode yang tetap terkunci sesudah Batal sebelum Mulai. Riwayat persiapan tetap disimpan; pointer antrean dilepas atomik hanya untuk persiapan yang belum pernah dimulai.
- Memulihkan pembatalan lama 0.8.3 saat data dimuat. Antrean yang pernah dimulai, memiliki percobaan atau hasil tetap terlindungi dan dapat dilanjutkan seperti sebelumnya.
- Menambahkan guard ID sesi, penolakan pembatalan kedaluwarsa serta rollback penyimpanan. Schema, serializer, kunci signing, UI dan guard inquiry tidak berubah.
- Menambahkan 14 regresi host serta uji service/form/database Android yang terisolasi. Uji posisi gulir kini menunggu render pencarian yang didebounce dan layout posisi yang dituju sebelum memeriksa refresh.
- VersionCode 18/versionName 0.8.4 konsisten pada build, Gradle, footer dan diagnosis. Validasi serta batas cakupan dicatat dalam TESTING.md.

## 0.8.3 — 1 November 2026

- Periode lebih proporsional: bulan/tahun 14 sp, label 11 sp, tombol Ubah 13 sp dan padding vertikal lebih rapat; luas sentuh tetap minimal 48 dp.
- Tabel hasil mempertahankan nama/kontrak satu baris dengan lebar kolom berdasarkan teks lengkap. Geser horizontal menyelaraskan kepala dan semua baris; ListView, pencarian/filter, scroll vertikal dan daur ulang tetap dipertahankan. Posisi horizontal disimpan dan dibatasi sesuai ruang yang tersedia.
- Rincian memakai judul lebih kecil, chip status, kelompok identitas netral serta pasangan tanggal/periode dan angsuran/cicilan. Nilai dapat dipilih, teks panjang/ERROR tetap lengkap, konten bergulir dan tombol Diagnosis/Tutup tetap tersedia.
- Parsing rincian dipindahkan ke pekerja AppRepository; callback diagnosis tetap memakai ID sesi asal. Tidak ada perubahan logika inquiry, schema, serializer, dependency atau kunci signing.
- VersionCode 17/versionName 0.8.3 konsisten pada Gradle, build mandiri, footer dan diagnosis. Cakupan uji dicatat dalam TESTING.md.

## 0.8.2 — 1 November 2026

- Header MUF CHECKER diperbesar ke 14 sp; judul halaman Periksa, Hasil dan Pengaturan dihapus sesuai revisi pengguna.
- Periode ditempatkan pada bidang lembut dengan label dan bulan/tahun 16 sp; Aktivitas kosong menjadi satu pesan tanpa progres atau petunjuk Jeda/STOP berulang. Kontrol antrean tetap mengikuti keadaan yang sama.
- Hasil menjadi empat kolom No/Kontrak/Nama/Status dengan grid tipis, baris daur ulang dan teks lengkap yang membungkus. Bayar/Belum adalah label ringkas; Error, Proses dan Tunggu tetap terpisah. Jatuh tempo dipertahankan pada rincian.
- Menghapus tanda v pada mode dan diagnosis; pemilih sesi diagnosis dipindahkan ke kanan judul.
- Navigasi kapsul dengan indikator bergeser dan overshoot ringan, skala konten tab serta transisi halaman native yang mengikuti pengaturan Android. Tidak mengubah jadwal atau tindakan pemeriksaan.
- Panel floating memakai renderer presentasi terpisah, permukaan glass translusen dan tombol menyatu; guard kesiapan, callback, window flags, lokasi dan perilaku antrean tetap.
- Ikon dokumen/centang orisinal dengan foreground/background adaptif dan varian monochrome.
- VersionCode 16/versionName 0.8.2; sertifikat, schema dan format snapshot tetap. Validasi host, emulator dan pemeriksaan UI Poco fisik dicatat terpisah di TESTING.md.

## 0.8.1 — 1 November 2026

- Menghapus subjudul halaman; menyelaraskan periode/Ubah; mengembalikan input sans sistem dan meringkas petunjuk serta Aktivitas.
- Ikon vektor orisinal pada navigasi dan menu sesi; tanggal/jam tetap membuka pemilih sesi tanpa chevron. Ringkasan memakai durasi `3m 35d` tanpa periode tambahan.
- Baris hasil daur ulang dengan kolom nomor urut, kontrak/nama sejajar, tanggal polos dan kapsul status hijau/abu netral/merah. Tata letak mengikuti ruang baris/font; identitas panjang tetap membungkus.
- Rincian sukses tanpa penjelasan periode berulang; alasan ERROR/pembatasan cabang tetap terlihat. Pengaturan memakai empat kelompok dan footer dialog adaptif.
- Versi dinaikkan ke versionCode 15/versionName 0.8.1 pada build, footer dan diagnosis. Build menolak kunci signing yang hilang; tidak membuat pengganti.
- Prinsip hierarki/navigasi/pengelompokan terinspirasi BitChord; tanpa menyalin kode, font, aset atau menambah dependency.

Validasi dan keterbatasan: lihat bagian 0.8.1 di TESTING.md. Logika pemeriksaan, format snapshot dan schema database tidak berubah.

## 0.8.0 — 27 Oktober 2026

- Tampilan utilitas native dengan warna netral, aksen hijau, kontrol 48 dp, hirarki teks dan progres jumlah/persen yang jelas.
- Hasil berpindah ke ListView native; kontrak/nama/status/tanggal membungkus, rincian label-di-atas-nilai, sesi terpilih diberi penanda.
- Parsing/pemformatan/filter daftar di pekerja AppRepository, debounce 180 ms dan penolakan callback pencarian lama. Scroll anchor tetap saat refresh dan baris menyimpan ID sesi asal.
- Pilihan filter/pencarian dipertahankan ketika Activity dibuat ulang. Animasi native singkat mengikuti sistem; tidak ada animasi antrean berulang.
- Menambahkan DESIGN.md, AGENTS.md, analisis agregat diagnosis 79 kontrak, dan instrumentasi Android terpisah dengan data sintetis.
- Timing/guard aksesibilitas, status, antrean, schema v2, package dan sertifikat pembaruan tetap dari 0.7.2. Service dan laporan hanya mendapat pembaruan teks versi.

Validasi: 2.388 pemeriksaan Java, 19 SQLite host, build/signature/alignment. Pembaruan 0.7.2 → 0.8.0 dan UI data sintetis diuji pada emulator Android 16; bukti serta batas pengujian dicatat di TESTING.md. Belum menjalankan inquiry Tokopedia pada Poco fisik atau mengukur tambahan percepatan versi ini.

Repositori sebelumnya berhenti di 0.5.0; publikasi kode 0.8.0 turut menyertakan perkembangan lokal 0.6.0–0.7.2 yang dicatat di bawah.

## 0.7.2 — 27 Oktober 2026 (prarilis lokal)

- Pembacaan kolom fokus khusus mode halaman siap setelah aksi pengisian diterima. Setelah minimal 250 ms, kolom dicari kembali dari root Tokopedia dan di-refresh. Keyboard hanya boleh ditutup bila nomor persis, paket, jendela, ID, kelas, posisi, fokus dan kemampuan kolom sesuai target yang sebelumnya diverifikasi.
- Jalur ringan tidak dapat menekan Bayar atau menyimpan hasil. Setelah keyboard ditutup, aplikasi membaca ulang seluruh form dan memeriksa penyedia MUF, nomor serta target Bayar. Jika kolom tidak sesuai/hilang, fokus berpindah, IME tidak tampil, atau pembacaan gagal, jalur kembali ke verifikasi lengkap dengan waktu stabilisasi input semula 500 ms. Penutupan keyboard yang ditolak Android menjeda antrean.
- Properti node dan label rincian dipakai ulang di dalam satu snapshot, mengurangi pembacaan berulang. Tidak menyimpan node aksesibilitas lintas layar atau kontrak. Target ringan dihapus saat pergantian kontrak, pengisian baru, penghentian dan sesudah pemeriksaan ringan selesai.
- Diagnosis menambahkan bagian VERIFY_INPUT_LIGHT dengan keputusan dan durasi pembacaan kolom, tanpa nomor/nama/nominal. Interval polling umum, animasi/UI, kestabilan hasil 400 ms, batas dua pengisian, satu inquiry dan transaksi hasil sebelum pindah tetap sama.
- Batas 100 kontrak serta schema database versi 2 dipertahankan. Sesi dari serializer 0.7.0 (50 kontrak) dan 0.7.1 (100 kontrak) diuji untuk hasil, mask, waktu dan kelanjutan antrean.

Validasi: 2.376 pemeriksaan Java dan 19 SQLite host. APK versionCode 13/versionName 0.7.2, sertifikat pembaruan sama dengan 0.7.1, signature v2/v3, zipalign dan SHA-256 diperiksa. Tidak ada perangkat Android terhubung; optimasi ini belum diukur langsung pada Tokopedia dan tidak menjanjikan persentase percepatan.

## 0.7.1 — 27 Oktober 2026 (prarilis lokal)

- Batas 100 kontrak berbeda per antrean. Validasi, pemulihan snapshot dan label memakai batas yang sama; nol awal, deduplikasi dan urutan tetap dipertahankan. Tidak ada jeda wajib setiap 25 kontrak.
- Mode halaman siap mengurangi jeda awal perpindahan berikutnya dari 700 menjadi 300 ms dan melewati fokus tambahan hanya ketika kolom kontrak sudah fokus pada form MUF terverifikasi. Mode tautan mempertahankan jeda sebelumnya. Dua pembacaan stabil minimal 400 ms, dua percobaan isi, satu inquiry dan simpan sebelum beralih tetap berlaku.
- Pembacaan durasi sesi mengelompokkan properti satu kali, menggantikan pemindaian seluruh properti untuk setiap baris. Format snapshot/schema database tetap sama.
- Panel setinggi sekitar 56 dp, dibanding sebelumnya 64 dp, dengan gradien statis beropasitas 76–80%, garis halus dan target sentuh 48 dp. Teks/tombol tetap jelas tanpa blur atau animasi latar berulang.
- Tombol native diratakan agar bayangan tidak terpotong; tombol diagnosis mengikuti tinggi isi dengan minimum 48 dp. Pilihan sesi dua baris memakai ListView yang mendaur ulang baris, pembatas abu-abu dan penanda pilihan.
- Label Periksa menjadi “kontrak berbeda”. Hasil mempertahankan dua kolom, menambahkan nomor urut dengan lebar tetap serta pembatas halus, dan merapatkan tinggi minimum baris menjadi 72 dp. Nama bertanda bintang, tanggal dan hasil tidak diubah.
- Paket dan sertifikat pembaruan sama dengan 0.7.0. Database tetap versi 2, tanpa penghapusan atau migrasi baru; sesi lama tetap dibaca.

Validasi: 2.213 pemeriksaan Java, 17 SQLite host, APK versionCode 12/versionName 0.7.1, signature v2/v3, zipalign dan checksum. Fixture asli dari serializer 0.7.0 berisi 50 kontrak sintetis berhasil dibaca, dilanjutkan dan dicoba ulang tanpa kehilangan hasil sukses. Pratinjau HTML diperiksa untuk label 100, panel 56/48 px, pembatas dan overflow pada tiga layar 390 px. Tidak ada perangkat Android terhubung; tampilan native dan tambahan percepatan nyata perlu diuji di ponsel.

## 0.7.0 — 27 Oktober 2026 (prarilis lokal)

- Rancangan Figma untuk Periksa, Hasil, Pengaturan, lima status panel dan dialog rincian; dialog Periode/Sesi/Hapus dilengkapi lokal karena kuota MCP Starter. Seluruh data rancangan sintetis.
- UI native mengikuti palet/radius/tipografi rancangan: mode di Pengaturan, ringkasan terpisah, tabel nomor/nama dan status/tanggal, dialog label/nilai dan konfirmasi ringkas.
- Panel Tokopedia satu baris dengan status singkat serta Mulai/Batal atau Jeda/STOP. Diagnosis mempertahankan rincian teknis. Pemberitahuan akhir singkat.
- Pembacaan awal setelah keyboard/inquiry/rincian dan penjadwalan sesuai akhir jeda fokus/input, dengan guard tindakan tetap berlaku. Pemuatan rincian yang belum lengkap diberi jendela semula, tanpa gulir dini. Rute tautan mempertahankan jeda lama.
- Guard hasil dua pembacaan terverifikasi berjarak minimal 400 ms dapat diuji terpisah; isi berubah, identitas/tanggal gagal atau layar hilang mereset pengamatan.
- Pencarian berulang di-cache dalam satu snapshot; pembaruan durasi tidak membangun ulang tabel. Penggambaran tabel ditunda sampai halaman Hasil terlihat.
- Transisi native halaman 140 ms dan dialog 150/100 ms mengikuti pengaturan sistem. Tanpa library animasi tambahan, perubahan izin, schema database atau aturan status pembayaran.

Validasi: 1.132 pemeriksaan Java dan 13 SQLite host; kompilasi/signature/alignment/checksum APK dan sertifikat pembaruan. Pratinjau lokal diuji untuk pencarian, filter, empat dialog dan ukuran ponsel. Rendering Android, kelancaran animasi dan percepatan aktual masih perlu uji perangkat.

## 0.6.0 — 27 Oktober 2026 (prarilis lokal)

- Mode halaman Tokopedia siap: antrean PREPARED, tombol Mulai/Batal mengambang di Tokopedia, dan pemeriksaan ulang form saat Mulai ditekan. Tidak membuka tautan atau menjalankan inquiry selama persiapan.
- Mode disimpan per sesi, termasuk setelah interupsi dan Coba ulang ERROR. Melanjutkan mode halaman siap memerlukan panel dan Mulai lagi; hasil sukses dipertahankan.
- Penggantian seluruh nomor dengan verifikasi persis, penyedia MUF, target Bayar unik (mengabaikan + Bayar Lainnya), satu ketukan inquiry, hasil stabil dan transaksi sebelum berpindah nomor tetap berlaku.
- Satu Kembali untuk kembali ke form; mode baru menjeda ketika form tidak pulih dan tidak membuka tautan lama sebagai pemulihan. Mode tautan masih dapat dipilih untuk sesi baru.
- Diagnosis memisahkan waktu persiapan awal dan waktu aktif, menandai jalur sebenarnya, serta mencatat durasi pembacaan layar. Diagnosis form persiapan yang gagal siap dapat diekspor setelah dibatalkan.
- Pesan pengisian membedakan aksi yang diterima Android dari nomor yang telah diverifikasi tampil. Mode halaman siap tidak menutup keyboard di antara fokus awal dan aksi pengisian.
- UI/animasi Figma belum diubah. APK memakai identitas paket dan kunci pembaruan yang sama.

Validasi otomatis: lihat TESTING.md. Belum ada perangkat Android terhubung; keberhasilan dan percepatan mode baru pada Tokopedia perlu diuji pengguna. Tidak ada publikasi ke GitHub/layanan eksternal pada pembaruan ini.

## 0.5.0 — 24 Oktober 2026

- Batas tetap 50 kontrak unik, nama tetap berbintang sesuai sumber.
- Event aksesibilitas terbatasi dengan polling cadangan; fokus input distabilkan dan pemulihan form dihitung sejak Kembali dijalankan.
- Pesan eksplisit pembatasan pembayaran/hubungi cabang dicatat ERROR dan tidak menambah hitungan kegagalan teknis berturut-turut.
- Penjagaan layar dengan timeout, pemeriksaan foreground Tokopedia dan pelepasan saat berhenti; tombol daya tetap menjeda antrean.
- UI tanpa judul halaman berulang atau label PRIBADI; periode dan Ubah satu baris; pemilih sesi dan menu penghapusan ringkas.
- Riwayat SQLite per sesi, migrasi transaksi dari v0.4.0, serta diagnosis terpisah untuk nomor yang sama di sesi berbeda.
- Diagnosis seluruh sesi dapat dilihat, disalin dan diekspor .txt melalui pemilih lokasi Android.
- Durasi kumulatif per kontrak dan tahap, rata-rata/median/tercepat/terlama serta jumlah pemulihan tercatat tanpa data pelanggan.
- Pembacaan riwayat dan ekspor di pekerja I/O; callback mengikuti lifecycle, koneksi database bersama, pembaruan tabel dibatasi perubahan data.
- Build APK tetap memakai sertifikat pembaruan yang sama. Tanpa library animasi tambahan.

Validasi: tes Java sintetis, tes schema/transaksi SQLite host, kompilasi APK dan verifikasi signing. Tidak ada perangkat Android terhubung; wake lock HyperOS, UI, migrasi Android, toast dan percepatan nyata masih memerlukan uji pengguna. Release ditandai prarilis.

## 0.4.0 — 23 Oktober 2026

- Kapasitas 1–50 kontrak, nama berbintang, navigasi tiga bagian, pencarian/filter dan checkpoint SQLite.
- Pengguna menguji 30 kontrak: 29 berhasil, satu error. Diagnosis yang dikirim memuat rincian kontrak terakhir, tanpa durasi seluruh antrean atau penyebab error tersebut.
