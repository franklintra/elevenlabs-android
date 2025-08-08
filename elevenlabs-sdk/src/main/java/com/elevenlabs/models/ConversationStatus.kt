package com.elevenlabs.models

/**
 * Enum representing the current connection status of a conversation session
 */
enum class ConversationStatus {
    /**
     * The session is not connected
     */
    DISCONNECTED,

    /**
     * The session is in the process of connecting
     */
    CONNECTING,

    /**
     * The session is connected and active
     */
    CONNECTED,

    /**
     * The session is in the process of disconnecting
     */
    DISCONNECTING,

    /**
     * The session encountered an error
     */
    ERROR;

    /**
     * Returns true if the session is in an active state (connected)
     */
    val isActive: Boolean
        get() = this == CONNECTED

    /**
     * Returns true if the session is in a transitional state
     */
    val isTransitioning: Boolean
        get() = this == CONNECTING || this == DISCONNECTING
}