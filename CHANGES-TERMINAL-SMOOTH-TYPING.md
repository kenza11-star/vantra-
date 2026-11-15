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
- Latar gradien biru-hitam + vignette (non-LOW), panel editor lebih pekat (0.95) agar panel
  belakang tidak tembus ke teks.
- Band baris aktif bergradien (aksen cyan), glow lembut pada lirik aktif (non-LOW).
- Warna aksen/kursor: `0xFF8BE9FD` (juga dipakai garis progres).

### Test (`TerminalEnhancedTest.kt`)
Ditambah: kecepatan manusiawi, huruf di tengah ketukan, jam pemutaran stabil.
