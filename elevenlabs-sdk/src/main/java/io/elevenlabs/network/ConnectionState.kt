package io.elevenlabs.network

/**
 * Represents the current state of a network connection
 */
enum class ConnectionState {
    /**
     * No connection established
     */
    IDLE,

    /**
     * In the process of connecting
     */
    CONNECTING,

    /**
     * Successfully connected and active
     */
    CONNECTED,

    /**
     * Attempting to reconnect after connection loss
     */
    RECONNECTING,

    /**
     * Disconnected (either intentionally or due to error)
     */
    DISCONNECTED,

    /**
     * Connection encountered an error
     */
    ERROR;

    /**
     * Returns true if the connection is in an active state
     */
    val isConnected: Boolean
        get() = this == CONNECTED

    /**
     * Returns true if the connection is in a transitional state
     */
    val isTransitioning: Boolean
        get() = this == CONNECTING || this == RECONNECTING

    /**
     * Returns true if the connection has failed
     */
    val isFailed: Boolean
        get() = this == ERROR || this == DISCONNECTED
}