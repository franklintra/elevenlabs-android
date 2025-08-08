package com.elevenlabs

import android.content.Context
import android.util.Log
import com.elevenlabs.audio.AudioSessionManager
import com.elevenlabs.audio.LiveKitAudioManager
import com.elevenlabs.network.TokenService
import com.elevenlabs.network.WebRTCConnection
import io.livekit.android.LiveKit

/**
 * Internal implementation of ConversationClient
 *
 * This class creates fully integrated conversation sessions with all components:
 * network layer, audio management, event handling, and client tools.
 */
internal object ConversationClientImpl {

    /**
     * Factory method to create and start a conversation session
     *
     * @param config Configuration for the conversation
     * @param context Android context for audio and network setup
     * @return A new ConversationSession instance
     */
    suspend fun startSession(
        config: ConversationConfig,
        context: Context
    ): ConversationSession {

        // Validate configuration
        validateConfig(config)

        // Generate token if needed for public agents
        val finalConfig = if (!config.isPrivateAgent) {
            val tokenService = TokenService()
            val tokenResponse = tokenService.fetchPublicAgentToken(config.agentId!!)
            config.copy(conversationToken = tokenResponse.token, agentId = null)
        } else {
            config
        }

        // Create LiveKit room
        val room = LiveKit.create(context)
        Log.d("ConversationClient", "Created LiveKit room instance @${room.hashCode()}")

        // Create connection with shared room (ensures consistent permission state)
        val connection = WebRTCConnection(context, room)
        Log.d("ConversationClient", "WebRTCConnection initialized")

        // Create audio manager
        val audioSessionManager = AudioSessionManager(context)
        val audioManager = LiveKitAudioManager(context, room)
        Log.d("ConversationClient", "LiveKitAudioManager initialized")

        // Create client tools registry with common tools
        val toolRegistry = ClientToolRegistryBuilder()
            .addTool("get_current_time", CommonClientTools.getCurrentTime)
            .addTool("get_device_info", CommonClientTools.getDeviceInfo)
            .addTool("log_message", CommonClientTools.logMessage)
            .build()

        // Configure audio session for conversation
        if (!finalConfig.textOnly) {
            audioSessionManager.configureForVoiceCall()
        }

        // Create the session
        return ConversationSessionImpl(
            context = context,
            config = finalConfig,
            room = room,
            connection = connection,
            audioManager = audioManager,
            toolRegistry = toolRegistry
        )
    }



    /**
     * Validate the conversation configuration
     *
     * @param config Configuration to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    private fun validateConfig(config: ConversationConfig) {
        // Configuration validation is already done in the ConversationConfig constructor
        // Additional validation can be added here if needed

        if (config.isPrivateAgent && config.conversationToken.isNullOrBlank()) {
            throw IllegalArgumentException("Private agent requires a valid conversation token")
        }

        if (!config.isPrivateAgent && config.agentId.isNullOrBlank()) {
            throw IllegalArgumentException("Public agent requires a valid agent ID")
        }
    }

    /**
     * Create a conversation session builder for advanced configuration
     *
     * @param context Android context
     * @return ConversationSessionBuilder instance
     */
    fun builder(context: Context): ConversationSessionBuilder {
        return ConversationSessionBuilder(context)
    }
}

/**
 * Builder class for creating customized conversation sessions
 */
class ConversationSessionBuilder(private val context: Context) {

    private var config: ConversationConfig? = null
    private val customTools = mutableMapOf<String, ClientTool>()
    private var audioSettings: com.elevenlabs.audio.AudioSettings? = null

    /**
     * Set the conversation configuration
     */
    fun config(config: ConversationConfig): ConversationSessionBuilder {
        this.config = config
        return this
    }

    /**
     * Add a custom client tool
     */
    fun addTool(name: String, tool: ClientTool): ConversationSessionBuilder {
        customTools[name] = tool
        return this
    }

    /**
     * Add a simple function-based tool
     */
    fun addTool(name: String, function: suspend (Map<String, Any>) -> String): ConversationSessionBuilder {
        customTools[name] = object : ClientTool {
            override suspend fun execute(parameters: Map<String, Any>): ClientToolResult {
                return try {
                    val result = function(parameters)
                    ClientToolResult.success(result)
                } catch (e: Exception) {
                    ClientToolResult.failure("Function execution failed: ${e.message}")
                }
            }
        }
        return this
    }

    /**
     * Set custom audio settings
     */
    fun audioSettings(settings: com.elevenlabs.audio.AudioSettings): ConversationSessionBuilder {
        this.audioSettings = settings
        return this
    }

    /**
     * Build the conversation session
     */
    suspend fun build(): ConversationSession {
        val sessionConfig = config ?: throw IllegalStateException("Configuration is required")

        // Create the basic session
        val session = ConversationClientImpl.startSession(sessionConfig, context)

        // Register custom tools
        customTools.forEach { (name, tool) ->
            session.registerTool(name, tool)
        }

        return session
    }
}