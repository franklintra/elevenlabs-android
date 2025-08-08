package com.elevenlabs.example.models

/**
 * Represents the current state of the UI
 */
sealed class UiState {
    object Idle : UiState()
    object Connecting : UiState()
    object Connected : UiState()
    object Disconnecting : UiState()
    object Error : UiState()
}