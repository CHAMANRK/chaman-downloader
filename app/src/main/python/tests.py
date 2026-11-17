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


def download(url, outdir, cb, fmt=""):
    """Asli download (Phase 0 Batch C, background stability test).

    cb ek Kotlin object hai: cb.onStats(pct, kind, done, total, speed, eta), cb.isCancelled().
    yt-dlp ko FFmpeg binary nahi milta (FFmpeg JNI library hai), isliye video
    aur audio alag files me download hote hain; merge Kotlin side se hota hai.
    fmt: yt-dlp format string ("137+140", "140" ...); khaali = FORMAT_SELECTOR (best).
    Returns JSON: {"files": [...], "id", "title", "merge_ext", "desc", "has_video"}
      files me 1 path (single stream) ya 2 paths [video, audio].
    """
    import yt_dlp

    base_opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "socket_timeout": 30,
        "retries": 5,
        "fragment_retries": 5,
    }

    try:
        with yt_dlp.YoutubeDL(dict(base_opts, format=fmt or FORMAT_SELECTOR)) as ydl:
            info = ydl.extract_info(url, download=False)
    except yt_dlp.utils.DownloadError as e:
        if "format" in str(e).lower():
            raise RuntimeError(
                str(e) + "\nAvailable formats:\n" + _formats_summary(url, base_opts)
            )
        raise

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
        with yt_dlp.YoutubeDL(opts) as ydl:
            info2 = ydl.extract_info(url, download=True)
            paths.append(ydl.prepare_filename(info2))
        done_before += sizes[i]

    if n == 2:
        vext = reqs[0].get("ext")
        aext = reqs[1].get("ext")
        merge_ext = "mp4" if (vext == "mp4" and aext in ("m4a", "mp4")) else "mkv"
    else:
        merge_ext = reqs[0].get("ext") or "mp4"

    desc = " + ".join(
        "{} {}p {} {}".format(
            kind_of(f), f.get("height") or "-", f.get("vcodec"), f.get("acodec")
        )
        for f in reqs
    )
    return json.dumps(
        {"files": paths, "id": vid, "title": info.get("title") or vid, "merge_ext": merge_ext, "desc": desc,
         "has_video": any(f.get("vcodec") not in (None, "none") for f in reqs)},
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
        cur = by_h.get(f["height"])
        if cur is None or key > (cur.get("ext") == "mp4", cur.get("tbr") or 0):
            by_h[f["height"]] = f

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


def search(url):
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
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)

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
        "videos": videos,
        "audios": audios,
    }
    return json.dumps(result, ensure_ascii=False)
