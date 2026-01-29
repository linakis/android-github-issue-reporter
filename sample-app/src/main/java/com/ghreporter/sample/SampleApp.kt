package com.ghreporter.sample

import android.app.Application
import com.ghreporter.GHReporter
import com.ghreporter.GHReporterConfig
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Sample application demonstrating GHReporter SDK integration.
 */
class SampleApp : Application() {

    lateinit var okHttpClient: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()

        // Validate credentials are configured in local.properties
        validateCredentials()

        // 1. Initialize GHReporter
        GHReporter.init(
            context = this,
            config = GHReporterConfig(
                githubOwner = BuildConfig.GITHUB_OWNER,
                githubRepo = BuildConfig.GITHUB_REPO,
                githubClientId = BuildConfig.GITHUB_CLIENT_ID,
                defaultLabels = listOf("bug", "from-app"),
                maxTimberLogEntries = 500,
                maxOkHttpLogEntries = 50,
                maxLogcatLines = 500,
                shakeThresholdG = 2.7f,
                shakeCooldownMs = 1000L,
                includeDeviceInfo = true,
                includeAppInfo = true
            )
        )

        // 2. Plant Timber tree for log collection
        Timber.plant(Timber.DebugTree())
        Timber.plant(GHReporter.getTimberTree())

        // 3. Create OkHttpClient with GHReporter interceptor
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(GHReporter.getOkHttpInterceptor())
            .build()

        Timber.i("SampleApp initialized with GHReporter SDK")
    }

    /**
     * Validates that required GHReporter credentials are configured in local.properties.
     * Throws IllegalStateException with helpful instructions if any are missing.
     */
    private fun validateCredentials() {
        val missingFields = mutableListOf<String>()

        if (BuildConfig.GITHUB_OWNER.isBlank()) missingFields.add("ghreporter.owner")
        if (BuildConfig.GITHUB_REPO.isBlank()) missingFields.add("ghreporter.repo")
        if (BuildConfig.GITHUB_CLIENT_ID.isBlank()) missingFields.add("ghreporter.clientId")

        if (missingFields.isNotEmpty()) {
            throw IllegalStateException(
                """
                |
                |========================================
                | GHReporter Sample App - Configuration Required
                |========================================
                |
                | Missing properties in local.properties:
                |   ${missingFields.joinToString("\n|   ")}
                |
                | To fix this:
                | 1. Copy local.properties.example to local.properties
                | 2. Fill in your GitHub OAuth App credentials
                |
                | Example local.properties:
                |   ghreporter.owner=your-github-username
                |   ghreporter.repo=your-repo-name
                |   ghreporter.clientId=your-oauth-app-client-id
                |
                | See README.md for setup instructions.
                |========================================
                """.trimMargin()
            )
        }
    }
}
