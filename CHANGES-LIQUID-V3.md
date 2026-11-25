# Liquid v3
- Cairan: 6 -> 3 bercak, lebih kecil, gerak lebih lambat (kental), tepi lebih lembut. Hanya ±15-20% layar, bukan full screen.
- Blur: 20 tap (dari 24), radius 18dp, lensa & frost lebih tipis. Lebih ringan di GPU -> frame rate display (60/120) terjaga.
- Video: dibatasi 1080p di Liquid (param baru `autoCap` di YouTubeVideoBackground). Cover: FilterQuality.High, zoom/drift dikurangi (1.05 -> 1.02) supaya tetap tajam.
- Cahaya tepi: tebal ±8% bawah / ±4.5% samping / ±3.5% atas (dulu 20% / 13% / 8%), alpha dikurangi, garis tepi 3dp -> 1dp. Warna dari cover (LocalArtworkColor), transisi halus 0.9 dtk saat ganti lagu.
Salin 3 file ke path aslinya: app/src/main/kotlin/com/vantra/music/ui/player/{liquid/,}
