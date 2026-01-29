# GHReporter - Android GitHub Issue Reporter SDK

An Android SDK for reporting GitHub issues with shake-to-report functionality. Automatically collects logs from Timber, OkHttp, and Logcat, uploads them as private GitHub Gists, and creates GitHub issues.

## Features

- **Shake-to-Report**: Shake detection triggers the issue reporter
- **Log Collection**: Captures Timber logs, OkHttp network requests, and Logcat
- **Private Gists**: Logs are uploaded as private GitHub Gists (linked in issue body)
- **GitHub OAuth**: Device Flow authentication (no Firebase required)
- **Jetpack Compose UI**: Modern Material3 design
- **Screenshot Support**: Attach screenshots to reports
- **Customizable**: Configure labels, log limits, shake sensitivity

## Installation

### Option 1: JitPack (Recommended)

[![](https://jitpack.io/v/linakis/android-github-issue-reporter.svg)](https://jitpack.io/#linakis/android-github-issue-reporter)

**Step 1:** Add JitPack repository to your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Or if using the older `build.gradle` (project level):

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2:** Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.linakis:android-github-issue-reporter:1.0.0")
}
```

Or with Groovy DSL:

```groovy
dependencies {
    implementation 'com.github.linakis:android-github-issue-reporter:1.0.0'
}
```

> **Note:** Replace `1.0.0` with the latest release version or use `main-SNAPSHOT` for the latest development build.

### Option 2: Local Module

If you prefer to include the SDK as a local module:

```kotlin
// settings.gradle.kts
include(":gh-reporter")

// app/build.gradle.kts
dependencies {
    implementation(project(":gh-reporter"))
}
```

## Setup

### 1. Create a GitHub OAuth App

The `githubClientId` is a unique identifier for your OAuth application that allows users to authenticate with GitHub. Here's how to get one:

1. Go to [GitHub Developer Settings > OAuth Apps](https://github.com/settings/developers)
2. Click **"New OAuth App"** (or "Register a new application")
3. Fill in the required fields:

   | Field | Value | Example |
   |-------|-------|---------|
   | **Application name** | Your app's name | `My App Issue Reporter` |
   | **Homepage URL** | Your app or company website | `https://myapp.com` |
   | **Application description** | Optional description | `Issue reporting for My App` |
   | **Authorization callback URL** | Use the Device Flow URL | `https://github.com/login/device` |

4. Click **"Register application"**
5. On the next page, copy the **Client ID** - it looks like `Iv1.a1b2c3d4e5f6g7h8`

> **Important:** You do NOT need the Client Secret. The SDK uses GitHub's [Device Flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow) which is designed for apps that can't securely store secrets (like mobile apps).

#### Enable Device Flow (Required)

After creating the OAuth App, you must enable Device Flow:

1. On your OAuth App's settings page, scroll down
2. Check **"Enable Device Flow"**
3. Click **"Update application"**

### 2. Initialize in Application

```kotlin
class MyApp : Application() {
    
    lateinit var okHttpClient: OkHttpClient
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // 1. Initialize GHReporter
        GHReporter.init(
            context = this,
            config = GHReporterConfig(
                githubOwner = "your-org",           // GitHub username or organization
                githubRepo = "your-repo",           // Repository name where issues will be created
                githubClientId = "Iv1.a1b2c3d4e5",  // OAuth App Client ID from step 1
                defaultLabels = listOf("bug", "from-app"),
                maxTimberLogEntries = 500,
                maxOkHttpLogEntries = 50,
                maxLogcatLines = 500
            )
        )
        
        // 2. Plant Timber tree for log collection
        Timber.plant(Timber.DebugTree())
        Timber.plant(GHReporter.getTimberTree())
        
        // 3. Create OkHttpClient with GHReporter interceptor
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(GHReporter.getOkHttpInterceptor())
            .build()
    }
}
```

### 3. Enable Shake Detection in Activities

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onResume() {
        super.onResume()
        GHReporter.enableShakeToReport(this)
    }
    
    override fun onPause() {
        super.onPause()
        GHReporter.disableShakeToReport()
    }
}
```

### 4. Manual Reporting (Optional)

```kotlin
// Trigger report manually
button.setOnClickListener {
    GHReporter.startReporting(context)
}
```

## Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `githubOwner` | String | Required | GitHub username or organization |
| `githubRepo` | String | Required | Repository name |
| `githubClientId` | String | Required | GitHub OAuth App client ID |
| `maxTimberLogEntries` | Int | 500 | Max Timber logs to keep |
| `maxOkHttpLogEntries` | Int | 50 | Max network requests to keep |
| `maxLogcatLines` | Int | 500 | Max logcat lines to capture |
| `enableLogcat` | Boolean | true | Enable logcat collection |
| `defaultLabels` | List | [] | Labels applied to all issues |
| `shakeThresholdG` | Float | 2.7 | Shake sensitivity (G-force) |
| `shakeCooldownMs` | Long | 1000 | Cooldown between detections |
| `includeDeviceInfo` | Boolean | true | Include device info in issue |
| `includeAppInfo` | Boolean | true | Include app version in issue |

## Authentication

GHReporter uses GitHub's [Device Flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow) for authentication:

1. User clicks "Sign in with GitHub"
2. SDK requests a device code from GitHub
3. User code is displayed (e.g., "ABCD-1234")
4. User opens github.com/login/device in browser
5. User enters the code
6. SDK polls until authentication completes
7. Access token is stored securely

**Benefits of Device Flow:**
- No client secret needed in the app
- No redirect URI handling required
- Works on any device

## Required OAuth Scopes

The SDK requests these GitHub OAuth scopes:
- `repo` - Create issues in private/public repos
- `gist` - Create private gists for logs
- `read:user` - Read user profile
- `user:email` - Read user email

## Architecture

```
gh-reporter/
├── GHReporter.kt           # Main entry point singleton
├── GHReporterConfig.kt     # Configuration data class
├── auth/
│   ├── GitHubAuthManager   # Device Flow OAuth
│   └── SecureTokenStorage  # EncryptedSharedPreferences
├── collectors/
│   ├── GHReporterTree      # Timber log collector
│   ├── GHReporterInterceptor # OkHttp interceptor
│   └── LogcatCollector     # Logcat collector
├── api/
│   ├── GitHubApiClient     # Retrofit setup
│   ├── GistService         # Private Gist creation
│   └── IssueService        # Issue creation
├── shake/
│   └── ShakeDetector       # Accelerometer detection
└── ui/
    ├── GHReporterActivity  # Main UI orchestration
    ├── ReporterViewModel   # State management
    ├── screens/
    │   ├── LoginScreen     # Device Flow UI
    │   └── IssueFormScreen # Issue form
    └── theme/
        └── Theme.kt        # Material3 theming
```

## Requirements

- Min SDK: 24 (Android 7.0)
- Target SDK: 35
- Kotlin 2.0+
- Jetpack Compose

## License

MIT License
