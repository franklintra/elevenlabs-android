package com.elevenlabs

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.elevenlabs.audio.AudioManager
import com.elevenlabs.audio.LiveKitAudioManager
import com.elevenlabs.models.ConversationMode
import com.elevenlabs.models.ConversationStatus
import com.elevenlabs.models.Message
import com.elevenlabs.network.BaseConnection
import com.elevenlabs.network.ConversationEventParser
import com.elevenlabs.network.WebRTCConnection
import io.livekit.android.room.Room
import kotlinx.coroutines.*

/**
 * Complete implementation of ConversationSession
 *
 * This implementation integrates all components: network layer, audio management,
 * event handling, and client tools to provide a full conversation experience.
 */
internal class ConversationSessionImpl(
    private val context: Context,
    private val config: ConversationConfig,
    private val room: Room,
    private val connection: BaseConnection,
    private val audioManager: AudioManager,
    private val toolRegistry: ClientToolRegistry
) : ConversationSession {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Event handler for processing conversation events
    private val eventHandler = ConversationEventHandler(
        audioManager = audioManager,
        toolRegistry = toolRegistry,
        messageCallback = { event ->
            // Send outgoing events through the connection
            connection.sendMessage(event)
        },
        onCanSendFeedbackChange = { canSend ->
            try { config.onCanSendFeedbackChange?.invoke(canSend) } catch (_: Throwable) {}
        },
        onUnhandledClientToolCall = { call ->
            try { config.onUnhandledClientToolCall?.invoke(call) } catch (_: Throwable) {}
        }
    )

    // LiveData backing fields
    private val _status = MutableLiveData<ConversationStatus>(ConversationStatus.DISCONNECTED)

    // Public observable properties using StateFlow -> LiveData conversion
    override val status: LiveData<ConversationStatus> = _status
    override val mode: LiveData<ConversationMode> = eventHandler.conversationMode.asLiveData()
    override val messages: LiveData<List<Message>> = eventHandler.messages.asLiveData()
    override val isMuted: LiveData<Boolean> =
        if (audioManager is LiveKitAudioManager) {
            audioManager.muteState.asLiveData()
        } else {
            MutableLiveData(false)
        }

        override suspend fun start() {
        try {
            _status.value = ConversationStatus.CONNECTING

            // Set up connection event listener
            connection.setOnMessageListener { messageJson ->
                scope.launch {
                    val event = ConversationEventParser.parseIncomingEvent(messageJson)
                    event?.let { eventHandler.handleIncomingEvent(it) }
                }
            }

            // Set up connection state listener
            connection.setOnConnectionStateListener { connectionState ->
                val conversationStatus = when (connectionState) {
                    com.elevenlabs.network.ConnectionState.CONNECTED -> ConversationStatus.CONNECTED
                    com.elevenlabs.network.ConnectionState.CONNECTING -> ConversationStatus.CONNECTING
                    com.elevenlabs.network.ConnectionState.DISCONNECTED -> ConversationStatus.DISCONNECTED
                    com.elevenlabs.network.ConnectionState.ERROR -> ConversationStatus.ERROR
                    else -> ConversationStatus.DISCONNECTED
                }
                _status.value = conversationStatus
            }

            // Start the connection
            val serverUrl = "wss://livekit.rtc.elevenlabs.io" // Default URL, can be configured
            val token = config.conversationToken ?: ""
            Log.d("ConversationSession", "Starting connection to $serverUrl")
            connection.connect(token, serverUrl, config)

            // Ensure audio starts only after room is connected (addresses LK permission ordering)
            if (!config.textOnly) {
                if (audioManager.hasAudioPermission()) {
                    // small delay to ensure LK internal state is ready
                    // delay(150)
                    audioManager.startRecording()
                    audioManager.startPlayback()
                } else {
                    Log.d("ConversationSession", "Audio permission not granted - text-only mode")
                }
            }
        } catch (e: Exception) {
            _status.value = ConversationStatus.ERROR
            throw RuntimeException("Failed to start conversation session", e)
        }
    }

    override suspend fun end() {
        try {
            _status.value = ConversationStatus.DISCONNECTING

            // Stop audio
            audioManager.stopRecording()
            audioManager.stopPlayback()

            // Disconnect from network
            connection.disconnect()

            // Clean up resources
            eventHandler.cleanup()
            audioManager.cleanup()

            _status.value = ConversationStatus.DISCONNECTED

        } catch (e: Exception) {
            _status.value = ConversationStatus.ERROR
            Log.d("ConversationSession", "Error ending conversation session: ${e.message}")
        } finally {
            scope.cancel()
        }
    }

    override fun sendMessage(message: String) {
        eventHandler.sendUserMessage(message)
    }

    override fun sendFeedback(isPositive: Boolean) {
        eventHandler.sendFeedback(isPositive)
    }

    override fun sendContextualUpdate(update: String) {
        eventHandler.sendContextualUpdate(update)
    }

    override suspend fun toggleMute() {
        val currentMuted = audioManager.isMuted()
        audioManager.setMuted(!currentMuted)
    }

    override suspend fun setMuted(muted: Boolean) {
        audioManager.setMuted(muted)
    }

    override fun registerTool(name: String, tool: ClientTool) {
        toolRegistry.registerTool(name, tool)
    }

    override fun unregisterTool(name: String) {
        toolRegistry.unregisterTool(name)
    }

}