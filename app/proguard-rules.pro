# Enigma2 FireTV – ProGuard rules

# Keep Retrofit service interfaces
-keep interface com.enigma2.firetv.data.api.** { *; }

# Keep data model classes (used by Gson)
-keep class com.enigma2.firetv.data.model.** { *; }
-keepclassmembers class com.enigma2.firetv.data.model.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
