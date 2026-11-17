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
- `.../MainActivity.kt` (Phase 1) - sirf Activity + `ChamanApp` (theme, edge-to-edge) + `MainShell` (HorizontalPager, 4 tab; download PASS pe haptic + "Saved" snackbar with Open) + `FloatingNav` (floating pill, sliding indicator, icon + selected label, Queue icon pe progress ring)
- `.../Theme.kt` (luxury palette: warm black+champagne gold / ivory+bronze, serif headlines, `Prefs` theme), `.../Ui.kt` (LuxCard, GoldButton, QuietButton, PageTitle)
- `.../HomeScreen.kt` (URL + clear (X) + Paste + Search; result: `ResultHeader` (duration badge, meta line, Details toggle), `SegToggle` Video|Audio, `QualityList` select, sticky Download bar; `runSearch()`), `.../ShareInbox.kt` (Share menu link: `ShareInbox`, `extractFirstUrl`, `normalizeLinkInput`; tests `ShareTest.kt`), `QueueScreen.kt`, `LibraryScreen.kt` (MediaStore `Download/Chaman`; search + All/Video/Audio chips, thumbnail pe duration badge + play overlay, `openMediaUri`), `LibraryFilter.kt` (`libMatches`, `commonMime`, `splitFileName`, `renamedFileName`; tests `LibraryFilterTest.kt`); Library me long-press multi-select (Rename/Delete/Share bar) + har item pe 3-dots menu, `NavIcons.kt` (4 Canvas nav icon), `SettingsScreen.kt` (theme 2x2: System/Dark/AMOLED/Light, battery exempt, version + "Made by Najeef", Debug)
- `.../DebugScreen.kt` - purana Phase 0 test screen (T1-T6), Settings > Engine tests (Debug) se khulta hai
- `.../DownloadState.kt` - service/UI shared state + timestamped log (+ `savedUri`/`savedMime` PASS ke baad)
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
  - Step C (Download folder select): user ne cancel kiya, nahi banana.

- Step D v0.4.4-share (device pe pass, user ne confirm kiya; tag v0.4.4): (1) Share menu se link: manifest me `ACTION_SEND text/plain` filter + MainActivity `launchMode=singleTask`, `onNewIntent`. `ShareInbox.kt`: `extractFirstUrl` (shared text me pehla http(s) link, trailing punctuation trim) + `ShareTest.kt`. Link aane par Home khulta hai, URL bharta hai, auto-search chalta hai (download khud start nahi hota). Link na mile to Toast. (2) Paste ya Search button ke baad keyboard band (`focusManager.clearFocus` + `keyboardController.hide`). Search logic ab `runSearch()` (HomeScreen.kt) me, Home button aur Share dono use karte hain.
  - Device test checklist: YouTube Share > Chaman (app band ho to bhi, app khula ho to bhi), Instagram reel Share > Chaman, bina link wala text share, Paste/Search pe keyboard close.

- Step E v0.4.5-ytclient (device pe pass, user ne confirm kiya, tag v0.4.5; mobile data pe YouTube download + Instagram dono chale; log me `[client=default]` aaya, yani fallback chain (tv/android_vr/web_embedded) abhi unproven kyunki us baar block nahi aaya): Problem: mobile data (Airtel) pe YouTube bot-check ("Sign in to confirm you're not a bot"): Search me title aata tha par formats nahi (isliye sirf "Best available"), Download FAIL. Wi-Fi pe sab theek; Termux ke yt-dlp se data pe bhi chalta hai (karan unverified: Termux me shayad newer yt-dlp / node JS runtime; app me Chaquopy pe JS runtime nahi). Fix: `tests.py` me `_YT_CLIENTS = [default, tv, android_vr, web_embedded]` chain, `search()` aur `download()` dono me (sirf YouTube URL pe; baaki sites pe pehle jaisa). Jo client chala wo `_client_memo[url]` me yaad rehta hai taaki download wahi format ids use kare. Sab fail ho to saaf Hinglish message (`BOT_MSG` / `NO_FORMATS_MSG`) + details. `DownloadState.error` (+ `shortError`) se Queue card me FAIL ke neeche chhota message. Log me `[client=...]` dikhta hai. Client names ka koi guarantee nahi: real device (data pe) test se verify karna hai; list `_YT_CLIENTS` me badli ja sakti hai. Cookies import abhi nahi (last option agar chain data pe fail ho).
  - Known chhota issue: vertical video (Shorts) ka label height se banta hai ("1920p" dikhta hai, asli 1080p). Fix: Step F (v0.4.6) me kiya, device test pending.
  - (purani) Device test checklist: data pe wahi Shorts link (Search me multiple options aaye, download chale), Wi-Fi pe dubara (regression), Instagram link (regression), Queue FAIL me chhota message.

- Step F v0.4.6-ui (device pe pass, user ne confirm kiya "All Pass"; tag v0.4.6): (1) Result screen halka: thumbnail pe duration badge, title, ek line me channel · views · date, "Details" toggle (likes, site, poora description). (2) Quality ab select hoti hai (radio rows, default "Best available") + "Video | Audio" toggle (audio na ho to toggle nahi), neeche ek sticky gold "Download · {quality} · ~size" bar (floating nav ke upar). Purane har-row Download buttons hata diye. (3) Link field: jab text ho to X (clear) button; `normalizeLinkInput` (ShareInbox.kt): newline hata, 2000 char cap, ek saath 20+ char paste ho (Paste button / keyboard paste) aur link ho to sirf pehla link rakhta hai (pehle lamba text field me bahut door scroll ho jata tha). (4) Vertical video label fix: `_q()` (tests.py) chhoti side leta hai, Shorts ab "1080p" (pehle "1920p"). Tests: `ShareTest.kt` (normalizeLinkInput), Python label logic fake-formats se check kiya.
  - Device test checklist: Shorts link (label 1080p, duration badge, selection + sticky button, Audio tab me m4a/webm download), normal horizontal video (1080p/720p), Instagram link (audio toggle na aaye to theek), lamba text paste (Paste button aur keyboard paste dono) -> sirf link bache, X button se clear, swipe left me field bahut door na jaye.

- Step G v0.4.7-polish (device pe pass, sirf nav ring theek nahi dikha: ring icon ke upar-side chhota arc tha; v0.4.8 me fix): 4 chhote kaam ek saath. (1) Library: search bar (+ X clear, keyboard Search pe band) + filter chips All/Video/Audio (`libMatches`), match na ho to "Koi match nahi" card. (2) Library item: thumbnail pe chhota play overlay + duration badge (MediaMetadataRetriever, cache). (3) Nav: har tab ka icon (Canvas), sirf selected ka label; Queue icon pe chhota progress ring (busy dot hata). (4) Download complete (RUNNING->PASS) pe haptic + "Saved" snackbar, "Open" action file kholta hai (`DownloadState.savedUri`). Tests: `LibraryFilterTest.kt`.
  - Device test checklist (G): sab pass, sirf ring ko chhodkar.

- Step H v0.4.8-select (device pe pass, user ne confirm kiya; tag v0.4.8): (1) Nav ring fix: download chalte waqt Queue icon ke bahar poora ring (28dp, progress ke saath), download icon andar (15dp); khatam hone par normal icon. (2) Library multi-select: item long-press = select mode (haptic), baad me tap se toggle; select mode me neeche floating bar (nav ke upar): X (clear), count, Rename (sirf 1 select ho tab), Delete, Share. Back dabane se selection hatta hai (`LibrarySelection.active`, MainShell ka Back tab-wapas-Home tab nahi chalta). Select mark (tick circle) har item pe. Selection sirf filter/search me dikhne wali files par. (3) Har item ke side me 3-dots menu (Rename / Delete / Share); select mode me dots ki jagah tick circle. (4) Rename dialog: naam edit, extension fixed (`splitFileName`, `renamedFileName`, illegal chars `safeFileName` se saaf). Delete: confirm dialog. Share: ACTION_SEND / SEND_MULTIPLE chooser (`commonMime`). Delete/Rename pehle seedha try; file apni app ki na ho (reinstall ke baad) to Android system permission popup (API30+ `createDeleteRequest`/`createWriteRequest`, API29 `RecoverableSecurityException`). Tests: `LibraryActionsTest` (LibraryFilterTest.kt).
  - Device test checklist: nav ring (download chalte waqt Queue icon ke bahar ring, icon andar, progress bhadhe, khatam pe normal); Library: long-press se select, tap se aur select/unselect, X aur Back se select band; 1 select = Rename/Delete/Share, 2+ select = Delete/Share; Rename (extension wahi rahe, khali naam na ho, duplicate naam pe error toast); Delete (confirm popup; single + multiple); Share (1 file + 2+ files, WhatsApp/Drive jaisi app me jaye); 3-dots menu se teeno; search/filter ke saath select; purani (reinstall se pehle ki) file delete/rename pe system permission popup aaye to Allow karke chale; dark + light.

- Step I v0.4.9-settings (CODE LIKHA, device test pending): (1) AMOLED theme: `ThemeMode` = SYSTEM/DARK/AMOLED/LIGHT (`Theme.kt` me `LuxAmoled` = LuxDark + asli black background #000000); Settings me 2x2 theme grid; AMOLED user khud chunta hai (auto-detect nahi). (2) Notification Hinglish + halaat ke hisaab se (`DownloadService.kt`, helpers `notifProgressText`, `friendlyFailText` in `Format.kt`, tests `FormatTest.kt`): shuru "Link check ho raha hai...", chalte waqt "Video download ho raha hai · 45% · 12 MB/s · 2:10 bacha" + [Cancel], merge "Video aur audio jod rahe hain...", save "File save ho rahi hai...", done "Download ho gaya: naam" + [Open] [Share] (MediaStore uri, ACTION_VIEW / chooser), fail/cancel + [Retry] (wahi url + format + title; `EXTRA_TITLE` naya extra, `PendingIntent.getForegroundService`). Naya download shuru hone par purana Done/Fail notification hat jata hai. Notification tap = app khulta hai. Pause/Resume nahi (resume support nahi, fake button nahi). (3) Settings me "Made by Najeef". (4) Folder selector (Step C) user ne CANCEL kar diya: folder `Download/Chaman` hi rahega, dobara mat banao.
  - Device test checklist: Settings me 4 theme options, AMOLED pe background asli black (Home/Queue/Library/Settings, cards/nav padhne layak), restart ke baad AMOLED yaad rahe; Settings me "Made by Najeef"; notification: download shuru (text), progress (% speed bacha + Cancel), video+audio merge text, done (Open file kholta hai, Share chooser aata hai, tap = app), Cancel dabao ("Download rok diya" + Retry), Retry dabao (wahi quality se dobara shuru), fail (airplane mode/ bad link: Hinglish kaaran + Retry), nayi download pe purana notification hate, dark/light/AMOLED me app theek.

## Design findings / known risks
- yt-dlp ko FFmpeg binary nahi milta (FFmpeg JNI library hai), isliye merge yt-dlp se nahi hoga. Design: Python `download()` video aur audio alag files me laata hai, Kotlin `FfmpegProbe.merge` se `-c copy` merge (mp4+m4a ho to .mp4, warna .mkv). Yahi pattern final app me bhi chalega (max quality ke liye zaroori). Pre-merged progressive streams YouTube pe ab kam/koi nahi hote.
- Android 15 me dataSync foreground service ka time limit hai (kuch ghante / 24h). Bahut lambe/queued downloads ke liye Phase 3 me WorkManager + service design dekhna hai.
- REQUEST_IGNORE_BATTERY_OPTIMIZATIONS Play Store policy me restricted hai (sideload/public APK ke liye theek).
- Instagram bina login ke kabhi block/rate-limit kar sakta hai (abhi pass).
- Unverified: mp3 encoder (libmp3lame), H.264/H.265 encoder (free tier me nahi ho sakta), Phase 4 se pehle check.

## UI/UX ideas (is session me diye gaye; status)
Done:
- Share se download (Share menu me Chaman, auto-search): v0.4.4, device pe pass
- Keyboard Paste/Search ke baad band: v0.4.4, pass
- Result screen halka (duration badge, meta line, Details, quality select + Video|Audio toggle + sticky Download): v0.4.6, pass
- Link field: clear (X) button + lamba paste normalize (sirf link, 2000 char cap): v0.4.6, pass
- Shorts label "1080p" (chhoti side): v0.4.6, pass
- YouTube bot-check fallback (player client chain) + Queue me saaf error: v0.4.5, pass (data pe `[client=default]` chala; fallback chain abhi unproven)

Baaki (user ne 10th hata diya; Smart Paste chip bhi user ne hata diya; order meri suggestion):
1. ~~Smart Paste chip~~: user ne hata diya (extra feature nahi chahiye)
2. Queue card: thumbnail + clean progress (circular/ring), speed + ETA readable ("12 MB/s · 2 min bacha"); monospace line aur log "Details" me chhupao
3. Queue multi-item: disabled Download ki jagah queue me add ("Queued #2"); Phase 2 (Room) ke saath; UI abhi se multi-item list ke hisaab se
4. Library: search bar + filter chips - v0.4.7 me (test pending). Long-press multi-select (Rename / Delete / Share) + 3-dots menu - v0.4.8 me (test pending). Sirf kaam karne wale buttons (fake nahi)
5. Library item: duration badge + chhota play overlay - v0.4.7 me (test pending)
6. Nav: icons + label (sirf selected ka), Queue pe progress ring - v0.4.7 me (test pending)
7. Success pe haptic + chhota "Saved" snackbar ("Open" action) - v0.4.7 me (test pending); "Library me dekho" card abhi bhi hai (hataya nahi)
8. (idea 10 = Home empty state hint/recent thumbnails: user ne nahi liya)
User decision (is session): Queue card (2) aur Queue multi-item (3) abhi chhod diye; sirf "kam code" wale kaam (Library search/filter, badge+overlay, nav icons+ring, haptic+snackbar) v0.4.7 me, phir Library multi-select (Delete/Share).

## Baaki kaam
1. ~~Batch C~~, ~~Phase 1 base UI~~, ~~Step A~~, ~~logo + permanent key~~, ~~Step B~~, ~~Step D~~, ~~Step E~~, ~~Step F~~, ~~Step G (ring ke alawa)~~ - device pe pass
2. Step I v0.4.9-settings: device test pending (checklist upar). Pass hone ke baad hi agla step.
3. Chhode hue (user ne abhi nahi bola): Smart Paste chip, Queue card redesign, Queue multi-item, (Step C download folder select = user ne cancel kiya), Phase 2+ (Room download manager), FFmpeg tools, Instagram/universal, optimization
4. Known: YouTube fallback chain (tv/android_vr/web_embedded) unproven (bot-check dobara aaye to dekhna); mp3 abhi nahi (libmp3lame unverified); 1080p option kabhi Premium/m3u8 format chun sakta hai (abhi problem nahi aayi; aaye to https-only filter)
