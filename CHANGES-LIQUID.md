# Liquid player style

Style baru **Liquid** di pemilih Style Music Player (geser ke paling kanan, setelah Geometry), menggantikan "Pemutar Lirik" yang dulu ada di Pengaturan.

## Isinya
- Latar: video lagu (video resmi / lagu yang memang video) kalau ada, kalau tidak ada pakai cover. Cover tetap di bawah sampai frame video pertama muncul, lalu cross-dissolve.
- Efek cairan: gambar (video/cover/poster) tetap tajam dan normal; beberapa bercak cairan kental (metaball AGSL, Android 13+) melayang di atasnya dan hanya mengaburkan bagian yang tertutup, ±30% layar, bukan full screen. Di tepi bercak gambar sedikit terbias seperti lensa, ada frost tipis dan garis cahaya halus. Bergerak tiap frame (tanpa langkah 30 fps), loop 100 dtk tanpa patahan. Android 12 ke bawah: bercak frost polos di jalur yang sama.
- Jam serif besar + tanggal italic, cahaya ungu di keempat tepi layar (paling kuat di bawah) + garis cahaya tipis, bernapas pelan.
- Transisi: tiap lagu mulai dari kartu cover kecil yang membesar dan larut ke dalam scene, lalu jam dan lirik muncul.
- Terbuka tegak (portrait); bisa diputar miring dari menu •••. Kunci pengaturan baru (`playerStyleRotated_LIQUID_v2`) supaya tampilan lama yang miring tidak terbawa.
- Kontrol (play, next, menu) hilang sendiri setelah 4 detik, ketuk layar untuk memunculkan lagi. Dobel ketuk = play/pause, geser = ganti lagu.

## Style lirik
Dipilih di **Edit** pada kartu Liquid di selector, atau lewat tombol **Aa** di pojok kiri bawah player:
- Constellation: bintang + silang di tiap kata, benang tipis menyambung ke kata sebelumnya (jaring)
- Kinetic: baris besar di tengah, kata naik satu-satu
- Karaoke: dua baris di bawah, baris yang dinyanyikan terisi cahaya
- Minimal: satu baris, melebur ke baris berikutnya
Lagu tanpa lirik bertimestamp otomatis memakai Constellation.

## File
Baru: `ui/player/liquid/LiquidPlayerStyle.kt`, `ui/player/liquid/LiquidEffects.kt`, `ui/player/studio/LiquidOptions.kt`
Diubah: `ui/player/liquid/LiquidEffects.kt` (shader cairan baru), `ui/player/liquid/LiquidPlayerStyle.kt` (latar + cahaya tepi), `constants/PreferenceKeys.kt`, `ui/player/styles/AlternatePlayerStyles.kt`, `ui/player/studio/PlayerStudioState.kt`, `ui/player/studio/StudioSheets.kt`, `ui/player/BottomSheetPlayer.kt`, `ui/screens/gesture/CustomisationPreviews.kt`, `ui/screens/settings/VantraSettingsScreen.kt`, `ui/menu/SongMenu.kt`
Dihapus dari menu: entri "Pemutar Lirik" di Pengaturan dan di menu lagu. Halaman lamanya (`ConstellationPlayerScreen`) masih ada di kode tapi tidak punya pintu masuk lagi; datanya (`buildGraph`) dipakai style baru.
