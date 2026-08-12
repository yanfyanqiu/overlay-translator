# OfflineInterpreter App ProGuard Rules

# Keep sherpa-onnx JNI
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Keep llama.cpp native methods
-keep class com.offlineinterpreter.app.** { *; }

# Keep lib native methods
-keep class com.offlineinterpreter.lib.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
