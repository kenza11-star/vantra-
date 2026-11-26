# Patch app: update lewat Vercel
Ekstrak ke root project (timpa file lama). Lalu:
1. Buka `core/updater/GithubUpdates.kt`, ganti `UpdateServerUrl` dengan alamat Vercel-mu.
2. Naikkan `versionCode` & `versionName` tiap kirim update, tanda tangani APK dengan key yang SAMA.
Alur pengguna: notifikasi "Vantra update x.y.z" → About app → Install (persen berjalan) → 100% otomatis muncul layar pasang → versi berganti.
