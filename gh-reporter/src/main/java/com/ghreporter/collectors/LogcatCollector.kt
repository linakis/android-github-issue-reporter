package com.ghreporter.collectors

import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Collects logcat output from the current process.
 *
 * Only captures logs from the current process ID for privacy.
 * Runs on IO dispatcher to avoid blocking the main thread.
 */
class LogcatCollector(
    private val maxLines: Int = 500
) {

    /**
     * Collect logcat output from the current process.
     *
     * @return Logcat output as a string
     */
    suspend fun collect(): String = withContext(Dispatchers.IO) {
        try {
            val pid = Process.myPid()
            
            // Use logcat command with:
            // -d: Dump log and exit
            // -v time: Include timestamps
            // --pid: Filter by process ID (Android 7.0+)
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "time", "--pid=$pid")
            )

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = mutableListOf<String>()

            reader.useLines { sequence ->
                sequence.forEach { line ->
                    lines.add(line)
                    // Keep only the last maxLines
                    if (lines.size > maxLines) {
                        lines.removeAt(0)
                    }
                }
            }

            process.waitFor()
            process.destroy()

            lines.joinToString("\n")
        } catch (e: Exception) {
            "Failed to collect logcat: ${e.message}"
        }
    }

    /**
     * Collect logcat with a specific log level filter.
     *
     * @param level Minimum log level (V, D, I, W, E, F, S)
     * @return Filtered logcat output
     */
    suspend fun collectWithLevel(level: String): String = withContext(Dispatchers.IO) {
        try {
            val pid = Process.myPid()
            
            // Filter by level: *:W means all tags at WARN level or above
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "time", "--pid=$pid", "*:$level")
            )

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = mutableListOf<String>()

            reader.useLines { sequence ->
                sequence.forEach { line ->
                    lines.add(line)
                    if (lines.size > maxLines) {
                        lines.removeAt(0)
                    }
                }
            }

            process.waitFor()
            process.destroy()

            lines.joinToString("\n")
        } catch (e: Exception) {
            "Failed to collect logcat: ${e.message}"
        }
    }

    /**
     * Collect logcat entries matching a specific tag.
     *
     * @param tag The log tag to filter by
     * @return Filtered logcat output
     */
    suspend fun collectByTag(tag: String): String = withContext(Dispatchers.IO) {
        try {
            val pid = Process.myPid()
            
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "time", "--pid=$pid", "-s", tag)
            )

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = mutableListOf<String>()

            reader.useLines { sequence ->
                sequence.forEach { line ->
                    lines.add(line)
                    if (lines.size > maxLines) {
                        lines.removeAt(0)
                    }
                }
            }

            process.waitFor()
            process.destroy()

            lines.joinToString("\n")
        } catch (e: Exception) {
            "Failed to collect logcat: ${e.message}"
        }
    }

    /**
     * Clear the logcat buffer for this process.
     * Note: This requires elevated permissions and may not work on all devices.
     */
    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-c"))
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        // Log level constants
        const val VERBOSE = "V"
        const val DEBUG = "D"
        const val INFO = "I"
        const val WARN = "W"
        const val ERROR = "E"
        const val FATAL = "F"
        const val SILENT = "S"
    }
}
