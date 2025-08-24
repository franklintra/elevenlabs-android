package io.elevenlabs.example.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationSession
import io.elevenlabs.example.models.UiState
import io.elevenlabs.models.ConversationStatus
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

    // Mode ("speaking" | "listening") propagated from SDK callback
    private val _mode = MutableLiveData<String>()
    val mode: LiveData<String> = _mode

    // Can send feedback state
    private val _canSendFeedback = MutableLiveData<Boolean>(false)
    val canSendFeedback: LiveData<Boolean> = _canSendFeedback

    // Mute state exposed to UI
    private val _isMuted = MutableLiveData<Boolean>(false)
    val isMuted: LiveData<Boolean> = _isMuted

    fun startConversation(activityContext: Context) {
        if (currentSession != null && _uiState.value != UiState.Idle && _uiState.value !is UiState.Error) {
            Log.d("ConversationViewModel", "Session already active or connecting.")
            return
        }

        _uiState.value = UiState.Connecting
        _sessionStatus.value = ConversationStatus.CONNECTING

        viewModelScope.launch {
            try {
                val config = io.elevenlabs.ConversationConfig(
                    agentId = "J3Pbu5gP6NNKBscdCdwA", // Replace with your agent ID
                    conversationToken = null,
                    userId = "demo-user",
                    textOnly = false,
                    overrides = null,
                    customLlmExtraBody = null,
                    dynamicVariables = null,
                    clientTools = mapOf(
                        "logMessage" to object : io.elevenlabs.ClientTool {
                            override suspend fun execute(parameters: Map<String, Any>): io.elevenlabs.ClientToolResult? {
                                val message = parameters["message"] as? String
                                    ?: return io.elevenlabs.ClientToolResult.failure("Missing 'message' parameter")
                                val level = parameters["level"] as? String ?: "INFO"

                                Log.d("ExampleApp", "[$level] Client Tool Log: $message")
                                return io.elevenlabs.ClientToolResult.success("Message logged successfully")
                            }
                        }
                    ),
                    onConnect = { conversationId ->
                        Log.d("ConversationViewModel", "Connected id=$conversationId")
                    },
                    onMessage = { source, message ->
                        // Receive messages from the server. Can be quite noisy hence commented out
                        // Log.d("ConversationViewModel", "onMessage [$source]: $message")
                     },
                    onModeChange = { mode ->
                        _mode.postValue(mode)
                    },
                    onStatusChange = { status ->
                        Log.d("ConversationViewModel", "onStatusChange: $status")
                    },
                    onCanSendFeedbackChange = { canSendFeedback ->
                        _canSendFeedback.postValue(canSendFeedback)
                        Log.d("ConversationViewModel", "onCanSendFeedbackChange: $canSendFeedback")
                    },
                    onUnhandledClientToolCall = { toolCall ->
                        Log.d("ConversationViewModel", "onUnhandledClientToolCall: $toolCall")
                    },
                    onVadScore = { score ->
                        // VAD score is used to determine if the user is speaking.
                        // Can be used to trigger UI changes or audio processing decisions.
                        // Log commented out as it's quite noisy
                        // Log.d("ConversationViewModel", "onVadScore: $score")
                    }
                )

                val session = ConversationClient.startSession(config, activityContext)

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

                // Observe mute state if available
                session.isMuted.observeForever { muted ->
                    _isMuted.value = muted
                }

                Log.d("ConversationViewModel", "Session created and started successfully")

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

                currentSession?.endSession()
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

    fun sendFeedback(isPositive: Boolean) {
        currentSession?.sendFeedback(isPositive)
        Log.d("ConversationViewModel", "Sent ${if (isPositive) "positive" else "negative"} feedback")
    }

    fun sendContextualUpdate(text: String) {
        try {
            currentSession?.sendContextualUpdate(text)
        } catch (t: Throwable) {
            Log.d("ConversationViewModel", "Failed to send contextual update: ${t.message}")
        }
    }

    fun sendUserMessage(text: String) {
        try {
            currentSession?.sendUserMessage(text)
        } catch (t: Throwable) {
            Log.d("ConversationViewModel", "Failed to send user message: ${t.message}")
        }
    }

    fun sendUserActivity() {
        try {
            currentSession?.sendUserActivity()
        } catch (t: Throwable) {
            Log.d("ConversationViewModel", "Failed to send user activity: ${t.message}")
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            try {
                currentSession?.toggleMute()
            } catch (t: Throwable) {
                Log.d("ConversationViewModel", "Failed to toggle mute: ${t.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        endConversation()
    }
}