# ==============================================================================
# MAX STREAM - ProGuard & R8 Compatibility Rules
# Preserves MPV Engine, CloudStream Plugin Runtime, Extractors, Jackson, NiceHttp,
# OkHttp, Reflection, and Metadata for 100% Release Build Parity.
# ==============================================================================

-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# MPV Player Engine & Native Libs
-keep,allowoptimization class is.xyz.mpv.** { public protected *; }
-keep,allowoptimization class net.mediaarea.mediainfo.lib.** { public protected *; }
-dontwarn org.xmlpull.v1.**
-dontnote org.xmlpull.v1.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# SMBJ Network Protocol Rules
-keep class com.hierynomus.smbj.** { *; }
-keep class com.hierynomus.mssmb2.** { *; }
-keep class com.hierynomus.msdtyp.** { *; }
-keep class com.hierynomus.msfscc.** { *; }
-keep class com.hierynomus.protocol.** { *; }
-keep class com.hierynomus.spnego.** { *; }
-keep class com.hierynomus.ntlm.** { *; }
-keep class com.hierynomus.security.** { *; }
-dontwarn org.ietf.jgss.**
-dontnote org.ietf.jgss.**
-dontwarn net.engio.mbassy.dispatch.el.**
-dontnote net.engio.mbassy.dispatch.el.**
-dontwarn javax.el.**
-dontnote javax.el.**
-keep class net.engio.mbassy.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class com.hierynomus.asn1.** { *; }

# ==============================================================================
# CloudStream 3 Full Engine & Plugin Runtime Preservation (R8 Compatibility)
# ==============================================================================
-keep class com.lagradost.cloudstream3.** { *; }
-keep interface com.lagradost.cloudstream3.** { *; }
-keepclassmembers class com.lagradost.cloudstream3.** { *; }
-keepclassmembernames class com.lagradost.cloudstream3.** { *; }

# CloudStream MainAPI, APIHolder, Plugin, BasePlugin & Extractors
-keep class com.lagradost.cloudstream3.MainAPI { *; }
-keep class com.lagradost.cloudstream3.APIHolder { *; }
-keep class com.lagradost.cloudstream3.plugins.** { *; }
-keep class com.lagradost.cloudstream3.utils.** { *; }
-keep class com.lagradost.cloudstream3.extractors.** { *; }
-keep class com.lagradost.cloudstream3.network.** { *; }
-keep class com.lagradost.cloudstream3.subtitles.** { *; }

# NiceHttp Preservation
-keep class com.lagradost.nicehttp.** { *; }
-keep interface com.lagradost.nicehttp.** { *; }
-keepclassmembers class com.lagradost.nicehttp.** { *; }

# Jackson JSON Parsing & Serialization (Used by CloudStream extensions & models)
-keep class com.fasterxml.jackson.** { *; }
-keep interface com.fasterxml.jackson.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { *; }
-keep @com.fasterxml.jackson.annotation.** class * { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.** *;
}
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn javax.xml.stream.**

# Jsoup HTML parsing
-keep class org.jsoup.** { *; }
-keep interface org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**

# Gson & KotlinX Serialization
-keep class com.google.gson.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep interface kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep Kotlin Reflection, Metadata & Coroutines for Dynamic DEX plugin execution
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.Dispatchers { *; }

# Keep OkHttp & Okio (Headers, Cookie Jar, Interceptors, User-Agent, DoH)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }
-keepclassmembers class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep AndroidX Fragment, Activity, Lifecycle & UI for Dynamic Plugins
-keep class androidx.fragment.app.** { *; }
-keep class androidx.appcompat.app.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class com.cncverse.** { *; }

# Keep CineHub Extensions, Provider Models & Registry
-keep class xyz.mpv.rex.cinehub.** { *; }
-keep interface xyz.mpv.rex.cinehub.** { *; }
-keepclassmembers class xyz.mpv.rex.cinehub.** { *; }

# Keep Serializable & Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Explicit Extractor & Crypto Helper Preservation
-keep class com.lagradost.cloudstream3.utils.CryptoJSHelper { *; }
-keep class com.lagradost.cloudstream3.extractors.** { *; }
-keep interface com.lagradost.cloudstream3.extractors.** { *; }
-keepclassmembers class com.lagradost.cloudstream3.extractors.** { *; }
-keep class com.lagradost.cloudstream3.extractors.helper.** { *; }
-keep class com.lagradost.cloudstream3.utils.M3u8Helper { *; }
-keep class com.lagradost.cloudstream3.utils.ExtractorLink { *; }
-keep class com.lagradost.cloudstream3.utils.Qualities { *; }
-keep class com.lagradost.cloudstream3.utils.SubtitleUtils { *; }
-keep class com.lagradost.cloudstream3.utils.AppUtilsKt { *; }
-keep class com.lagradost.cloudstream3.utils.AppUtils { *; }
-keep class com.lagradost.cloudstream3.utils.JsoupHelperKt { *; }
-keep class com.lagradost.cloudstream3.utils.Coroutines { *; }
-keep class com.lagradost.cloudstream3.utils.ExtractorApi { *; }
-keep class com.lagradost.cloudstream3.models.** { *; }
-keep class com.lagradost.cloudstream3.LoadResponse { *; }
-keep class com.lagradost.cloudstream3.MovieLoadResponse { *; }
-keep class com.lagradost.cloudstream3.TvSeriesLoadResponse { *; }
-keep class com.lagradost.cloudstream3.AnimeLoadResponse { *; }
-keep class com.lagradost.cloudstream3.Episode { *; }
-keep class com.lagradost.cloudstream3.SubtitleFile { *; }

# Dynamic Plugin & ClassLoader Reflection Support
-keep class dalvik.system.PathClassLoader { *; }
-keep class dalvik.system.DexClassLoader { *; }
-keep class dalvik.system.BaseDexClassLoader { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ==============================================================================
# Preserved Library & Reflection Rules Exactly As Requested
# ==============================================================================

# Suppress warnings
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**

# Keep kotlinx.coroutines for dynamic DEX plugin execution
-keep class kotlinx.coroutines.** {
    <fields>;
    <methods>;
}
-keep interface kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    <fields>;
    <methods>;
}

-keep class kotlinx.coroutines.Dispatchers {
    public static *** *;
    public static *** *(...);
}
-keepclassmembers class kotlinx.coroutines.Dispatchers {
    public static *** *;
    public static *** *(...);
}

# Keep Kotlin standard library
-keep class kotlin.** {
    <fields>;
    <methods>;
}
-keep interface kotlin.** { *; }
-keepclassmembers class kotlin.** {
    <fields>;
    <methods>;
}

# Keep OkHttp networking library
-keep class okhttp3.** {
    <fields>;
    <methods>;
}
-keep interface okhttp3.** { *; }
-keepclassmembers class okhttp3.** {
    <fields>;
    <methods>;
}

# Keep Okio
-keep class okio.** {
    <fields>;
    <methods>;
}
-keep interface okio.** { *; }
-keepclassmembers class okio.** {
    <fields>;
    <methods>;
}

# Keep Gson
-keep class com.google.gson.** {
    <fields>;
    <methods>;
}
-keep interface com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** {
    <fields>;
    <methods>;
}

# Keep Jackson
-keep class com.fasterxml.jackson.** { *; }
-keep interface com.fasterxml.jackson.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { *; }

# Keep Jsoup
-keep class org.jsoup.** { *; }
-keep interface org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** { *; }

# Keep NiceHttp
-keep class com.lagradost.nicehttp.** { *; }
-keep interface com.lagradost.nicehttp.** { *; }
-keepclassmembers class com.lagradost.nicehttp.** { *; }

