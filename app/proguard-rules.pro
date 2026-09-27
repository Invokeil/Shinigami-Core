# Shinigami Core — R8 / ProGuard rules

# --- Kotlinx Serialization -------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.invokeil.shinigami.**$$serializer { *; }
-keepclassmembers class com.invokeil.shinigami.** { *** Companion; }
-keepclasseswithmembers class com.invokeil.shinigami.** { kotlinx.serialization.KSerializer serializer(...); }

# --- OkHttp ----------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Coroutines ------------------------------------------------------------
-dontwarn kotlinx.coroutines.**

# --- Keep crash-report usability ------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
