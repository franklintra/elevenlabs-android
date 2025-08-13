# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
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

# Keep LiveKit classes
-keep class io.livekit.** { *; }
-keep class org.webrtc.** { *; }

# Keep ElevenLabs SDK public API
-keep public class io.elevenlabs.** { *; }

# Keep Gson TypeAdapters and TypeTokens
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep model classes for JSON serialization
-keep class io.elevenlabs.models.** { *; }

# Keep WebRTC audio classes
-keep class org.webrtc.audio.** { *; }