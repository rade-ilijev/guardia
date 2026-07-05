package com.guardia.app.core.system

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opt-in, fully on-device crash log. Because Guardia ships no analytics/Crashlytics (the privacy
 * promise), field crashes would otherwise be invisible. When the user turns this on, uncaught
 * exceptions are appended to a private file the user can read, share, or clear from Settings.
 * Nothing is ever uploaded.
 */
object CrashLogger {

    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_BYTES = 256 * 1024L

    /** Flipped by [GuardiaApp] from the stored preference; checked at crash time without coroutines. */
    @Volatile var enabled: Boolean = false

    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (enabled) runCatching { append(appContext, thread, throwable) }
            // Always defer to the platform handler so the app still crashes/reports normally.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun append(context: Context, thread: Thread, throwable: Throwable) {
        val file = logFile(context)
        if (file.exists() && file.length() > MAX_BYTES) file.delete()
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        file.appendText(
            buildString {
                append("=== ").append(stamp).append(" (thread: ").append(thread.name).append(") ===\n")
                append("App ").append(appVersion(context)).append('\n')
                append(stack).append('\n')
            },
        )
    }

    fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun read(context: Context): String =
        runCatching { logFile(context).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

    fun clear(context: Context) {
        runCatching { logFile(context).delete() }
    }

    private fun appVersion(context: Context): String = runCatching {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        "${pkg.versionName} (${context.packageName})"
    }.getOrDefault(context.packageName)
}
