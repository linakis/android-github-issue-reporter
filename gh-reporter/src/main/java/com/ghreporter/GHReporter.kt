package com.ghreporter

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import com.ghreporter.collectors.GHReporterInterceptor
import com.ghreporter.collectors.GHReporterTree
import com.ghreporter.collectors.LogcatCollector
import com.ghreporter.shake.ShakeDetector
import com.ghreporter.ui.GHReporterActivity
import okhttp3.Interceptor
import java.lang.ref.WeakReference

/**
 * Main entry point for GHReporter SDK.
 *
 * Usage:
 * ```kotlin
 * // In Application.onCreate()
 * GHReporter.init(
 *     context = this,
 *     config = GHReporterConfig(
 *         githubOwner = "myorg",
 *         githubRepo = "my-app",
 *         githubClientId = "Iv1.xxxxxxxx" // GitHub OAuth App client ID
 *     )
 * )
 *
 * // Plant the Timber tree
 * Timber.plant(GHReporter.getTimberTree())
 *
 * // Add OkHttp interceptor
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(GHReporter.getOkHttpInterceptor())
 *     .build()
 *
 * // Enable shake to report in Activity
 * GHReporter.enableShakeToReport(activity)
 * ```
 */
object GHReporter {

    private var isInitialized = false
    private lateinit var applicationContext: Context
    private lateinit var config: GHReporterConfig

    private val _timberTree: GHReporterTree by lazy {
        checkInitialized()
        GHReporterTree(config.maxTimberLogEntries)
    }

    private val _okHttpInterceptor: GHReporterInterceptor by lazy {
        checkInitialized()
        GHReporterInterceptor(config.maxOkHttpLogEntries)
    }

    private val _logcatCollector: LogcatCollector by lazy {
        checkInitialized()
        LogcatCollector(config.maxLogcatLines)
    }

    private var shakeDetector: ShakeDetector? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    /**
     * Initialize the GHReporter SDK. Must be called before using any other methods.
     *
     * @param context Application context
     * @param config SDK configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    @JvmStatic
    fun init(context: Context, config: GHReporterConfig) {
        if (isInitialized) {
            return
        }

        // Validate configuration
        validateConfig(config)

        this.applicationContext = context.applicationContext
        this.config = config
        this.isInitialized = true
    }

    /**
     * Validates the SDK configuration.
     */
    private fun validateConfig(config: GHReporterConfig) {
        require(config.githubOwner.isNotBlank()) {
            "githubOwner cannot be empty"
        }
        require(config.githubRepo.isNotBlank()) {
            "githubRepo cannot be empty"
        }
        require(config.githubClientId.isNotBlank()) {
            "githubClientId cannot be empty"
        }
        
        // Check for placeholder values
        if (config.githubOwner in listOf("your-org", "your-username", "REPLACE_ME")) {
            throw IllegalArgumentException(
                "GitHub owner is still set to placeholder value: '${config.githubOwner}'. " +
                "Please update with your actual GitHub username or organization."
            )
        }
        if (config.githubRepo in listOf("your-repo", "REPLACE_ME")) {
            throw IllegalArgumentException(
                "GitHub repo is still set to placeholder value: '${config.githubRepo}'. " +
                "Please update with your actual repository name."
            )
        }
        if (config.githubClientId.startsWith("Iv1.x") || config.githubClientId == "REPLACE_ME") {
            throw IllegalArgumentException(
                "GitHub Client ID is still set to placeholder value. " +
                "To fix this:\n" +
                "1. Go to https://github.com/settings/developers\n" +
                "2. Create a new OAuth App with Device Flow enabled\n" +
                "3. Copy the Client ID and update your GHReporterConfig"
            )
        }
    }

    /**
     * Returns the Timber tree for log collection.
     * Plant this tree to capture logs: `Timber.plant(GHReporter.getTimberTree())`
     */
    @JvmStatic
    fun getTimberTree(): GHReporterTree {
        checkInitialized()
        return _timberTree
    }

    /**
     * Returns the OkHttp interceptor for network log collection.
     * Add to your OkHttpClient: `.addInterceptor(GHReporter.getOkHttpInterceptor())`
     */
    @JvmStatic
    fun getOkHttpInterceptor(): Interceptor {
        checkInitialized()
        return _okHttpInterceptor
    }

    /**
     * Enable shake-to-report functionality for the given activity.
     * Call this in onResume() and call disableShakeToReport() in onPause().
     *
     * @param activity The activity to enable shake detection for
     */
    @JvmStatic
    fun enableShakeToReport(activity: Activity) {
        checkInitialized()

        currentActivityRef = WeakReference(activity)

        if (shakeDetector == null) {
            shakeDetector = ShakeDetector(
                context = activity,
                thresholdG = config.shakeThresholdG,
                cooldownMs = config.shakeCooldownMs
            ) {
                currentActivityRef?.get()?.let { act ->
                    startReporting(act)
                }
            }
        }

        shakeDetector?.start()
    }

    /**
     * Disable shake-to-report functionality.
     * Call this in onPause().
     */
    @JvmStatic
    fun disableShakeToReport() {
        shakeDetector?.stop()
        currentActivityRef = null
    }

    /**
     * Manually trigger the issue reporting UI.
     *
     * @param context Context to launch the activity
     */
    @JvmStatic
    fun startReporting(context: Context) {
        checkInitialized()

        val intent = Intent(context, GHReporterActivity::class.java).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    /**
     * Get the current SDK configuration.
     */
    @JvmStatic
    fun getConfig(): GHReporterConfig {
        checkInitialized()
        return config
    }

    /**
     * Get the application context.
     */
    internal fun getApplicationContext(): Context {
        checkInitialized()
        return applicationContext
    }

    /**
     * Get collected Timber logs.
     */
    internal fun getTimberLogs(): List<GHReporterTree.LogEntry> {
        return if (isInitialized) _timberTree.getLogs() else emptyList()
    }

    /**
     * Get collected OkHttp network logs.
     */
    internal fun getNetworkLogs(): List<GHReporterInterceptor.NetworkLogEntry> {
        return if (isInitialized) _okHttpInterceptor.getLogs() else emptyList()
    }

    /**
     * Collect logcat logs.
     */
    internal suspend fun collectLogcat(): String {
        return if (isInitialized && config.enableLogcat) {
            _logcatCollector.collect()
        } else {
            ""
        }
    }

    /**
     * Clear all collected logs.
     */
    @JvmStatic
    fun clearLogs() {
        if (isInitialized) {
            _timberTree.clear()
            _okHttpInterceptor.clear()
        }
    }

    /**
     * Check if the SDK is initialized.
     */
    @JvmStatic
    fun isInitialized(): Boolean = isInitialized

    private fun checkInitialized() {
        check(isInitialized) {
            "GHReporter is not initialized. Call GHReporter.init() first."
        }
    }
}
