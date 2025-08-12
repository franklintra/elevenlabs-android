package com.elevenlabs.models

/**
 * Represents a message in the conversation
 *
 * @param id Unique integer identifier for the message
 * @param content The text content of the message
 * @param role Who sent the message (user or agent)
 * @param timestamp When the message was sent
 * @param metadata Additional metadata about the message
 */
data class Message(
    val id: Int,
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Enum representing who sent a message
 */
enum class MessageRole {
    /**
     * Message sent by the user
     */
    USER,

    /**
     * Message sent by the agent
     */
    AGENT;

    /**
     * Returns true if this message is from the user
     */
    val isUser: Boolean
        get() = this == USER

    /**
     * Returns true if this message is from the agent
     */
    val isAgent: Boolean
        get() = this == AGENT
}