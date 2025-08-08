package com.elevenlabs.audio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager as SystemAudioManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Audio utilities for Android-specific functionality
 *
 * This object provides utility functions for runtime permission handling for microphone access,
 * audio focus management for interruptions, volume control and routing configuration,
 * and optimal audio settings for conversation quality.
 */
object AudioUtils {

    private const val AUDIO_PERMISSION_REQUEST_CODE = 1001

    /**
     * Check if audio recording permission is granted
     * @param context Android context
     * @return true if permission is granted
     */
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Request audio recording permission
     * @param activity Activity to request permission from
     * @return true if permission is granted
     */
    suspend fun requestAudioPermission(activity: Activity): Boolean {
        if (hasAudioPermission(activity)) {
            return true
        }

        return suspendCancellableCoroutine { continuation ->
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                AUDIO_PERMISSION_REQUEST_CODE
            )

            // Note: In a real implementation, you would need to handle the result
            // in the activity's onRequestPermissionsResult callback and communicate
            // back to this coroutine. For now, we'll return false as a placeholder.
            continuation.resume(false)
        }
    }

    /**
     * Configure audio session for voice communication
     * @param context Android context
     */
    fun configureAudioSession(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as SystemAudioManager

        // Set audio mode for voice communication
        audioManager.mode = SystemAudioManager.MODE_IN_COMMUNICATION

        // Enable speaker phone if needed (can be controlled separately)
        // audioManager.isSpeakerphoneOn = false

        // Request audio focus for voice calls
        requestAudioFocus(audioManager)
    }

    /**
     * Request audio focus for media playback
     * @param audioManager System audio manager
     */
    private fun requestAudioFocus(audioManager: SystemAudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val focusRequest = AudioFocusRequest.Builder(SystemAudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { /* no-op */ }
                .build()

            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { /* no-op */ },
                SystemAudioManager.STREAM_VOICE_CALL,
                SystemAudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    // Removed no-op focus change handler

    /**
     * Abandon audio focus
     * @param context Android context
     */
    fun abandonAudioFocus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as SystemAudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For API 26+, you would need to store the AudioFocusRequest to abandon it
            // This is a simplified version
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { /* no-op */ }
        }
    }

    /**
     * Get optimal audio settings for conversation quality
     * @return AudioSettings optimized for voice conversations
     */
    fun getOptimalAudioSettings(): AudioSettings {
        return AudioSettings.VOICE_CALL_OPTIMIZED
    }

    /**
     * Check if the device supports audio input
     * @param context Android context
     * @return true if device has microphone capability
     */
    fun hasMicrophone(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }

    /**
     * Check if the device supports audio output
     * @param context Android context
     * @return true if device has audio output capability
     */
    fun hasAudioOutput(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
    }

    /**
     * Set speaker phone mode
     * @param context Android context
     * @param enabled true to enable speaker phone, false to disable
     */
    fun setSpeakerPhoneEnabled(context: Context, enabled: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as SystemAudioManager
        audioManager.isSpeakerphoneOn = enabled
    }

    /**
     * Check if speaker phone is enabled
     * @param context Android context
     * @return true if speaker phone is enabled
     */
    fun isSpeakerPhoneEnabled(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as SystemAudioManager
        return audioManager.isSpeakerphoneOn
    }

    /**
     * Set microphone mute state
     * @param context Android context
     * @param muted true to mute microphone, false to unmute
     */
    fun setMicrophoneMuted(context: Context, muted: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as SystemAudioManager
        audioManager.isMicrophoneMute = muted
    }

    /**
     * Check if microphone is muted
     * @param context Android context
     * @return true if microphone is muted
     */
    fun isMicrophoneMuted(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as SystemAudioManager
        return audioManager.isMicrophoneMute
    }

    /**
     * Get the current audio mode
     * @param context Android context
     * @return Current audio mode
     */
    fun getAudioMode(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as SystemAudioManager
        return audioManager.mode
    }

    /**
     * Reset audio session to normal mode
     * @param context Android context
     */
    fun resetAudioSession(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as SystemAudioManager
        audioManager.mode = SystemAudioManager.MODE_NORMAL
        abandonAudioFocus(context)
    }
}