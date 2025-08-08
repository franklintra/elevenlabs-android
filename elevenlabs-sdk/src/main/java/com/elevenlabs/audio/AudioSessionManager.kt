package com.elevenlabs.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager as SystemAudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Log

/**
 * Audio session management for optimal conversation quality
 *
 * This class handles audio session configuration, focus management,
 * and quality optimization for voice conversations.
 */
class AudioSessionManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as SystemAudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val audioFocusChangeListener = SystemAudioManager.OnAudioFocusChangeListener { focusChange ->
        handleAudioFocusChange(focusChange)
    }

    /**
     * Configure audio session for voice communication
     * @param settings Audio settings to apply
     */
    fun configureForVoiceCall(settings: AudioSettings = AudioSettings.VOICE_CALL_OPTIMIZED) {
        try {
            // Set audio mode for voice communication
            audioManager.mode = SystemAudioManager.MODE_IN_COMMUNICATION

            // Request audio focus
            requestAudioFocus()

            // Configure additional settings based on the provided AudioSettings
            applyAudioSettings(settings)

            // Ensure audio routes to speaker (important on emulator/voice calls)
            audioManager.isSpeakerphoneOn = true
            val vol = audioManager.getStreamVolume(SystemAudioManager.STREAM_VOICE_CALL)
            val max = audioManager.getStreamMaxVolume(SystemAudioManager.STREAM_VOICE_CALL)
            Log.d("AudioSessionManager", "MODE_IN_COMMUNICATION, speakerphoneOn=${audioManager.isSpeakerphoneOn}, voiceVol=$vol/$max")

        } catch (e: Exception) {
            Log.d("AudioSessionManager", "Failed to configure audio session: ${e.message}")
        }
    }

    /**
     * Configure audio session for media playback
     * @param settings Audio settings to apply
     */
    fun configureForMedia(settings: AudioSettings = AudioSettings.HIGH_QUALITY) {
        try {
            // Set audio mode for media playback
            audioManager.mode = SystemAudioManager.MODE_NORMAL

            // Request audio focus
            requestAudioFocus()

            // Configure additional settings
            applyAudioSettings(settings)

        } catch (e: Exception) {
            Log.d("AudioSessionManager", "Failed to configure audio session for media: ${e.message}")
        }
    }

    /**
     * Request audio focus for the current session
     */
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestAudioFocusV26()
        } else {
            requestAudioFocusLegacy()
        }
    }

    /**
     * Request audio focus for Android O and above
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocusV26() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(SystemAudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        hasAudioFocus = result == SystemAudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Request audio focus for devices below Android O
     */
    @Suppress("DEPRECATION")
    private fun requestAudioFocusLegacy() {
        val result = audioManager.requestAudioFocus(
            audioFocusChangeListener,
            SystemAudioManager.STREAM_VOICE_CALL,
            SystemAudioManager.AUDIOFOCUS_GAIN
        )
        hasAudioFocus = result == SystemAudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Abandon audio focus
     */
    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    /**
     * Handle audio focus changes
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            SystemAudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                onAudioFocusGained()
            }
            SystemAudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                onAudioFocusLost()
            }
            SystemAudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                onAudioFocusLostTransient()
            }
            SystemAudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                onAudioFocusLostCanDuck()
            }
        }
    }

    /**
     * Called when audio focus is gained
     */
    private fun onAudioFocusGained() {
        // Resume normal audio operations
        Log.d("AudioSessionManager", "Audio focus gained - resuming audio")
    }

    /**
     * Called when audio focus is lost permanently
     */
    private fun onAudioFocusLost() {
        // Stop all audio operations
        Log.d("AudioSessionManager", "Audio focus lost - stopping audio")
    }

    /**
     * Called when audio focus is lost temporarily
     */
    private fun onAudioFocusLostTransient() {
        // Pause audio operations
        Log.d("AudioSessionManager", "Audio focus lost temporarily - pausing audio")
    }

    /**
     * Called when audio focus is lost but can duck (lower volume)
     */
    private fun onAudioFocusLostCanDuck() {
        // Lower volume but continue playing
        Log.d("AudioSessionManager", "Audio focus lost - ducking volume")
    }

    /**
     * Apply audio settings to the session
     */
    private fun applyAudioSettings(settings: AudioSettings) {
        // Note: Some of these settings might need to be applied at the track level
        // This is a placeholder for where system-level audio settings would be configured

        // Set speaker phone if needed
        // audioManager.isSpeakerphoneOn = false // or true based on user preference

        // Set microphone mute state
        // audioManager.isMicrophoneMute = false

        Log.d("AudioSessionManager", "Applied audio settings: sampleRate=${settings.sampleRate}, channels=${settings.channels}")
    }

    /**
     * Reset audio session to normal state
     */
    fun resetAudioSession() {
        try {
            abandonAudioFocus()
            audioManager.mode = SystemAudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false

        } catch (e: Exception) {
            Log.d("AudioSessionManager", "Failed to reset audio session: ${e.message}")
        }
    }

    /**
     * Check if we currently have audio focus
     */
    fun hasAudioFocus(): Boolean = hasAudioFocus

    /**
     * Get current audio mode
     */
    fun getCurrentAudioMode(): Int = audioManager.mode

    /**
     * Set speaker phone enabled/disabled
     */
    fun setSpeakerPhoneEnabled(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
    }

    /**
     * Check if speaker phone is enabled
     */
    fun isSpeakerPhoneEnabled(): Boolean = audioManager.isSpeakerphoneOn

    /**
     * Set microphone mute state
     */
    fun setMicrophoneMuted(muted: Boolean) {
        audioManager.isMicrophoneMute = muted
    }

    /**
     * Check if microphone is muted
     */
    fun isMicrophoneMuted(): Boolean = audioManager.isMicrophoneMute

    /**
     * Get optimal audio settings for the current device
     */
    fun getOptimalSettings(): AudioSettings {
        return when {
            isLowEndDevice() -> AudioSettings(
                sampleRate = 16000,
                channels = 1,
                bitRate = 32000,
                echoCancellation = true,
                noiseSuppression = true,
                automaticGainControl = true
            )
            isHighEndDevice() -> AudioSettings(
                sampleRate = 44100,
                channels = 1,
                bitRate = 64000,
                echoCancellation = true,
                noiseSuppression = true,
                automaticGainControl = false
            )
            else -> AudioSettings.VOICE_CALL_OPTIMIZED
        }
    }

    /**
     * Check if this is a low-end device
     */
    private fun isLowEndDevice(): Boolean {
        // Simple heuristic - can be improved with more sophisticated detection
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
    }

    /**
     * Check if this is a high-end device
     */
    private fun isHighEndDevice(): Boolean {
        // Simple heuristic - can be improved with more sophisticated detection
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }
}