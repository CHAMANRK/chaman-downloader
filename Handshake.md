# HANDSHAKE — Chaman Downloader <-> CHAMAN Cookie Browser

Ye file dono repos ke root me **bilkul same copy** rehti hai (`Handshake.md`). Handshake me kuch bhi badle to
pehle yahan likho, phir dono me copy karo, phir code. Dono zip me ye file honi chahiye.

Handshake version: **1**   |   Last updated: Browser v0.3.0-handshake + Downloader v0.4.19-cookies

## Apps
| | Chaman Downloader | CHAMAN Cookie Browser |
|---|---|---|
| Package | `com.chaman.downloader` | `com.chaman.browser.geckotest` |
| Entry activity | `com.chaman.downloader.MainActivity` | `com.chaman.geckotest.MainActivity` |
| Repo | `CHAMANRK/chaman-downloader` | `CHAMANRK/CHAMAN-GeckoView-Test` (naam galat ho to `CookieSetup.APK_URL` + yahan badlo) |
| Signing | permanent key (secrets) | **same permanent key** (same do secrets browser repo me bhi) |

## Flow (user ke liye 3 step)
1. Downloader (Settings, baad me welcome page) -> **Import Instagram cookies** button.
2. Browser installed nahi / purana / alag key: Downloader install karwata hai. Phir **LOGIN** intent se browser seedha Instagram login pe khulta hai.
3. User login karke **Export** dabata hai -> browser **IMPORT_COOKIES** intent se Downloader ko file deta hai -> Downloader front me aata hai (= redirect back), "N cookies saved".

## Message 1: LOGIN (Downloader -> Browser)
- Explicit intent: component `com.chaman.browser.geckotest/com.chaman.geckotest.MainActivity`
- Action `com.chaman.browser.action.LOGIN`, extra `site` = `instagram` | `youtube`
- Browser site ka login page seedha kholta hai (home cards skip). Browser `launchMode=singleTask`, `onNewIntent` bhi handle.

## Message 2: IMPORT_COOKIES (Browser -> Downloader)
- Explicit intent: component `com.chaman.downloader/com.chaman.downloader.MainActivity`
- Action `com.chaman.downloader.action.IMPORT_COOKIES`, extra `site`, `data` = content URI, `FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_NEW_TASK`
- URI authority: `com.chaman.browser.geckotest.cookies` (Browser ka FileProvider, `cacheDir/cookies/<site>.txt`)
- File: Netscape `cookies.txt` (header `# Netscape HTTP Cookie File`, 7 tab fields, `#HttpOnly_` prefix HttpOnly ke liye, session cookie expiry 0)
- Browser sirf tab bhejta hai jab login cookie mili (naam check, value nahi): instagram `sessionid`; youtube `SAPISID` / `__Secure-3PSID` / `SID` me se koi

## Downloader receive rules (sab pass hone chahiye, warna reject + saaf message)
1. `site` abhi sirf `instagram` allowed.
2. URI scheme `content`, authority = browser authority upar wali.
3. `resolveContentProvider(authority).packageName == com.chaman.browser.geckotest`.
4. `checkSignatures(downloader, browser) == SIGNATURE_MATCH` (same key).
5. File <= 512 KB, Netscape format valid (0 bad lines, kam se kam 1 cookie).
6. Phir `filesDir/cookies/<site>.txt` me atomic save; UI refresh; Settings tab khulta hai.

## Cookie use (Downloader)
- Instagram link pe `cookies/instagram.txt` ki **copy** (`cookies/run/instagram.txt`) yt-dlp `cookiefile` me jaati hai (search + download).
- Cookie values kabhi UI/log me nahi. Settings me sirf count. "Clear" button file delete karta hai.
- YouTube cookies abhi use nahi hoti (user ka decision: sirf Instagram).

## Install flow (Downloader)
- Browser state: `MISSING`, `WRONG_KEY` (purani debug-key wali browser app), `OLD` (versionCode < 3), `READY`.
- `READY` -> LOGIN intent. `WRONG_KEY` -> uninstall prompt (`ACTION_DELETE`) phir fresh install. `MISSING`/`OLD` -> APK download -> install.
- APK URL (fixed): `https://github.com/CHAMANRK/CHAMAN-GeckoView-Test/releases/latest/download/CHAMAN-Cookie-Browser.apk`
- Download ke baad install se pehle check: APK package == browser package aur APK ki signature == Downloader ki signature.
- Android chup-chap install nahi karne deta: user ko system install screen pe 1-2 tap + pehli baar "Install unknown apps" allow karna padta hai.
- Install ke baad Downloader wapas front me aate hi (onResume) khud LOGIN intent bhej deta hai.

## Release contract (Browser repo)
- Tag `v*` push -> CI APK ko GitHub Release me `CHAMAN-Cookie-Browser.apk` naam se upload karta hai (naam fixed, kabhi mat badlo).
- Browser CI me do secrets chahiye (Downloader wale hi): `CHAMAN_KEYSTORE_B64`, `CHAMAN_KEYSTORE_PASSWORD` (alias `chaman`). Na hon to debug key, aur Downloader APK reject karega.
- Browser `versionCode` har release me badhao. Downloader `MIN_BROWSER_VERSION` (abhi 3) sirf tab badhao jab handshake toote.

## Site presets (Browser)
| id | start URL | cookie domains (export) | login cookie names |
|---|---|---|---|
| instagram | `https://www.instagram.com/accounts/login/` | `instagram.com` | `sessionid` |
| youtube | `https://www.youtube.com/` | `youtube.com`, `google.com` | `SAPISID`, `__Secure-3PSID`, `SID` |

## Known risks
- Pehli baar Browser v0.2 (debug key) device pe ho to Downloader uninstall prompt dikhayega (alag key). Ek baar ka kaam.
- Instagram cookies rotate/expire ho sakti hain; tab dobara import. yt-dlp ke saath cookies ka asli proof device test me.
- Background se activity start Android block kar sakta hai: install download background me poora ho to Downloader front me aate hi aage badhta hai.

## Changelog
- v1: LOGIN + IMPORT_COOKIES, signature check, install flow, Instagram only.
