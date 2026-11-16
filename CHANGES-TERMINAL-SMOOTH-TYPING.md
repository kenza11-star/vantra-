# Terminal style: ketikan seperti asli + tampilan lebih halus

**Status build: BELUM dikompilasi / dijalankan** (lingkungan penulisan tanpa Kotlin/Android SDK).
Verifikasi lewat CI atau:

    ./gradlew :app:testFossDebugUnitTest --tests "com.vantra.music.ui.player.terminal.*"
    ./gradlew assembleArm64GmsRelease

## Masalah yang terlihat di video (529654.mp4)
1. Kursor tertinggal 3-6 huruf di belakang teks (caret memakai spring 400/40 yang mengejar teks).
2. Huruf muncul ±30/detik, seragam seperti mesin, lalu berhenti jauh sebelum baris berikutnya.
3. Huruf muncul utuh sekaligus (tanpa transisi), sedangkan posisi player datang bertahap
   (per buffer audio) sehingga huruf datang bergerombol.

## Perubahan
### Ketikan (`TerminalTypingTimeline.kt`)
- Ritme manusia: kecepatan melayang pelan, variasi per tombol, jeda setelah `, ; : . ! ?`,
  jeda kecil di awal kata, sesekali ragu. Deterministik (seek tetap persis sama).
- Durasi ketik mengikuti jarak antar baris lirik (maks 86%), ±17 huruf/detik di Normal
  (sebelumnya ±30 seragam). Ease halus di awal/akhir baris.
- Kode scaffolding sebelum lirik diketik selama lead-in (ease-out), bukan muncul sekaligus.
- API baru, aditif: `TerminalCodeCaret.next` dan `.frac` = huruf yang sedang "ditekan".
  `visibleChars()` / `locate()` tidak berubah signature-nya.
- Konstanta penyetel: `PerUnitMs` (48), `MaxTypingMs`, `EaseMix`, `StrokeUnits`.

### Jam pemutaran (`TerminalSynchronization.kt`, `TerminalPlayerStyle.kt`)
- `TerminalPlayClock`: jam yang berjalan mengikuti frame dan ditarik halus ke posisi player.
  Snap saat seek/pause/ganti lagu/selisih >250 ms. Waktu tidak pernah mundur.
- Hanya ketikan/lirik yang memakai jam ini; kamera dan FFT tetap memakai posisi asli.

### Render (`TerminalRenderer.kt`, `TerminalSyntax.kt`)
- Kursor menempel persis di ujung teks (tanpa spring), meluncur bersama huruf yang fade-in.
- Huruf yang sedang diketik fade-in + turun ~2.5dp ke baseline.
- Blink kursor halus (fade), glow tipis di sekitar kursor (mode non-LOW).
- Band baris aktif bergradien (aksen cyan), glow lembut pada lirik aktif (non-LOW).
- Warna aksen/kursor: `0xFF8BE9FD` (juga dipakai garis progres).

### Test (`TerminalEnhancedTest.kt`)
Ditambah: kecepatan manusiawi, huruf di tengah ketukan, jam pemutaran stabil.

## Update: latar AMOLED + kotak terminal baru (`TerminalRenderer.kt`)
Dari video 529672.mp4: kotak editor tampak biru-navy dengan tepi tajam, dan latar bergradien.
- **Latar hitam murni** (`#000000`) di seluruh layar: tanpa gradien, tanpa vignette, tanpa tint.
  Di layar AMOLED piksel itu mati.
- **Kartu terminal**: hitam pekat (opacity 1, jadi panel belakang tidak tembus ke teks), sudut
  membulat 14dp dan anti-alias (sebelumnya persegi tajam dan tidak anti-alias).
- Garis tepi tipis (hairline) bergradien: lebih terang di atas, memudar ke bawah.
- Baris memudar halus ke hitam di tepi atas dan bawah kartu, bukan terpotong keras.
- Title bar kecil: tiga titik abu-abu + `lyrics.kt` (redup). Hapus blok "Hairline edge, then a
  quiet window title" di `draw()` kalau tidak mau.
- Garis gutter dan indent guide dinetralkan (tidak lagi kebiruan).
- Kamera tidak diubah (sudah teredam: yaw maks ±10°).

## Update 3: gaya sinematik (video 529734) + mode phonk/montagem (video 529732, 529733)
**Status: belum dikompilasi/dijalankan.** Cek CI atau
`./gradlew :app:testFossDebugUnitTest --tests "com.vantra.music.ui.player.terminal.*"`.

### Gaya sinematik (referensi: video terminal)
- **Close-up mengikuti kursor**: baris yang diketik di-zoom (±15 kolom di portrait, ±26 di landscape),
  kamera menggeser halus mengikuti kursor, baris lama meredup ke atas. Hanya aktif jika Typing
  nyala dan lirik tersinkron ada; selain itu tampilan editor biasa.
- **Kursor blok merah** dengan glow (menggantikan garis tipis cyan). Keyword jadi merah
  (`TerminalPalette.Keyword`), aksen `TerminalPalette.Cursor` merah.
- **Refleksi** samar di bawah baris yang sedang diketik.
- **Pull-back ke "dinding kode" miring + bola cahaya oranye** saat instrumental/intro/outro,
  penanda `climax`, dan breakdown phonk. Panel samping, garis tepi, dan title bar hilang di dua shot ini.
- Kamera: `TerminalCamera.shotPitch/shotYaw/shotRoll/shotDolly` (nol = tidak ada efek).

### Mode phonk/montagem (referensi: dua video phonk)
Terdeteksi otomatis dari audio (`TerminalCinema`): kick stabil 85-200 BPM + bass berat. Lagu
ritme acak, balada, atau tanpa audio lokal tidak memicu apa pun. Analisis video referensi:
kick ±152 BPM (interval 0,395 dtk), breakdown tanpa bass, lalu drop.
- Hentakan zoom (punch) + miring bergantian tiap kick.
- Flash hitam-putih dengan teks terbalik: tiap kick ke-2, **maksimal ±2,6x/detik** (aman untuk
  sensitif cahaya). Mati jika Reduced Motion atau Intensity = Off.
- Garis kecepatan radial, RGB split pada lirik aktif.
- Breakdown (bass hilang >0,8 dtk): kamera mundur ke dinding + bola cahaya.
  Kick yang kembali = **drop**: flash penuh, hentakan besar, banting masuk ke close-up.
- Skala efek mengikuti "Bass response" di pengaturan; hanya berjalan jika Beat reactive nyala.

### Penyetel
`TerminalCinema` (MinGap/MaxGap, FlashGap, ambang `score`), `closeZoom`/`cols` dan pan spring di
`TerminalRenderer.draw`, sudut dinding di `TerminalPlayerStyle` (`-0.50f` pitch dll).

### Belum dikerjakan
Kinetic typography kata-per-kata dan CD cover berputar seperti di video phonk (perlu artwork
bitmap di renderer); bisa jadi tahap berikutnya.

### Test baru
`TerminalCinemaTest`: lock phonk, ritme acak/balada tidak memicu, batas laju flash,
breakdown -> dinding -> drop -> close-up, shot mengikuti lirik, motion off = nol efek.

## Update 4: perbaikan dari hasil rekaman 530008.mp4 + impact frame phonk
Dasar: analisis rekaman build Stage 7 (35 flash dalam 42 dtk, level latar banyak di abu-abu,
pita hitam saat flash, garis di atas teks, wall kecil, kursor lepas dari lirik).

Perbaikan:
1. **Flash potong keras**, tanpa ramp abu-abu: putih penuh (75 ms), setiap flash ke-3 abu terang
   (0,62), teks otomatis hitam. Tidak ada fade.
2. **Fade tepi** atas/bawah/kiri/kanan kini mengambil warna flash (tidak ada pita hitam lagi);
   ada fade lembut di sisi kiri/kanan untuk lirik panjang.
3. **Garis kecepatan di belakang teks** (di margin dan di atas kartu), bukan di atas teks.
4. **Shot dinding** lebih tenang (sudut lebih kecil), sedikit zoom (1,3x) dan baris lebih
   terang agar terbaca. **Orb matahari dikurangi**: inti 45% lebih kecil, bloom jauh lebih
   redup dan sempit, streak pendek, intensitas maks ~60%.
5. **Kursor tetap di ujung kata** saat close-up (tidak lompat ke baris kode di bawahnya).
6. **Lirik satu baris** saat Typing nyala (wrap di 64 kolom); kamera yang menggeser.
7. Band baris aktif hilang di close-up (tidak ada kotak abu-abu bertepi tajam).

Impact frame phonk (baru):
- **Drop = stutter putih-hitam-putih** (sekali, 0,19 dtk); flash biasa selalu tunggal,
  maks 2,5/dtk, jarak minimum diuji.
- **Ring gelombang kejut** keluar dari tengah tiap flash; lebih besar dan tebal saat drop.
- **Screen shake** pada tiap hentakan, zoom hentakan 7% -> 9% (drop ±11%).
- Flash hanya aktif setelah mode phonk terkunci (bukan karena kick acak).
- Skala flash/ring mengikuti "Bass response" (rendah = lebih lembut, 0 = mati).

Test: `TerminalCinemaTest` diperbarui (flash hanya level 0/0,62/1, ring terkirim, jarak minimum
flash, drop = flash penuh).

## Update 5: pengaturan, sinkron beat, teks kinetik
Menu opsi Terminal (OptionSheet yang sama) kini punya tiga grup baru:
- **Camera shots**: Cinematic (default) / Editor (tampilan editor biasa, tanpa close-up dan dinding).
- **Beat flash**: Off (tanpa flash dan tanpa kata raksasa) / Soft (denyut abu gelap, teks tetap terang)
  / Full (potong keras putih-hitam). Ring dan shake tetap ikut "Bass response".
- **Beat sync**: Later 150/75 ms, Exact, Earlier 75/150 ms. Efek terasa telat di speaker ->
  pilih Earlier; terlalu cepat di Bluetooth -> pilih Later. Jika audio di posisi bergeser belum
  tersedia, dibaca di posisi normal (tidak pernah kehilangan beat).

Lainnya:
- **Kata raksasa**: setiap flash, kata terakhir yang diketik dibanting memenuhi layar (hanya
  sepanjang potongan flash, miring bergantian) seperti video lirik phonk.
- **Deteksi phonk lebih cepat**: butuh 5 interval kick (sebelumnya 6), ±0,4 dtk lebih awal.
  Dicek terhadap ritme acak (Random(3)) tanpa false-positive.
- Test: `editorModeKeepsThePlainView...` (shots=false, flashScale 0 dan 0,4).

Masih belum: CD cover + waveform di pojok (perlu bitmap artwork di renderer), motion blur
horizontal, auto-turun efek saat FPS drop (performa belum diukur di HP kelas bawah).

## Update 6: impact frame glitch + warna flash (bukan hanya putih-hitam)
Berlaku hanya untuk musik yang terkunci sebagai phonk/montagem.

**Warna flash** (potong keras, tanpa fade): putih, magenta `#FF2D95`, putih, cyan `#19D3FF`,
putih, merah `#FF3B30`, berulang. Teks otomatis hitam di latar terang (putih, cyan) dan tetap
terang di latar gelap (magenta, merah). Drop = putih -> hitam -> magenta/cyan (sekali).

**Glitch seluruh layar** (`TerminalRenderer.drawGlitch`): scene direkam ke `Picture` lalu diputar ulang
- **aberasi kromatik**: tiga salinan kanal R, G, B bergeser (blending ADD),
- **irisan layar robek** yang bergeser ke samping (3-9 irisan, makin banyak makin kuat),
- **balok datamosh** yang berpindah posisi,
- **garis tipis cyan/magenta/putih/kuning**,
- **scanline** di atasnya,
- polanya baru tiap frame (berkedip seperti sinyal rusak).
Kata raksasa juga ikut: kanal merah/cyan terpisah dan separuh atas/bawah saling bergeser.

**Berlapis per kick**: tiap kick = robekan kecil (0,4); tiap kick ke-2 = flash berwarna + ring +
glitch besar (0,85); drop = glitch penuh (1,0) yang meredup lebih lama.

**Pengaman**: otomatis berhenti ±0,4 dtk jika ada frame lambat (<22 fps); tidak berjalan di
kualitas Low; mati bila Reduced Motion / Intensity Off. Beat flash Off/Soft menyisakan setengah
kekuatan glitch; "Bass response" menskalakannya.

Test baru: `flashesWalkThroughColours...` (>=3 warna, glitch di kick yang tidak flash, tidak ada
glitch tanpa beat phonk, drop = glitch 1,0 dan flash putih).

Catatan: `Picture` + `saveLayer` dipakai pada API 24+; belum diuji di perangkat. Jika terasa berat
di HP tertentu, set kualitas ke Low atau Beat flash Off.
