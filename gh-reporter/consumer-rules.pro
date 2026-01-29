# Consumer ProGuard rules for GHReporter SDK
# These rules are automatically applied to apps that consume this library

# Keep GHReporter public API
-keep class com.ghreporter.GHReporter { *; }
-keep class com.ghreporter.GHReporterConfig { *; }
-keep class com.ghreporter.collectors.GHReporterTree { *; }
-keep class com.ghreporter.collectors.GHReporterInterceptor { *; }
