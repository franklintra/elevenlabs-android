package com.elevenlabs.example.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.elevenlabs.ConversationClient
import com.elevenlabs.ConversationSession
import com.elevenlabs.example.models.AppConfiguration
import com.elevenlabs.example.models.UiState
import com.elevenlabs.models.ConversationStatus
import kotlinx.coroutines.launch

/**
 * Simplified ViewModel for basic connection management
 *
 * Only handles connect/disconnect functionality with status updates
 */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    private var currentSession: ConversationSession? = null

    // UI State
    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    // Session Status
    private val _sessionStatus = MutableLiveData<ConversationStatus>(ConversationStatus.DISCONNECTED)
    val sessionStatus: LiveData<ConversationStatus> = _sessionStatus

    // Error handling
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Audio permissions
    private val _audioPermissionRequired = MutableLiveData<Boolean>(false)
    val audioPermissionRequired: LiveData<Boolean> = _audioPermissionRequired

    private var hasAudioPermission: Boolean = false

    fun startConversation(appConfig: AppConfiguration, activityContext: Context) {
        if (currentSession != null && _uiState.value != UiState.Idle && _uiState.value !is UiState.Error) {
            Log.d("ConversationViewModel", "Session already active or connecting.")
            return
        }

        _uiState.value = UiState.Connecting
        _sessionStatus.value = ConversationStatus.CONNECTING

        viewModelScope.launch {
            try {
                val session = ConversationClient.startSession(
                    appConfig.toConversationConfig(),
                    activityContext
                )

                currentSession = session

                // Observe session status
                session.status.observeForever { status ->
                    _sessionStatus.value = status
                    _uiState.value = when (status) {
                        ConversationStatus.CONNECTED -> UiState.Connected
                        ConversationStatus.CONNECTING -> UiState.Connecting
                        ConversationStatus.DISCONNECTED -> UiState.Idle
                        ConversationStatus.DISCONNECTING -> UiState.Disconnecting
                        ConversationStatus.ERROR -> UiState.Error
                    }

                    if (status == ConversationStatus.ERROR) {
                        _errorMessage.postValue("Connection failed. Please try again.")
                    }
                }

                // Start the session
                session.start()
                Log.d("ConversationViewModel", "Session started successfully")

            } catch (e: Exception) {
                Log.e("ConversationViewModel", "Error starting conversation: ${e.message}", e)
                _errorMessage.postValue("Failed to start conversation: ${e.localizedMessage ?: e.message}")
                _uiState.postValue(UiState.Error)
                _sessionStatus.postValue(ConversationStatus.ERROR)
            }
        }
    }

    fun endConversation() {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Disconnecting
                _sessionStatus.value = ConversationStatus.DISCONNECTING

                currentSession?.end()
                currentSession = null

                _uiState.value = UiState.Idle
                _sessionStatus.value = ConversationStatus.DISCONNECTED

                Log.d("ConversationViewModel", "Session ended successfully")
            } catch (e: Exception) {
                Log.e("ConversationViewModel", "Error ending conversation: ${e.message}", e)
                _errorMessage.postValue("Failed to end conversation: ${e.localizedMessage ?: e.message}")
                _uiState.postValue(UiState.Error)
            }
        }
    }

    fun onAudioPermissionResult(isGranted: Boolean) {
        hasAudioPermission = isGranted
        _audioPermissionRequired.value = !isGranted

        if (!isGranted) {
            Log.d("ConversationViewModel", "Audio permission not granted - will use text-only mode")
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        endConversation()
    }
}