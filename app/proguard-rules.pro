# ── MediaPipe Tasks ───────────────────────────────────────────────────────────
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-keep class com.google.mediapipe.framework.** { *; }

# ── TensorFlow Lite (bundled inside MediaPipe) ────────────────────────────────
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.task.** { *; }
-keepclassmembers class * {
    @org.tensorflow.lite.annotations.UsedByReflection *;
}

# ── CameraX ───────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }

# ── Keep native method bindings ───────────────────────────────────────────────
-keepclasseswithmembernames class * {
    native <methods>;
}
