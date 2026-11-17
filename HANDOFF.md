# HANDOFF - Chaman Downloader

## Project kya hai
Android media downloader (YouTube, Instagram, yt-dlp-supported sites, direct links, HLS/DASH).
Premium minimal UI, dark+light, no ads. Brand: Chaman. Package: `com.chaman.downloader`.

## Stack
Kotlin + Jetpack Compose | embedded Python via Chaquopy 16.0.0 (Python 3.11) | yt-dlp |
native FFmpeg (jniLibs .so, Phase 0 Batch B) | Room | Foreground Service + WorkManager | MediaStore.
Build sirf GitHub Actions se (AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7, JDK 17). Sirf arm64-v8a.

## Layout
- `app/build.gradle.kts` - Chaquopy config, yt-dlp pip pin (`ytdlpVersion` in gradle.properties)
- `app/src/main/python/tests.py` - Python test functions
- `app/src/main/java/com/chaman/downloader/MainActivity.kt` - 6 test cards (Run / PASS-FAIL / log)
- `app/src/main/java/com/chaman/downloader/FfmpegProbe.kt` - FFmpeg version + convert probe
- `.github/workflows/build.yml` - CI; yt-dlp latest PyPI se resolve, `BUILD_INFO.txt` me log

## Rules
- Test major engines independently; untested module integrate nahi
- Ek step device pe pass hone ke baad hi agla step
- Koi fake/non-functional button nahi; proper error handling + logging
- Stable functionality preserve; tested dependency versions pin
- Keys/tokens kahin hardcode nahi
- Website access restrictions aur content rights ka respect

## Status
- Phase 0 / Batch A (0.1 Python ARM64, 0.2 yt-dlp version, 0.3 YouTube info extract): device pe pass (Python 3.11.10 aarch64, yt-dlp 2026.10.26, YouTube info extract OK, JS runtime issue nahi aaya). Tag: v0.1.0
- Batch B (0.4 Instagram public reel info, 0.5 FFmpeg native): CODE LIKHA (v0.2.0-phase0b), CI build pending, device test pending
  - T4: `yt_info` Instagram public reel URL pe (bina login)
  - T5a/T5b: `FfmpegProbe.kt`, `dev.ffmpegkit-maintained:ffmpeg:8.1.9` (Maven Central page se: LGPL v3, free tier). Library API reflection se call hota hai (`com.arthenica.ffmpegkit.FFmpegKit`). ABI, package naam, codecs UNVERIFIED, device test se confirm hoga.
  - T5b `-f lavfi sine` + native `aac` encoder pe depend karta hai; minimal build me lavfi na ho to yahi fail dikhayega.
- Batch C (0.6 background download stability, 0.7 pinned reproducible CI): baaki

## Known risks (test se confirm hone hain)
- Naye yt-dlp ko YouTube pe JS runtime chahiye ho sakta hai (signature/challenge solving). Test 3 fail ho to error log yahi dikhayega; fix usi step me.
- Instagram bina login ke block/rate-limit kar sakta hai (Batch B).
- Android 10+ pe FFmpeg ko jniLibs me `.so` ki tarah rakhna padega (Batch B).
- `ytdlpVersion=2025.10.22` fallback hai, CI latest uthata hai. Pass hone ke baad tested version pin karna hai.

## Baaki kaam
1. CI build green karna, APK device pe chalana, 3 tests ka result dena
2. Batch B, Batch C
3. Phase 1 se UI (Home / Queue / Library / Settings)
