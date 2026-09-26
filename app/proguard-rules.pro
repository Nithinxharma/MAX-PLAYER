# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-dontobfuscate
-keep,allowoptimization class is.xyz.mpv.** { public protected *; }
-keep,allowoptimization class net.mediaarea.mediainfo.lib.** { public protected *; }
-dontwarn org.xmlpull.v1.**
-dontnote org.xmlpull.v1.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# SMBJ ProGuard Rules
# Keep SMBJ classes
-keep class com.hierynomus.smbj.** { *; }
-keep class com.hierynomus.mssmb2.** { *; }
-keep class com.hierynomus.msdtyp.** { *; }
-keep class com.hierynomus.msfscc.** { *; }
-keep class com.hierynomus.protocol.** { *; }
-keep class com.hierynomus.spnego.** { *; }
-keep class com.hierynomus.ntlm.** { *; }
-keep class com.hierynomus.security.** { *; }

# JGSS (Kerberos/SPNEGO) - Optional, not needed for basic NTLM auth
# These are used for domain authentication, which we don't use
-dontwarn org.ietf.jgss.**
-dontnote org.ietf.jgss.**

# MBassador (event bus used by SMBJ) - EL (Expression Language) is optional
-dontwarn net.engio.mbassy.dispatch.el.**
-dontnote net.engio.mbassy.dispatch.el.**
-dontwarn javax.el.**
-dontnote javax.el.**

# Keep MBassador core classes
-keep class net.engio.mbassy.** { *; }

# BouncyCastle (crypto provider used by SMBJ)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ASN.1 classes
-keep class com.hierynomus.asn1.** { *; }

# Keep all classes that use reflection or are loaded dynamically
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions

# Keep CloudStream extension SDK & plugins framework for dynamic DEX loading
-keep class com.lagradost.cloudstream3.** { *; }
-keep interface com.lagradost.cloudstream3.** { *; }
-keepclassmembers class com.lagradost.cloudstream3.** { *; }

-keep class com.lagradost.nicehttp.** { *; }
-keep interface com.lagradost.nicehttp.** { *; }
-keepclassmembers class com.lagradost.nicehttp.** { *; }

# Keep AndroidX Fragment, DialogFragment, Activity, and Lifecycle for dynamic plugin UI and Donation dialogs
-keep class androidx.fragment.app.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keep interface androidx.fragment.app.** { *; }
-keepclassmembers class androidx.fragment.app.** {
    <init>(...);
    <fields>;
    <methods>;
}

-keep class androidx.appcompat.app.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keep interface androidx.appcompat.app.** { *; }
-keepclassmembers class androidx.appcompat.app.** {
    <init>(...);
    <fields>;
    <methods>;
}

-keep class androidx.activity.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keep interface androidx.activity.** { *; }
-keepclassmembers class androidx.activity.** {
    <init>(...);
    <fields>;
    <methods>;
}

-keep class androidx.lifecycle.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keep interface androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.** {
    <init>(...);
    <fields>;
    <methods>;
}

# Keep dynamic third-party plugin packages (such as cncverse donation modules)
-keep class com.cncverse.** { *; }
-keep interface com.cncverse.** { *; }
-keepclassmembers class com.cncverse.** { *; }

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

# Keep Kotlin standard library & reflect
-keep class kotlin.** {
    <fields>;
    <methods>;
}
-keep interface kotlin.** { *; }
-keepclassmembers class kotlin.** {
    <fields>;
    <methods>;
}

# Keep OkHttp & Okio networking libraries
-keep class okhttp3.** {
    <fields>;
    <methods>;
}
-keep interface okhttp3.** { *; }
-keepclassmembers class okhttp3.** {
    <fields>;
    <methods>;
}

-keep class okio.** {
    <fields>;
    <methods>;
}
-keep interface okio.** { *; }
-keepclassmembers class okio.** {
    <fields>;
    <methods>;
}

# Keep Gson & Jackson JSON parsers
-keep class com.google.gson.** {
    <fields>;
    <methods>;
}
-keep interface com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** {
    <fields>;
    <methods>;
}

-keep class com.fasterxml.jackson.** { *; }
-keep interface com.fasterxml.jackson.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**

-keep class org.jsoup.** { *; }
-keep interface org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**

# Keep CineHub extension bridge models & interfaces
-keep class xyz.mpv.rex.cinehub.extension.** { *; }
-keep interface xyz.mpv.rex.cinehub.extension.** { *; }
-keepclassmembers class xyz.mpv.rex.cinehub.extension.** { *; }

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}