"""Phase 0 feasibility tests. Har function ek string return karta hai
ya exception raise karta hai (Kotlin side usse FAIL + log me dikhata hai)."""
import json
import platform
import sys


def env_info():
    return "Python {} | arch={} | platform={}".format(
        platform.python_version(), platform.machine(), sys.platform
    )


def ytdlp_version():
    import yt_dlp.version as v

    return "yt-dlp {}".format(v.__version__)


def yt_info(url):
    """Sirf metadata extract, koi download nahi."""
    import yt_dlp

    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "socket_timeout": 20,
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)

    fmts = info.get("formats") or []
    heights = [f.get("height") or 0 for f in fmts]
    result = {
        "title": info.get("title"),
        "duration_sec": info.get("duration"),
        "uploader": info.get("uploader"),
        "formats_count": len(fmts),
        "max_height": max(heights) if heights else 0,
        "has_audio_only": any(
            f.get("vcodec") == "none" and f.get("acodec") not in (None, "none")
            for f in fmts
        ),
    }
    return json.dumps(result, ensure_ascii=False, indent=2)


# Best mp4 video + best m4a audio (alag files, Kotlin FFmpegKit se merge karega);
# fallback: koi bhi video+audio, phir single pre-merged stream.
FORMAT_SELECTOR = "bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b"


# ---------------- YouTube bot-check: player client fallback (Step E) ----------------
# Data/shared IP pe YouTube default client se formats nahi deta ("Sign in to confirm you're not a bot").
# Alag player client chain me try hote hain; jo chalta hai wahi us URL ke liye yaad rehta hai
# (download() ko wahi format ids chahiye jo search() ne dikhaye). Order badalna ho to sirf ye list.
_YT_CLIENTS = [None, "tv", "android_vr", "web_embedded"]  # None = yt-dlp ka default
_client_memo = {}  # url -> client jo kaam kiya

BOT_MSG = "YouTube ne is network ko rok diya (bot-check). Wi-Fi try karo ya thodi der baad dubara."
NO_FORMATS_MSG = "YouTube ne is video ke formats nahi diye (bot-check ya restricted video). Wi-Fi try karo ya thodi der baad dubara."


_cookie_dir = None


def set_cookie_dir(path):
    """Kotlin app start par cookie folder batata hai (filesDir/cookies)."""
    global _cookie_dir
    _cookie_dir = path
    return "ok"


def _cookie_opts(url):
    """Instagram link + import ki hui cookies ho to yt-dlp ko cookiefile (copy) do. Values kabhi log nahi hoti."""
    import os
    import shutil

    if "instagram.com" not in url.lower() or not _cookie_dir:
        return {}
    src = os.path.join(_cookie_dir, "instagram.txt")
    if not os.path.isfile(src):
        return {}
    run_dir = os.path.join(_cookie_dir, "run")
    os.makedirs(run_dir, exist_ok=True)
    dst = os.path.join(run_dir, "instagram.txt")
    shutil.copyfile(src, dst)  # yt-dlp close par file wapas likhta hai: original ko chhoona nahi
    return {"cookiefile": dst}


def _is_youtube(url):
    u = url.lower()
    return "youtube.com" in u or "youtu.be" in u


def _is_bot_check(msg):
    m = str(msg).lower()
    return "not a bot" in m and "sign in" in m


def _client_order(url):
    """Non-YouTube: sirf default. YouTube: yaad rakha client pehle, phir baaki."""
    if not _is_youtube(url):
        return [None]
    order = list(_YT_CLIENTS)
    if url in _client_memo:
        c = _client_memo[url]
        if c in order:
            order.remove(c)
        order.insert(0, c)
    return order


def _with_client(opts, client):
    if client is None:
        return dict(opts)
    return dict(opts, extractor_args={"youtube": {"player_client": [client]}})


def _friendly(err):
    """Bot-check ho to saaf message (details ke saath), warna original error."""
    if _is_bot_check(err):
        return RuntimeError(BOT_MSG + "\n\nDetails: " + str(err))
    return err


def _formats_summary(url, base_opts):
    """Format select fail ho to diagnose ke liye available formats ki short list."""
    import yt_dlp

    opts = dict(base_opts, skip_download=True, ignore_no_formats_error=True)
    opts.pop("format", None)
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
    lines = []
    for f in (info.get("formats") or [])[:40]:
        lines.append(
            "{} {} {}p v={} a={}".format(
                f.get("format_id"),
                f.get("ext"),
                f.get("height") or "-",
                f.get("vcodec"),
                f.get("acodec"),
            )
        )
    return "\n".join(lines) if lines else "(koi format nahi mila)"


def _download_impl(url, outdir, cb, fmt=""):
    """Asli download (Phase 0 Batch C, background stability test).

    cb ek Kotlin object hai: cb.onStats(pct, kind, done, total, speed, eta), cb.isCancelled().
    yt-dlp ko FFmpeg binary nahi milta (FFmpeg JNI library hai), isliye video
    aur audio alag files me download hote hain; merge Kotlin side se hota hai.
    fmt: yt-dlp format string ("137+140", "140" ...); khaali = FORMAT_SELECTOR (best).
    Returns JSON: {"files": [...], "id", "title", "merge_ext", "desc", "has_video"}
      files me 1 path (single stream) ya 2 paths [video, audio].
    """
    import yt_dlp

    # Step M: "mp3|<format id>" = audio download, mp3 conversion Kotlin (FFmpeg) karega.
    to_mp3 = fmt.startswith("mp3|")
    if to_mp3:
        fmt = fmt[4:]

    base_opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "socket_timeout": 30,
        "retries": 5,
        "fragment_retries": 5,
    }
    base_opts.update(_cookie_opts(url))

    info = None
    client_used = None
    last_err = None
    for c in _client_order(url):
        try:
            with yt_dlp.YoutubeDL(_with_client(dict(base_opts, format=fmt or FORMAT_SELECTOR), c)) as ydl:
                info = ydl.extract_info(url, download=False)
            client_used = c
            if _is_youtube(url):
                _client_memo[url] = c
            break
        except yt_dlp.utils.YoutubeDLError as e:
            last_err = e
    if info is None:
        if _is_bot_check(last_err):
            raise _friendly(last_err)
        if "format" in str(last_err).lower():
            raise RuntimeError(
                str(last_err) + "\nAvailable formats:\n"
                + _formats_summary(url, _with_client(base_opts, _client_memo.get(url)))
            )
        raise last_err
    base_opts = _with_client(base_opts, client_used)

    vid = info["id"]
    reqs = info.get("requested_formats") or [info]
    n = len(reqs)
    sizes = [(f.get("filesize") or f.get("filesize_approx") or 0) for f in reqs]
    total_all = sum(sizes)

    def kind_of(f):
        if n == 1:
            return "single"
        return "video" if f.get("vcodec") not in (None, "none") else "audio"

    paths = []
    done_before = 0
    for i, f in enumerate(reqs):
        kind = kind_of(f)

        def hook(d, i=i, kind=kind, offset=done_before):
            if cb.isCancelled():
                raise yt_dlp.utils.DownloadCancelled("cancelled by user")
            if d.get("status") != "downloading":
                return
            done = d.get("downloaded_bytes") or 0
            tot = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            if total_all:
                pct = (offset + done) * 100.0 / total_all
            elif tot:
                pct = (i + done / float(tot)) * 100.0 / n
            else:
                pct = -1.0
            speed = d.get("speed") or 0
            eta = d.get("eta")
            cb.onStats(
                float(min(pct, 100.0)) if pct >= 0 else -1.0,
                kind,
                float(done),
                float(tot),
                float(speed),
                float(eta) if eta is not None else -1.0,
            )

        opts = dict(
            base_opts,
            format=f["format_id"],
            outtmpl="%s/%s.%s.%%(ext)s" % (outdir, vid, kind),
            progress_hooks=[hook],
        )
        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                info2 = ydl.extract_info(url, download=True)
                paths.append(ydl.prepare_filename(info2))
        except yt_dlp.utils.DownloadError as e:
            raise _friendly(e)
        done_before += sizes[i]

    if n == 2:
        vext = reqs[0].get("ext")
        aext = reqs[1].get("ext")
        merge_ext = "mp4" if (vext == "mp4" and aext in ("m4a", "mp4")) else "mkv"
    else:
        merge_ext = reqs[0].get("ext") or "mp4"

    desc = " + ".join(
        "{} {}p {} {}".format(
            kind_of(f), _q(f) or "-", f.get("vcodec"), f.get("acodec")
        )
        for f in reqs
    )
    if _is_youtube(url):
        desc += " [client=%s]" % (client_used or "default")
    return json.dumps(
        {"files": paths, "id": vid, "title": info.get("title") or vid, "merge_ext": merge_ext, "desc": desc,
         "has_video": any(f.get("vcodec") not in (None, "none") for f in reqs) and not to_mp3,
         "to_mp3": to_mp3},
        ensure_ascii=False,
    )


# ---------------- Step B: Search (poori detail + quality options) ----------------

def _has(c):
    return c not in (None, "none")


def _vlabel(vc):
    v = (vc or "").lower()
    if v.startswith("avc"):
        return "H.264"
    if v.startswith(("vp09", "vp9")):
        return "VP9"
    if v.startswith("av01"):
        return "AV1"
    if v.startswith(("hev", "hvc")):
        return "H.265"
    return v.split(".")[0] or "video"


def _q(f):
    """Quality number: chhoti side (vertical Shorts 1080x1920 = 1080p, height 1920 nahi)."""
    h = f.get("height")
    w = f.get("width")
    return min(h, w) if (h and w) else h


def _fsize(f):
    return int(f.get("filesize") or f.get("filesize_approx") or 0)


def _build_options(fmts):
    """formats list -> (videos, audios). Har height ke liye ek best option.
    fmt string wahi hai jo download(url, outdir, cb, fmt) ko jaata hai."""
    audio = [f for f in fmts if not _has(f.get("vcodec")) and _has(f.get("acodec"))]

    def abr(f):
        return f.get("abr") or f.get("tbr") or 0

    m4a = [f for f in audio if f.get("ext") == "m4a"]
    best_m4a = max(m4a, key=abr) if m4a else None
    best_any = max(audio, key=abr) if audio else None

    video = [f for f in fmts if _has(f.get("vcodec")) and f.get("height")]
    by_h = {}
    for f in video:
        key = (f.get("ext") == "mp4", f.get("tbr") or 0)
        q = _q(f)
        cur = by_h.get(q)
        if cur is None or key > (cur.get("ext") == "mp4", cur.get("tbr") or 0):
            by_h[q] = f

    videos = []
    for h in sorted(by_h, reverse=True):
        f = by_h[h]
        if _has(f.get("acodec")):  # progressive: audio saath me
            a = None
        else:
            a = best_m4a if (f.get("ext") == "mp4" and best_m4a) else best_any
        fps = f.get("fps")
        label = "%dp" % h + ("%d" % fps if fps and fps > 30 else "")
        if a is not None:
            out_ext = "mp4" if (f.get("ext") == "mp4" and a.get("ext") in ("m4a", "mp4")) else "mkv"
        else:
            out_ext = f.get("ext") or "mp4"
        videos.append(
            {
                "label": label,
                "detail": "%s, %s" % (_vlabel(f.get("vcodec")), out_ext),
                "size": _fsize(f) + (_fsize(a) if a else 0),
                "fmt": f["format_id"] + (("+" + a["format_id"]) if a else ""),
            }
        )

    audios = []
    for ext, name in (("m4a", "Audio m4a (AAC)"), ("webm", "Audio webm (Opus)")):
        c = [f for f in audio if f.get("ext") == ext]
        if not c:
            continue
        f = max(c, key=abr)
        audios.append(
            {
                "label": name,
                "detail": ("%d kbps" % abr(f)) if abr(f) else ext,
                "size": _fsize(f),
                "fmt": f["format_id"],
            }
        )
    return videos, audios


def _search_impl(url):
    """Metadata + quality options (download nahi). JSON return."""
    import yt_dlp

    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "ignore_no_formats_error": True,
        "socket_timeout": 20,
    }
    opts.update(_cookie_opts(url))
    info = None
    client_used = None
    last_err = None
    last_info = None
    for c in _client_order(url):
        try:
            with yt_dlp.YoutubeDL(_with_client(opts, c)) as ydl:
                got = ydl.extract_info(url, download=False)
        except yt_dlp.utils.YoutubeDLError as e:
            last_err = e
            continue
        if _is_youtube(url) and not (got.get("formats") or []):
            last_info = got  # title mila par formats nahi: agla client
            continue
        info = got
        client_used = c
        if _is_youtube(url):
            _client_memo[url] = c
        break
    if info is None:
        if last_info is None and last_err is not None:
            raise _friendly(last_err)
        raise RuntimeError(NO_FORMATS_MSG)

    videos, audios = _build_options(info.get("formats") or [])
    desc = info.get("description") or ""
    result = {
        "title": info.get("title"),
        "uploader": info.get("uploader") or info.get("channel"),
        "duration_sec": info.get("duration"),
        "upload_date": info.get("upload_date"),
        "view_count": info.get("view_count"),
        "like_count": info.get("like_count"),
        "site": info.get("extractor_key") or info.get("extractor"),
        "description": desc[:1500],
        "thumbnail": info.get("thumbnail"),
        "client": client_used or "default",
        "videos": videos,
        "audios": audios,
    }
    return json.dumps(result, ensure_ascii=False)


# ---------------- v0.4.20: Instagram cookies diagnostic ----------------
# Instagram link fail ho to error ke neeche ek line: file/sessionid/loaded count + session check HTTP code.
# Cookie values kabhi output me nahi aati (sirf count, haan/nahi, status code).

_IG_CHECK_URL = "https://www.instagram.com/api/v1/accounts/current_user/?edit=true"
_IG_APP_ID = "936619743392459"
_IG_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"


def _read_cookie_pairs(path):
    """Netscape file -> [(name, value)] instagram.com cookies. Parse fail ho to bhi crash nahi."""
    pairs = []
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.rstrip("\r\n")
            if line.startswith("#HttpOnly_"):
                line = line[len("#HttpOnly_"):]
            elif line.startswith("#") or not line.strip():
                continue
            f = line.split("\t")
            if len(f) != 7:
                continue
            if "instagram.com" not in f[0]:
                continue
            pairs.append((f[5], f[6]))
    return pairs


_FF_UA = "Mozilla/5.0 (Android 14; Mobile; rv:132.0) Gecko/132.0 Firefox/132.0"


def _short_word(v):
    """Server ka message chhota aur safe banao (sirf a-z 0-9 _ space, 30 char). Cookie value nahi aati."""
    import re

    return re.sub(r"[^A-Za-z0-9_ ]", "", str(v))[:30].strip()


def _session_check(pairs, ua=None):
    """current_user API ko cookies ke saath ek request. 'HTTP <code> (<message>)' ya 'error <Name>' return."""
    import urllib.error
    import urllib.request

    class _NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *a, **k):
            return None

    names = dict(pairs)
    headers = {
        "User-Agent": ua or _IG_UA,
        "X-IG-App-ID": _IG_APP_ID,
        "Cookie": "; ".join("%s=%s" % (n, v) for n, v in pairs),
        "Accept": "*/*",
    }
    if "csrftoken" in names:
        headers["X-CSRFToken"] = names["csrftoken"]
    req = urllib.request.Request(_IG_CHECK_URL, headers=headers)

    def word(body):
        try:
            d = json.loads(body.decode("utf-8", "replace"))
            w = _short_word(d.get("message") or d.get("error_type") or d.get("status") or "")
            return " (%s)" % w if w else ""
        except Exception:
            return ""

    try:
        opener = urllib.request.build_opener(_NoRedirect)
        with opener.open(req, timeout=10) as r:
            return "HTTP %d%s" % (r.status, word(r.read(4096)))
    except urllib.error.HTTPError as e:
        try:
            body = e.read(4096)
        except Exception:
            body = b""
        return "HTTP %d%s" % (e.code, word(body))
    except Exception as e:
        return "error %s" % type(e).__name__


def _cookie_diag(url):
    """Instagram URL ke liye diagnostic line, warna None. Kabhi exception nahi uthata."""
    import os

    try:
        if "instagram.com" not in url.lower():
            return None
        if not _cookie_dir:
            return "cookies: folder set nahi hua"
        src = os.path.join(_cookie_dir, "instagram.txt")
        if not os.path.isfile(src):
            return "cookies: file nahi mili, 0 loaded, sessionid: nahi, session check: skip"
        pairs = _read_cookie_pairs(src)
        has_sid = any(n == "sessionid" and v for n, v in pairs)
        has_uid = any(n == "ds_user_id" and v for n, v in pairs)
        check = _session_check(pairs) if pairs else "skip"
        check_ff = _session_check(pairs, _FF_UA) if pairs else "skip"
        return "cookies: file mili, %d loaded, sessionid: %s, ds_user_id: %s, session check: %s, firefox-UA: %s" % (
            len(pairs), "haan" if has_sid else "nahi", "haan" if has_uid else "nahi", check, check_ff
        )
    except Exception as e:
        return "cookies: diag error %s" % type(e).__name__


def _with_diag(url, fn, *args):
    import yt_dlp

    try:
        return fn(*args)
    except yt_dlp.utils.DownloadCancelled:
        raise
    except Exception as e:
        d = _cookie_diag(url)
        if d is None:
            raise
        raise RuntimeError("%s\n%s" % (e, d)) from None


def search(url):
    return _with_diag(url, _search_impl, url)


def download(url, outdir, cb, fmt=""):
    return _with_diag(url, _download_impl, url, outdir, cb, fmt)
