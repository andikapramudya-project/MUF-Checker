# Deployment GitHub ke Netlify

Dashboard adalah aplikasi statis tanpa backend.

## 1. Push ke GitHub

```bash
git init -b main
git add .
git commit -m "Build local-first MUF inquiry dashboard and Firefox add-on"
git remote add origin https://github.com/USER/NAMA-REPO.git
git push -u origin main
```

Jangan commit file `.env`, build add-on yang memuat origin privat, atau data konsumen.

## 2. Buat site Netlify

1. Pilih **Add new site > Import an existing project**.
2. Pilih GitHub dan repository ini.
3. Netlify membaca `netlify.toml`:
   - build command: `pnpm build:dashboard`;
   - publish directory: `apps/dashboard/dist`;
   - Node.js 22;
   - SPA fallback ke `index.html`.
4. Deploy site.

Tidak diperlukan Netlify Functions, database, atau analytics.

## 3. Build ulang add-on

Setelah URL production diketahui, build add-on dengan origin Netlify persis:

```powershell
$env:DASHBOARD_ORIGINS='https://nama-situs.netlify.app'
pnpm build:addon
```

Origin dimasukkan ke manifest add-on saat build. Bila domain Netlify berubah atau custom domain ditambahkan, add-on harus dibuild ulang dan ditandatangani ulang.

## 4. Verifikasi production

- Buka dashboard dan pastikan tidak ada banner **MODE SIMULASI**.
- Pastikan kondisi kosong berbunyi **Belum ada hasil pemeriksaan**.
- Pastikan origin lain tidak dapat terhubung ke add-on.
- Pastikan refresh halaman tetap bekerja karena SPA redirect.
- Pastikan riwayat hanya berada di IndexedDB perangkat.

Referensi: [Netlify JavaScript SPAs](https://docs.netlify.com/build/configure-builds/javascript-spas/) dan [Netlify redirects](https://docs.netlify.com/manage/routing/redirects/overview/).
