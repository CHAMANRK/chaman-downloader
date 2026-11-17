# HANDOFF - Chaman Downloader

## Project kya hai
Android media downloader (YouTube, Instagram, yt-dlp-supported sites, direct links, HLS/DASH).
Premium minimal UI, dark+light, no ads. Brand: Chaman. Package: `com.chaman.downloader`.

## Stack
Kotlin + Jetpack Compose | embedded Python via Chaquopy 16.0.0 (Python 3.11) | yt-dlp 2026.10.26 (pinned) |
FFmpegKit community fork `dev.ffmpegkit-maintained:ffmpeg:8.1.9` (ffmpeg n8.1.3, LGPL, free tier) |
Foreground Service (dataSync) | MediaStore Downloads | Room + WorkManager (aage).
Build sirf GitHub Actions se (AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7, JDK 17). Sirf arm64-v8a. minSdk 29, targetSdk 34.

## Layout
- `app/build.gradle.kts` - Chaquopy config, yt-dlp + ffmpegkit versions `gradle.properties` se
- `app/src/main/python/tests.py` - `env_info`, `ytdlp_version`, `yt_info`, `download(url, outdir, cb)`
- `.../FfmpegProbe.kt` - FFmpeg version + convert (reflection se FFmpegKit)
- `.../DownloadService.kt` - foreground service: wake lock, notification (progress + Cancel), Python download, MediaStore save (`Download/Chaman/`)
- `.../MainActivity.kt` (Phase 1) - sirf Activity + `ChamanApp` (theme, edge-to-edge) + `MainShell` (HorizontalPager, 4 tab) + `FloatingNav` (floating pill, sliding indicator)
- `.../Theme.kt` (luxury palette: warm black+champagne gold / ivory+bronze, serif headlines, `Prefs` theme), `.../Ui.kt` (LuxCard, GoldButton, QuietButton, PageTitle)
- `.../HomeScreen.kt` (URL, Paste, Info dekho, Download), `QueueScreen.kt`, `LibraryScreen.kt` (MediaStore `Download/Chaman`), `SettingsScreen.kt` (theme, battery exempt, Debug)
- `.../DebugScreen.kt` - purana Phase 0 test screen (T1-T6), Settings > Engine tests (Debug) se khulta hai
- `.../DownloadState.kt` - service/UI shared state + timestamped log
- `.../Format.kt` - pure helpers: `fmtSpeed`, `fmtBytes`, `fmtEta`, `formatProgressLine`, `safeFileName` (+ `FormatTest.kt`)
- `.../Mime.kt` + `app/src/test/.../MimeTest.kt` - pure function + unit test
- `.github/workflows/build.yml` - unit tests, assembleDebug, `BUILD_INFO.txt` (sab versions)

## Rules
- Test major engines independently; untested module integrate nahi
- Ek step device pe pass hone ke baad hi agla step
- Koi fake/non-functional button nahi; proper error handling + logging
- Stable functionality preserve; tested dependency versions pin
- Keys/tokens kahin hardcode nahi
- Website access restrictions aur content rights ka respect
- Repo hamesha public

## Status
- Phase 0 / Batch A (Python ARM64, yt-dlp version, YouTube info extract): device pe pass (v0.1.0)
- Phase 0 / Batch B (Instagram public reel bina login, FFmpeg native n8.1.3, aac m4a convert): device pe pass (v0.2.0). Instagram `duration` null aata hai.
- Phase 0 / Batch C - DEVICE PE PASS, closed (v0.3.1) (0.6 background download stability, 0.7 pinned CI): v0.3.0 pe T1-T5 device pass, battery exempt true. T6 FAIL v0.3.0 me: `Requested format is not available` (format `best[ext=mp4]/best`, video q7nG6rCxbK4; shayad is video ka koi pre-merged stream nahi, confirm nahi hua).
  - Fix v0.3.1 (CODE LIKHA, device test pending): `FORMAT_SELECTOR = bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b`, video aur audio alag download, Kotlin `FfmpegProbe.merge` (-c copy) se merge. Format select phir fail ho to error me available formats ki list aayegi.
  - v0.3.1 device result: T6 functional PASS (video+audio alag download, FFmpeg merge, MediaStore save `Download/Chaman/`, 2560p vp09+mp4a mp4 bana). Download 9 sec me khatam hua (~30 MB/s), isliye background/screen-off stability ka asli test NAHI hua (unproven). Tag: v0.3.1
  - Observation: user ne bataya ki YouTube URL (q7nG6rCxbK4) ka download pehle 25-30 KB/s pe bahut slow tha. Karan confirm nahi hua (ek possible lead: YouTube throttling jab yt-dlp ka JS challenge solve na ho; unverified). Refine karte waqt speed check karna.
  - Background/screen-off stability: user ne device pe test kiya, theek chala (v0.3.1) - device pe pass. Batch C CLOSED.
  - Idea (nahi bana, ab zaroori nahi): T6 me "Speed limit KB/s" field (yt-dlp `ratelimit`) taaki 5-10 min ka stability test ho sake.
  - T6: lamba public YouTube video (default URL `https://youtu.be/q7nG6rCxbK4`), background + screen off me 5-10 min test, log me 10% milestones + 30s heartbeat + chosen formats + total time
  - Battery-optimization exempt button (Oppo/ColorOS)
  - 0.7: yt-dlp ab `gradle.properties` me pinned 2026.10.26; CI default pinned version uthata hai, PyPI latest sirf info ke liye print hota hai; unit test CI me chalta hai
  - "Reproducible" ka matlab yahan: same pinned versions. Bit-for-bit identical APK ka claim nahi. GitHub Actions major tags (@v4) pinned hain, commit SHA nahi.

- Phase 1 (base UI) v0.4.0: device pe pass (user ne confirm kiya, tag v0.4.0). Home/Queue/Library/Settings + floating nav + swipe pager. Queue abhi sirf ek current download dikhata hai (Room queue Phase 2). Library tap = file open (ACTION_VIEW). Quality default / delete / share abhi nahi (fake button nahi rakhna).

- Phase 1 improvements (user request, 3 steps):
  - Step A v0.4.1 (CODE LIKHA, device test pending): file naam = video title (`safeFileName`, illegal chars hata, 180 UTF-8 bytes limit; duplicate naam par Android khud (1) lagata hai), speed auto unit (1024 KB/s se upar MB/s) + ETA + size (`Format.kt`, unit tests `FormatTest.kt`), Library me thumbnail (loadThumbnail, fallback first frame; audio ke liye placeholder). Python `download` ab `cb.onStats(...)` call karta hai aur JSON me `title` deta hai.
  - Step B (baaki): Search button (Info ki jagah) -> poori detail (thumbnail, title, channel, duration, date, views, likes, description) + har quality ke saath apna Download button + audio-only options. mp3 abhi nahi (libmp3lame unverified).
  - Step C (baaki): Settings me Download folder select (default `Download/Chaman`, Change via folder picker, default pe wapas). Library chuna hua folder dikhayegi.

## Design findings / known risks
- yt-dlp ko FFmpeg binary nahi milta (FFmpeg JNI library hai), isliye merge yt-dlp se nahi hoga. Design: Python `download()` video aur audio alag files me laata hai, Kotlin `FfmpegProbe.merge` se `-c copy` merge (mp4+m4a ho to .mp4, warna .mkv). Yahi pattern final app me bhi chalega (max quality ke liye zaroori). Pre-merged progressive streams YouTube pe ab kam/koi nahi hote.
- Android 15 me dataSync foreground service ka time limit hai (kuch ghante / 24h). Bahut lambe/queued downloads ke liye Phase 3 me WorkManager + service design dekhna hai.
- REQUEST_IGNORE_BATTERY_OPTIMIZATIONS Play Store policy me restricted hai (sideload/public APK ke liye theek).
- Instagram bina login ke kabhi block/rate-limit kar sakta hai (abhi pass).
- Unverified: mp3 encoder (libmp3lame), H.264/H.265 encoder (free tier me nahi ho sakta), Phase 4 se pehle check.

## Baaki kaam
1. ~~Batch C~~ done (device pe pass)
2. Phase 1: base UI (Home / Queue / Library / Settings) - v0.4.0 device test pending, phir refine (luxury polish)
3. Phase 2+: yt-dlp integration proper, download manager (Room, queue, pause/resume), FFmpeg tools, Instagram/universal, optimization
