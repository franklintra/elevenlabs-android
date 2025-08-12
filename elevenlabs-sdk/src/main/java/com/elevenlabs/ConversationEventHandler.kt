package com.elevenlabs

import android.util.Log
import com.elevenlabs.audio.AudioManager
import com.elevenlabs.models.*
import com.elevenlabs.network.OutgoingEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Event processing pipeline for real-time conversation
 *
 * This class handles type-safe event routing using sealed classes, async event processing
 * with coroutines, state synchronization with UI layer, and error handling with recovery mechanisms.
 */
class ConversationEventHandler(
    private val audioManager: AudioManager,
    private val toolRegistry: ClientToolRegistry,
    private val messageCallback: (OutgoingEvent) -> Unit,
    private val onCanSendFeedbackChange: ((Boolean) -> Unit)? = null,
    private val onUnhandledClientToolCall: ((ConversationEvent.ClientToolCall) -> Unit)? = null
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State management
    private val _conversationMode = MutableStateFlow(ConversationMode.LISTENING)
    val conversationMode: StateFlow<ConversationMode> = _conversationMode

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _lastAgentEventId = MutableStateFlow<String?>(null)

    /**
     * Handle incoming conversation events
     *
     * @param event The conversation event to process
     */
    suspend fun handleIncomingEvent(event: ConversationEvent) {
        try {
            when (event) {
                is ConversationEvent.AgentResponse -> handleAgentResponse(event)
                is ConversationEvent.UserTranscript -> handleUserTranscript(event)
                is ConversationEvent.Interruption -> handleInterruption(event)
                is ConversationEvent.ClientToolCall -> handleClientToolCall(event)
                is ConversationEvent.ModeChange -> handleModeChange(event)
                is ConversationEvent.VadScore -> handleVadScore(event)
                is ConversationEvent.ConnectionStateChange -> handleConnectionStateChange(event)
                is ConversationEvent.Ping -> handlePing(event)
                is ConversationEvent.Error -> handleError(event)
            }
        } catch (e: Exception) {
            Log.d("ConversationEventHandler", "Error handling conversation event: ${e.message}")
            // Continue processing other events even if one fails
        }
    }

    /**
     * Handle agent response events
     */
    private suspend fun handleAgentResponse(event: ConversationEvent.AgentResponse) {
        // Update conversation mode to speaking
        _conversationMode.value = ConversationMode.SPEAKING

        // Add message to conversation history
        val message = Message(
            id = event.eventId,
            content = event.content,
            role = MessageRole.AGENT,
            timestamp = event.timestamp
        )
        addMessageToHistory(message)

        // Store the last agent event ID for feedback
        _lastAgentEventId.value = event.eventId
        onCanSendFeedbackChange?.invoke(true)

        // If this is a voice conversation, ensure audio playback is active
        if (!audioManager.isPlaying()) {
            try {
                audioManager.startPlayback()
            } catch (e: Exception) {
            Log.d("ConversationEventHandler", "Failed to start audio playback: ${e.message}")
            }
        }

        Log.d("ConversationEventHandler", "Agent response: ${event.content}")
    }

    /**
     * Handle user transcript events
     */
    private suspend fun handleUserTranscript(event: ConversationEvent.UserTranscript) {
        // Only process final transcripts to avoid duplicate messages
        if (event.isFinal) {
            val message = Message(
                id = event.eventId,
                content = event.content,
                role = MessageRole.USER,
                timestamp = event.timestamp
            )
            addMessageToHistory(message)

        Log.d("ConversationEventHandler", "User transcript: ${event.content}")
        }
    }

    /**
     * Handle interruption events
     */
    private fun handleInterruption(event: ConversationEvent.Interruption) {
        // User interrupted agent speech - switch to listening mode
        _conversationMode.value = ConversationMode.LISTENING
        onCanSendFeedbackChange?.invoke(false)

        Log.d("ConversationEventHandler", "Conversation interrupted: ${event.eventId}")
    }

    /**
     * Handle client tool call events
     */
    private suspend fun handleClientToolCall(event: ConversationEvent.ClientToolCall) {
        scope.launch {
            val toolExists = toolRegistry.isToolRegistered(event.toolName)
            if (!toolExists) {
                // Notify app layer about unhandled tool call
                try { onUnhandledClientToolCall?.invoke(event) } catch (_: Throwable) {}
            }

            val result = try {
                if (!toolExists) {
                    ClientToolResult.failure("Tool '${event.toolName}' not registered on client")
                } else {
                    toolRegistry.executeTool(event.toolName, event.parameters)
                }
            } catch (e: Exception) {
                ClientToolResult.failure("Tool execution failed: ${e.message}")
            }

            // Send result back to agent if response is expected
            if (event.expectsResponse) {
                val toolResultEvent = OutgoingEvent.ToolResult(
                    toolCallId = event.toolCallId,
                    result = mapOf<String, Any>(
                        "success" to result.success,
                        "result" to result.result,
                        "error" to (result.error ?: "")
                    ),
                    isError = !result.success
                )

                messageCallback(toolResultEvent)
            }
            Log.d("ConversationEventHandler", "Tool executed: ${event.toolName} -> ${if (result.success) "SUCCESS" else "FAILED"}")
        }
    }

    /**
     * Handle mode change events
     */
    private fun handleModeChange(event: ConversationEvent.ModeChange) {
        _conversationMode.value = event.mode
        Log.d("ConversationEventHandler", "Conversation mode changed to: ${event.mode}")
    }

    /**
     * Handle ping events
     */
    private fun handlePing(event: ConversationEvent.Ping) {
        Log.d("ConversationEventHandler", "Ping received: eventId=${event.eventId}, pingMs=${event.pingMs}")
        // Reply with pong using same event id
        scope.launch {
            try {
                val pong = OutgoingEvent.Pong(eventId = event.eventId.toString())
                messageCallback(pong)
            } catch (e: Exception) {
                Log.d("ConversationEventHandler", "Failed to send pong: ${e.message}")
            }
        }
    }

    /**
     * Handle VAD (Voice Activity Detection) score events
     */
    private fun handleVadScore(event: ConversationEvent.VadScore) {
        // VAD scores can be used for UI indicators or audio processing decisions
        // For now, we'll just log them
        if (event.score > 0.7f) {
            Log.d("ConversationEventHandler", "High voice activity detected: ${event.score}")
        }
    }

    /**
     * Handle connection state change events
     */
    private fun handleConnectionStateChange(event: ConversationEvent.ConnectionStateChange) {
        Log.d("ConversationEventHandler", "Connection state changed: ${event.status} ${event.reason?.let { "($it)" } ?: ""}")

        // Handle specific connection states
        when (event.status) {
            ConversationStatus.CONNECTED -> {
                // Connection established - can start audio if needed
            }
            ConversationStatus.DISCONNECTED -> {
                // Connection lost - stop audio and reset state
                scope.launch {
                    try {
                        audioManager.stopRecording()
                        audioManager.stopPlayback()
                    } catch (e: Exception) {
            Log.d("ConversationEventHandler", "Error stopping audio on disconnect: ${e.message}")
                    }
                }
                _conversationMode.value = ConversationMode.LISTENING
                onCanSendFeedbackChange?.invoke(false)
            }
            ConversationStatus.ERROR -> {
                // Connection error - handle gracefully
        Log.d("ConversationEventHandler", "Connection error occurred")
            }
            else -> {
                // Other states (connecting, etc.)
            }
        }
    }

    /**
     * Handle error events
     */
    private fun handleError(event: ConversationEvent.Error) {
        Log.d("ConversationEventHandler", "Conversation error: ${event.error} ${event.code?.let { "(code: $it)" } ?: ""}")

        // Could implement specific error recovery logic here
        // For example, retry on certain error codes, reset state, etc.
    }

    /**
     * Send a user message
     *
     * @param content Message content to send
     */
    fun sendUserMessage(content: String) {
        val event = OutgoingEvent.UserMessage(content)
        messageCallback(event)

        // Add to local message history
        val message = Message(
            id = event.eventId,
            content = content,
            role = MessageRole.USER,
            timestamp = System.currentTimeMillis()
        )
        addMessageToHistory(message)
    }

    /**
     * Send feedback for the last agent response
     *
     * @param isPositive true for positive feedback, false for negative
     */
    fun sendFeedback(isPositive: Boolean) {
        val lastEventId = _lastAgentEventId.value
        if (lastEventId != null) {
            val event = OutgoingEvent.Feedback(
                isPositive = isPositive,
                targetEventId = lastEventId
            )
            messageCallback(event)
        Log.d("ConversationEventHandler", "Sent ${if (isPositive) "positive" else "negative"} feedback for event: $lastEventId")
        } else {
        Log.d("ConversationEventHandler", "No agent response to provide feedback for")
        }
    }

    /**
     * Send contextual update without triggering a response
     *
     * @param content Context information to send
     */
    fun sendContextualUpdate(content: String) {
        val event = OutgoingEvent.ContextualUpdate(content)
        messageCallback(event)
        Log.d("ConversationEventHandler", "Sent contextual update: $content")
    }

    /**
     * Add a message to the conversation history
     */
    private fun addMessageToHistory(message: Message) {
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(message)
        _messages.value = currentMessages
    }

    /**
     * Clear conversation history
     */
    fun clearMessageHistory() {
        _messages.value = emptyList()
        _lastAgentEventId.value = null
        Log.d("ConversationEventHandler", "Conversation history cleared")
    }

    /**
     * Get the current conversation mode
     */
    fun getCurrentMode(): ConversationMode = _conversationMode.value

    /**
     * Get the current message list
     */
    fun getCurrentMessages(): List<Message> = _messages.value

    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
        clearMessageHistory()
    }
}