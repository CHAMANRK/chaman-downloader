"""Phase 0 Batch A feasibility tests. Har function ek string return karta hai
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
