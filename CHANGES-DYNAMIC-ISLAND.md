# Dynamic Island di Vantra

- Menu baru **Dynamic Island** di Pengaturan (di bawah Modul): saklar, status, daftar izin dengan tombol Izinkan, dan tombol ke pengaturan lengkap.
- Kode ada di `app/src/main/kotlin/com/vantra/music/island` (paket `com.vantra.music.island`); satu aplikasi, satu nama: Vantra.
- Izin & komponen ditambahkan di AndroidManifest (aksesibilitas, akses notifikasi, layanan latar, tile).
- Dihapus: pembaruan dari repo lain, tautan/kredit luar, ikon peluncur kedua.
- `build.gradle.kts`: `aidl = true` dan dependensi Shizuku (mode wajah opsional).
