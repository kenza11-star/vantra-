# Patch aplikasi Vantra: update langsung dari website

Ekstrak ke root project (timpa file lama), lalu build APK dan install SEKALI secara manual.
Setelah itu semua update berikutnya masuk lewat website.

## 6 file yang berubah
- core/updater/GithubUpdates.kt      -> sumber update = https://updated-mu-orcin.vercel.app (GitHub jadi cadangan); unduhan dicek (HTTP, bukan halaman web, tidak terpotong)
- core/updater/UpdateCheckWorker.kt  -> BARU: cek di latar tiap +-15 menit + notifikasi "Vantra update x.y.z"
- ui/screens/update/UpdateSheet.kt   -> rememberUpdateState() sekarang NYATA (dulu kosong, selalu "UpToDate"): cek saat app dibuka/dilanjutkan, lalu tiap 30 detik selama app terbuka
- ui/screens/settings/ProfileScreen.kt -> kartu "Version & about app": muncul "x.y.z is out, tap to update" + kapsul "Update"; ditekan = buka sheet Install
- ui/screens/update/UpdateNotifications.kt -> judul notifikasi "Vantra update x.y.z"
- App.kt                             -> menjadwalkan pengecekan latar

## Alur pengguna
Kamu Send di website -> dalam <=30 detik kartu di app berubah jadi Update (dan notifikasi datang) -> ketuk -> Install here -> persen berjalan -> 100% layar pasang Android muncul otomatis -> versi berganti.

## Syarat Android (wajib)
1. APK yang di-upload harus ditandatangani dengan key YANG SAMA dengan app terpasang.
2. versionCode di APK harus LEBIH BESAR dari yang terpasang, dan versionName harus sama dengan angka "Version" yang kamu ketik di website.
3. Izinkan "Install unknown apps" untuk Vantra saat diminta (sekali).

Jika kamu sudah mengubah file-file di atas di repo selain UpdateServerUrl, jangan timpa buta: gabungkan perubahannya.
