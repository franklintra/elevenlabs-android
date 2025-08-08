package com.elevenlabs.audio

/**
 * Interface for managing audio capture, playback, and processing for voice conversations
 *
 * This interface provides comprehensive audio management including microphone input
 * handling with permission management, speaker output control and volume management,
 * mute/unmute functionality, and audio session configuration for optimal quality.
 */
interface AudioManager {

    /**
     * Start recording from the microphone
     * @throws SecurityException if audio permission is not granted
     * @throws IllegalStateException if already recording
     */
    suspend fun startRecording()

    /**
     * Stop recording from the microphone
     */
    suspend fun stopRecording()

    /**
     * Start audio playback for incoming audio
     * @throws IllegalStateException if already playing
     */
    suspend fun startPlayback()

    /**
     * Stop audio playback
     */
    suspend fun stopPlayback()

    /**
     * Set the microphone mute state
     * @param muted true to mute microphone, false to unmute
     */
    suspend fun setMuted(muted: Boolean)

    /**
     * Check if the microphone is currently muted
     * @return true if muted, false if unmuted
     */
    fun isMuted(): Boolean

    /**
     * Set the playback volume
     * @param volume Volume level from 0.0 (silent) to 1.0 (maximum)
     */
    fun setVolume(volume: Float)

    /**
     * Get the current playback volume
     * @return Volume level from 0.0 to 1.0
     */
    fun getVolume(): Float

    /**
     * Check if audio recording permission is granted
     * @return true if permission is granted, false otherwise
     */
    fun hasAudioPermission(): Boolean

    /**
     * Request audio recording permission
     * This should trigger the system permission dialog
     * @return true if permission was granted, false if denied
     */
    suspend fun requestAudioPermission(): Boolean

    /**
     * Check if audio recording is currently active
     * @return true if recording, false otherwise
     */
    fun isRecording(): Boolean

    /**
     * Check if audio playback is currently active
     * @return true if playing, false otherwise
     */
    fun isPlaying(): Boolean

    /**
     * Set listener for audio state changes
     * @param listener Callback for audio state events
     */
    fun setAudioStateListener(listener: AudioStateListener?)

    /**
     * Clean up audio resources
     * Should be called when audio management is no longer needed
     */
    fun cleanup()
}

/**
 * Listener interface for audio state changes
 */
interface AudioStateListener {
    /**
     * Called when recording state changes
     * @param isRecording true if recording started, false if stopped
     */
    fun onRecordingStateChanged(isRecording: Boolean) {}

    /**
     * Called when playback state changes
     * @param isPlaying true if playback started, false if stopped
     */
    fun onPlaybackStateChanged(isPlaying: Boolean) {}

    /**
     * Called when mute state changes
     * @param isMuted true if muted, false if unmuted
     */
    fun onMuteStateChanged(isMuted: Boolean) {}

    /**
     * Called when volume changes
     * @param volume New volume level (0.0 to 1.0)
     */
    fun onVolumeChanged(volume: Float) {}

    /**
     * Called when an audio error occurs
     * @param error Description of the error
     * @param exception Optional exception that caused the error
     */
    fun onAudioError(error: String, exception: Throwable? = null) {}
}

/**
 * Audio settings configuration
 */
data class AudioSettings(
    /**
     * Sample rate for audio processing (e.g., 16000, 44100)
     */
    val sampleRate: Int = 16000,

    /**
     * Number of audio channels (1 for mono, 2 for stereo)
     */
    val channels: Int = 1,

    /**
     * Bit rate for audio encoding
     */
    val bitRate: Int = 64000,

    /**
     * Enable echo cancellation
     */
    val echoCancellation: Boolean = true,

    /**
     * Enable noise suppression
     */
    val noiseSuppression: Boolean = true,

    /**
     * Enable automatic gain control
     */
    val automaticGainControl: Boolean = true
) {
    companion object {
        /**
         * Default settings optimized for voice conversations
         */
        val VOICE_CALL_OPTIMIZED = AudioSettings(
            sampleRate = 16000,
            channels = 1,
            bitRate = 32000,
            echoCancellation = true,
            noiseSuppression = true,
            automaticGainControl = true
        )

        /**
         * High quality settings for music or detailed audio
         */
        val HIGH_QUALITY = AudioSettings(
            sampleRate = 44100,
            channels = 2,
            bitRate = 128000,
            echoCancellation = false,
            noiseSuppression = false,
            automaticGainControl = false
        )
    }
}