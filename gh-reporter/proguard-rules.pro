# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep GHReporter public API
-keep class com.ghreporter.GHReporter { *; }
-keep class com.ghreporter.GHReporterConfig { *; }
-keep class com.ghreporter.collectors.GHReporterTree { *; }
-keep class com.ghreporter.collectors.GHReporterInterceptor { *; }

# Keep API models
-keep class com.ghreporter.api.models.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Moshi
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
