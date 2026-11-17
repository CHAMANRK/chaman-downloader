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
- `.../HomeScreen.kt` (URL, Paste, Search, result: details + quality-wise Download), `QueueScreen.kt`, `LibraryScreen.kt` (MediaStore `Download/Chaman`), `SettingsScreen.kt` (theme, battery exempt, Debug)
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
- Keys/tokens/keystore kahin hardcode nahi, repo me nahi (sirf GitHub Secrets)
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
  - Step A v0.4.1 (device pe pass, tag v0.4.1): file naam = video title (`safeFileName`, illegal chars hata, 180 UTF-8 bytes limit; duplicate naam par Android khud (1) lagata hai), speed auto unit (1024 KB/s se upar MB/s) + ETA + size (`Format.kt`, unit tests `FormatTest.kt`), Library me thumbnail (loadThumbnail, fallback first frame; audio ke liye placeholder). Python `download` ab `cb.onStats(...)` call karta hai aur JSON me `title` deta hai.
  - v0.4.2 (device pe pass, tag v0.4.2): logo (adaptive vector icon: warm black bg, gold "C" + download arrow, monochrome themed icon bhi) + permanent signing key. Key GitHub Secrets me: `CHAMAN_KEYSTORE_B64` (base64 .jks), `CHAMAN_KEYSTORE_PASSWORD` (PKCS12, key password = store password), alias `chaman` (workflow me fixed). Secrets na mile to debug key fallback (workflow warning + BUILD_INFO me `signing:` line). Keystore repo me kabhi nahi (`.gitignore` me `*.jks`); user ke paas backup (Drive/laptop). Pehli baar purani (debug-key) APK uninstall lagega, uske baad update seedha.
  - Step B v0.4.3 (CODE LIKHA, device test pending): Info button hata, Search button (python `search(url)`): thumbnail, title, channel, duration, upload date, views, likes, site, description (poora dikhao). Har height ka best option (`_build_options`: mp4 container prefer, audio m4a/opus pair, progressive formats single) apne Download button ke saath + "Best available" + audio-only (m4a/webm). Download `download(url, outdir, cb, fmt)` ko format string deta hai (service extra `EXTRA_FORMAT`). Search fail ho to "Seedha best quality me download" fallback. Pending check: v0.4.2 (permanent key) se v0.4.3 BINA uninstall install hona chahiye (update test). mp3 abhi nahi (libmp3lame unverified).
  - Step C (baaki): Settings me Download folder select (default `Download/Chaman`, Change via folder picker, default pe wapas). Library chuna hua folder dikhayegi.

- Step D v0.4.4-share (device pe pass, user ne confirm kiya; tag v0.4.4): (1) Share menu se link: manifest me `ACTION_SEND text/plain` filter + MainActivity `launchMode=singleTask`, `onNewIntent`. `ShareInbox.kt`: `extractFirstUrl` (shared text me pehla http(s) link, trailing punctuation trim) + `ShareTest.kt`. Link aane par Home khulta hai, URL bharta hai, auto-search chalta hai (download khud start nahi hota). Link na mile to Toast. (2) Paste ya Search button ke baad keyboard band (`focusManager.clearFocus` + `keyboardController.hide`). Search logic ab `runSearch()` (HomeScreen.kt) me, Home button aur Share dono use karte hain.
  - Device test checklist: YouTube Share > Chaman (app band ho to bhi, app khula ho to bhi), Instagram reel Share > Chaman, bina link wala text share, Paste/Search pe keyboard close.
  - UI/UX ideas list (baaki): result screen halka (chips + ek sticky Download), smart paste chip, queue card me thumbnail + clean progress, library search/filter/multi-select (delete/share), nav icons, success snackbar.

- Step E v0.4.5-ytclient (CODE LIKHA, device test pending): Problem: mobile data (Airtel) pe YouTube bot-check ("Sign in to confirm you're not a bot"): Search me title aata tha par formats nahi (isliye sirf "Best available"), Download FAIL. Wi-Fi pe sab theek; Termux ke yt-dlp se data pe bhi chalta hai (karan unverified: Termux me shayad newer yt-dlp / node JS runtime; app me Chaquopy pe JS runtime nahi). Fix: `tests.py` me `_YT_CLIENTS = [default, tv, android_vr, web_embedded]` chain, `search()` aur `download()` dono me (sirf YouTube URL pe; baaki sites pe pehle jaisa). Jo client chala wo `_client_memo[url]` me yaad rehta hai taaki download wahi format ids use kare. Sab fail ho to saaf Hinglish message (`BOT_MSG` / `NO_FORMATS_MSG`) + details. `DownloadState.error` (+ `shortError`) se Queue card me FAIL ke neeche chhota message. Log me `[client=...]` dikhta hai. Client names ka koi guarantee nahi: real device (data pe) test se verify karna hai; list `_YT_CLIENTS` me badli ja sakti hai. Cookies import abhi nahi (last option agar chain data pe fail ho).
  - Device test checklist: data pe wahi Shorts link (Search me multiple options aaye, download chale), Wi-Fi pe dubara (regression), Instagram link (regression), Queue FAIL me chhota message.

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
