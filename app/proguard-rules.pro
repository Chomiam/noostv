# ============================================================================
# Règles de sécurisation et d'obfuscation R8 / ProGuard pour NoosTV
# ============================================================================

# 1. Protection des modèles Gson et sérialisation JSON
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class io.noostv.data.model.** { <fields>; }
-keep class io.noostv.data.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 2. Protection Media3 / ExoPlayer (Codec, DRM, HLS, DASH, Renderers)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# 3. Protection OkHttp & Okio
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# 4. Protection Jetpack Compose & Compose TV
-keep class androidx.compose.** { *; }
-keep class androidx.tv.** { *; }

# 5. Obfuscation avancée et renommage des métadonnées internes
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# 6. Suppression des logs verbeux de débogage en production
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
