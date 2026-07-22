# Launcher-specific ProGuard rules
# Keep all launcher-related classes intact
-keep class com.lightest.launcher.** { *; }

# Keep Android framework classes used by reflection
-keep class android.content.pm.** { *; }
-keep class android.os.UserHandle { *; }

# Keep Compose runtime internals (needed for stability annotations)
-keep @androidx.compose.runtime.Immutable class * { *; }
-keep @androidx.compose.runtime.Stable class * { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Kotlin metadata (required for reflection used by Compose compiler)
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault

# Remove all logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Remove printStackTrace() calls in release (saves stack trace allocation overhead)
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}
