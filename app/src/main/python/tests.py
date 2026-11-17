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


def download(url, outdir, cb):
    """Asli download (Phase 0 Batch C, background stability test).

    cb ek Kotlin object hai: cb.onProgress(pct, text), cb.isCancelled().
    yt-dlp ko FFmpeg binary nahi milta (FFmpeg JNI library hai), isliye video
    aur audio alag files me download hote hain; merge Kotlin side se hota hai.
    Returns JSON: {"files": [...], "id", "merge_ext", "desc"}
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
        with yt_dlp.YoutubeDL(dict(base_opts, format=FORMAT_SELECTOR)) as ydl:
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
            cb.onProgress(
                float(min(pct, 100.0)) if pct >= 0 else -1.0,
                "%s %.1f / %.1f MB @ %.0f KB/s" % (kind, done / 1e6, tot / 1e6, speed / 1e3),
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
        {"files": paths, "id": vid, "merge_ext": merge_ext, "desc": desc},
        ensure_ascii=False,
    )
