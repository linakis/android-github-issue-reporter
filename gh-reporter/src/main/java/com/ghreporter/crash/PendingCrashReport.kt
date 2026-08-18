package com.ghreporter.crash

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * A crash captured by [CrashHandler] and persisted to disk, since the process is about to die
 * and any in-memory log collectors ([com.ghreporter.collectors.GHReporterTree] etc.) won't
 * survive to the next launch. Everything needed to file the issue is snapshotted at crash time;
 * nothing here depends on live app state.
 */
@JsonClass(generateAdapter = true)
data class PendingCrashReport(
    @Json(name = "thread_name") val threadName: String,
    @Json(name = "exception_type") val exceptionType: String,
    @Json(name = "exception_message") val exceptionMessage: String?,
    @Json(name = "stack_trace") val stackTrace: String,
    @Json(name = "timber_logs") val timberLogs: String?,
    @Json(name = "device_manufacturer") val deviceManufacturer: String,
    @Json(name = "device_model") val deviceModel: String,
    @Json(name = "android_release") val androidRelease: String,
    @Json(name = "android_sdk_int") val androidSdkInt: Int,
    @Json(name = "app_package") val appPackage: String,
    @Json(name = "app_version_name") val appVersionName: String?,
    @Json(name = "app_version_code") val appVersionCode: Long,
    @Json(name = "crashed_at_epoch_millis") val crashedAtEpochMillis: Long,
) {
    /** A short, single-line title for the issue — the exception type plus its origin. */
    fun issueTitle(): String {
        val origin = stackTrace.lineSequence().drop(1).firstOrNull()?.trim().orEmpty()
        return "Crash: $exceptionType${if (origin.isNotEmpty()) " at $origin" else ""}".take(
            MAX_TITLE_LENGTH,
        )
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 200
    }
}
