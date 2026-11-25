package com.chaman.downloader

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Process ke saath Python start. Pehle sirf MainActivity start karti thi, to Nap ke bind (chaman-link) se app band hone par
 * process bina Activity ke banta tha aur download me "Cannot use GenericPlatform on Android" aata tha (v0.4.32-link).
 * Ab har entry point (Activity, LinkService, DownloadService) se pehle Python ready hota hai.
 */
class ChamanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}
