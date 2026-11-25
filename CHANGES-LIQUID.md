# Liquid player style

Style baru **Liquid** di pemilih Style Music Player (geser ke paling kanan, setelah Geometry), menggantikan "Pemutar Lirik" yang dulu ada di Pengaturan.

## Isinya
- Latar: video lagu (video resmi / lagu yang memang video) kalau ada, kalau tidak ada pakai cover. Cover tetap di bawah sampai frame video pertama muncul, lalu cross-dissolve.
- Efek cairan: blur + gelombang AGSL (Android 13+), blur saja di Android 12, tanpa blur di bawahnya. Blur cover 46dp, blur video 15dp, pindah halus 1,4 detik.
- Jam serif besar + tanggal italic, cahaya ungu di tepi layar, frost putih tipis.
- Transisi: tiap lagu mulai dari kartu cover kecil yang membesar dan larut ke dalam scene, lalu jam dan lirik muncul.
- Terbuka miring (landscape) seperti layar laptop; bisa diputar tegak dari menu •••.
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
Diubah: `constants/PreferenceKeys.kt`, `ui/player/styles/AlternatePlayerStyles.kt`, `ui/player/studio/PlayerStudioState.kt`, `ui/player/studio/StudioSheets.kt`, `ui/player/BottomSheetPlayer.kt`, `ui/screens/gesture/CustomisationPreviews.kt`, `ui/screens/settings/VantraSettingsScreen.kt`, `ui/menu/SongMenu.kt`
Dihapus dari menu: entri "Pemutar Lirik" di Pengaturan dan di menu lagu. Halaman lamanya (`ConstellationPlayerScreen`) masih ada di kode tapi tidak punya pintu masuk lagi; datanya (`buildGraph`) dipakai style baru.
