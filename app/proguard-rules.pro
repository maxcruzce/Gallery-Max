# Add project specific ProGuard rules here.

# Keep data classes
-keep class com.hypergallery.data.** { *; }

# Coil
-dontwarn coil.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}