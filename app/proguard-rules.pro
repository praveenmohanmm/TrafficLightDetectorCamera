# ── App classes ──────────────────────────────────────────────────────────────
# OverlayView is referenced by name in activity_main.xml — R8 must not rename
# or remove any class in this package or layout inflation crashes on launch.
-keep class com.trafficlightdetector.** { *; }

# ── MediaPipe Tasks ───────────────────────────────────────────────────────────
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-dontwarn com.google.mediapipe.**

# ── TensorFlow Lite (bundled inside MediaPipe) ────────────────────────────────
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.task.** { *; }
-keepclassmembers class * {
    @org.tensorflow.lite.annotations.UsedByReflection *;
}
-dontwarn org.tensorflow.**

# ── CameraX ───────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }

# ── Keep JNI / native method bindings ────────────────────────────────────────
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── Strip all android.util.Log calls from release builds ─────────────────────
# Prevents debug output leaking in production and reduces binary size slightly.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# ── Suppress warnings from optional transitive dependencies ──────────────────
-dontwarn com.google.protobuf.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.**
-dontwarn javax.annotation.**
