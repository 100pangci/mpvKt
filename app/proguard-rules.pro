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
-keep,allowoptimization class is.xyz.mpv.** { *; }

# SMB client (jcifs-ng) drags in BouncyCastle as its crypto backend; both
# reference optional JDK-only APIs that Android does not ship. R8 can safely
# drop the unused integrations (servlet NTLM filter, Kerberos/JGSS).
-dontwarn java.awt.**
-dontwarn javax.naming.**
-dontwarn javax.security.auth.**
-dontwarn javax.servlet.**
-dontwarn org.ietf.jgss.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
# jcifs-ng builds its crypto through JCE provider lookups; these digest
# mappings are only referenced by name inside BouncyCastleProvider, so R8
# cannot see them. NTLM needs MD4, SMB3 signing would need AESCMAC.
-keep class org.bouncycastle.jcajce.provider.digest.MD4 { *; }
-keep class org.bouncycastle.jcajce.provider.digest.MD4$* { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.AES { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.AES$* { *; }
