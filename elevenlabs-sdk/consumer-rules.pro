# Consumer ProGuard rules for ElevenLabs SDK
# These rules will be automatically applied to consumers of this library

# Keep public API classes and methods
-keep public class com.elevenlabs.** { public *; }

# Keep LiveKit dependencies
-keep class io.livekit.** { *; }
-keep class org.webrtc.** { *; }

# Keep model classes for JSON serialization
-keep class com.elevenlabs.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*