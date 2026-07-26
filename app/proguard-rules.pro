# Lightest Launcher — minimal keep rules so R8 can shrink aggressively.
# Do NOT blanket-keep the whole package; Compose models are not reflected.

# Framework types touched via system services (safe no-ops if unused after shrink)
-keep class android.os.UserHandle { *; }

# Strip logging / stack traces in release (zero runtime cost, smaller dex)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# Kotlin coroutines (library consumer rules usually cover this; keep names for dispatchers)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Annotations retained for Compose stability where needed
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault
