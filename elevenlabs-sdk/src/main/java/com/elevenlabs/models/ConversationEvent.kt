package com.elevenlabs.models

/**
 * Sealed class representing all possible conversation events
 *
 * This provides type-safe event handling for real-time conversation communication
 * between the client and ElevenLabs agents.
 */
sealed class ConversationEvent {

    /**
     * Event representing a response from the agent
     *
     * @param content The agent's response text
     * @param eventId Unique identifier for this event
     * @param timestamp When the event occurred
     */
    data class AgentResponse(
        val content: String,
        val eventId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : ConversationEvent()

    /**
     * Event representing user speech transcription
     *
     * @param content The transcribed user speech
     * @param eventId Unique identifier for this event
     * @param timestamp When the event occurred
     * @param isFinal Whether this is the final transcription or partial
     */
    data class UserTranscript(
        val content: String,
        val eventId: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isFinal: Boolean = true
    ) : ConversationEvent()

    /**
     * Event representing an interruption of agent speech
     *
     * @param eventId The event ID that was interrupted
     * @param timestamp When the interruption occurred
     */
    data class Interruption(
        val eventId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : ConversationEvent()

    /**
     * Event representing a tool call from the agent
     *
     * @param toolName Name of the tool to execute
     * @param parameters Parameters to pass to the tool
     * @param toolCallId Unique identifier for this tool call
     * @param expectsResponse Whether the agent expects a response
     * @param timestamp When the tool call was made
     */
    data class ClientToolCall(
        val toolName: String,
        val parameters: Map<String, Any>,
        val toolCallId: String,
        val expectsResponse: Boolean = true,
        val timestamp: Long = System.currentTimeMillis()
    ) : ConversationEvent()

    /**
     * Event representing a change in conversation mode
     *
     * @param mode The new conversation mode
     * @param timestamp When the mode changed
     */
    data class ModeChange(
        val mode: ConversationMode,
        val timestamp: Long = System.currentTimeMillis()
    ) : ConversationEvent()

    /**
     * Event representing voice activity detection score
     *
     * @param score VAD score (0.0 to 1.0, higher means more likely speech)
     * @param timestamp When the score was calculated
     */
    data class VadScore(
        val score: Float,
        val timestamp: Long = System.currentTimeMillis()
    ) : ConversationEvent()

    /**
     * Event representing connection state changes
     *
     * @param status The new connection status
     * @param reason Optional reason for the status change
     * @param timestamp When the status changed
     */
    data class ConnectionStateChange(
        val status: ConversationStatus,
        val reason: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : ConversationEvent()

    /**
     * Event representing an error that occurred during the conversation
     *
     * @param error The error message or description
     * @param code Optional error code
     * @param timestamp When the error occurred
     */
    data class Error(
        val error: String,
        val code: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : ConversationEvent()
}