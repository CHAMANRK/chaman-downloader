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


def download(url, outdir, cb):
    """Asli download (Phase 0 Batch C, background stability test).

    cb ek Kotlin object hai: cb.onProgress(pct, text), cb.isCancelled().
    Format abhi single pre-merged stream (best mp4), kyunki yt-dlp ko
    FFmpeg binary nahi milta (FFmpeg JNI library hai). Video+audio merge
    Kotlin side se FFmpegKit se hoga (Phase 2/4).
    Returns: downloaded file ka absolute path.
    """
    import yt_dlp

    def hook(d):
        if cb.isCancelled():
            raise yt_dlp.utils.DownloadCancelled("cancelled by user")
        if d.get("status") == "downloading":
            done = d.get("downloaded_bytes") or 0
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            pct = (done * 100.0 / total) if total else -1.0
            speed = d.get("speed") or 0
            cb.onProgress(
                float(pct),
                "%.1f / %.1f MB @ %.0f KB/s" % (done / 1e6, total / 1e6, speed / 1e3),
            )

    opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "format": "best[ext=mp4]/best",
        "outtmpl": outdir + "/%(id)s.%(ext)s",
        "progress_hooks": [hook],
        "socket_timeout": 30,
        "retries": 5,
        "fragment_retries": 5,
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=True)
        return ydl.prepare_filename(info)
