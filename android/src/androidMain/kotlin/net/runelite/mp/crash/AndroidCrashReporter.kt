package net.runelite.mp.crash

import android.content.Context
import android.util.Log

/**
 * Hardened build: Firebase Crashlytics has been removed. Crash info stays on the device
 * (logcat only); nothing is uploaded anywhere. The public API is kept so callers are
 * unchanged.
 */
object AndroidCrashReporter {

    private const val TAG = "CrashReporter"

    fun install(context: Context) {
        Log.i(TAG, "crash reporting is local-only in this build")
    }

    fun breadcrumb(line: String) {
        // Intentionally a no-op: launcher lines already go to logcat via logLine().
    }

    fun nonFatal(throwable: Throwable) {
        Log.w(TAG, "non-fatal", throwable)
    }

    fun forceCrash(message: String = "AndroidCrashReporter forceCrash() smoke test") {
        Thread({ throw RuntimeException(message) }, "CrashSmokeTest").start()
    }
}
