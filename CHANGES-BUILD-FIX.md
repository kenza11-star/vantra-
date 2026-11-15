# Build fix: CI gagal di compileArm64FossDebugKotlin / compileArm64GmsReleaseKotlin

## Gejala (dari log GitHub Actions)
```
> Task :app:compileArm64FossDebugKotlin
e: file:///.../app/src/main/kotlin/com/vantra/music/ui/player/terminal/TerminalFrame.kt:75:29
   The API of this layout is experimental and is likely to change in the future.
> Task :app:compileArm64FossDebugKotlin FAILED
```
Error yang sama menggagalkan job "Terminal V3 Stage 1 verification" (debug)
dan "Build Vantra (arm64 GMS release)" (release).

## Penyebab
`terminalInsets()` memakai `WindowInsets.systemBarsIgnoringVisibility`, yang ditandai
`@ExperimentalLayoutApi` di Compose Foundation. Tanpa opt-in, Kotlin menganggapnya error compile.

## Perbaikan
`app/src/main/kotlin/com/vantra/music/ui/player/terminal/TerminalFrame.kt`
- tambah `import androidx.compose.foundation.layout.ExperimentalLayoutApi`
- tambah `@OptIn(ExperimentalLayoutApi::class)` pada `terminalInsets(...)`

Pola ini sama dengan yang sudah dipakai di `StableInsets.kt` dan `MainActivity.kt`.
