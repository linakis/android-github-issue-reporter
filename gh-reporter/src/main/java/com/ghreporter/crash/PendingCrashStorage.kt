package com.ghreporter.crash

import android.content.Context
import com.squareup.moshi.Moshi
import java.io.File

/**
 * Persists at most one [PendingCrashReport] to app-private storage across process death.
 * Deliberately synchronous, plain-file I/O — [CrashHandler] calls [save] on the crashing
 * thread right before the process dies, where there's no time or safety margin for anything
 * async (a coroutine dispatcher, a DB, even SharedPreferences' apply()-then-maybe-lose-it
 * semantics are all riskier here than a direct blocking file write).
 *
 * Only one pending crash is kept at a time: if the app crashes repeatedly before the user
 * next launches it successfully, only the first crash of that run is reported — avoids
 * flooding the issue tracker with duplicates of what's likely the same root cause.
 */
internal class PendingCrashStorage(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(PendingCrashReport::class.java)

    /** Persists [report], overwriting anything already pending. Synchronous, crash-safe. */
    fun save(report: PendingCrashReport) {
        try {
            file.writeText(adapter.toJson(report))
        } catch (_: Exception) {
            // Best-effort: if we can't even write the crash file, there's nothing further to
            // do from inside an exception handler that's already run out of grace period.
        }
    }

    /** Reads the pending crash report, if any. Does not delete it — call [clear] after handling. */
    fun load(): PendingCrashReport? {
        if (!file.exists()) return null
        return try {
            adapter.fromJson(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    /** Removes the pending crash report so it isn't reported again on a future launch. */
    fun clear() {
        file.delete()
    }

    private companion object {
        const val FILE_NAME = "ghreporter_pending_crash.json"
    }
}
