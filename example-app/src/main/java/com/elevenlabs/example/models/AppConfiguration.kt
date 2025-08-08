package com.elevenlabs.example.models

import com.elevenlabs.ConversationConfig
import com.elevenlabs.Overrides
import com.elevenlabs.AgentOverrides

/**
 * Configuration data for the conversation
 *
 * Supports two modes:
 * - Public agents: Use agentId only (no API key needed)
 * - Private agents: Use conversationToken only (generated on your backend)
 */
data class AppConfiguration(
    val agentId: String? = null,
    val conversationToken: String? = null,
    val userId: String? = null,
    val textOnly: Boolean = false
) {
    /**
     * Convert to SDK ConversationConfig
     */
    fun toConversationConfig(): ConversationConfig {
        return ConversationConfig(
            agentId = agentId,
            conversationToken = conversationToken,
            userId = userId,
            textOnly = textOnly,
            overrides = Overrides(
                agent = AgentOverrides(
                    firstMessage = "Hello, how are you?"
                )
            ),
            onConnect = { conversationId ->
                android.util.Log.d("ExampleApp", "Connected to conversation ${conversationId}")
            }
        )
    }

    /**
     * Validate the configuration
     */
    fun isValid(): Boolean {
        return !agentId.isNullOrBlank() || !conversationToken.isNullOrBlank()
    }

    /**
     * Get a description of this configuration
     */
    fun getDescription(): String {
        return when {
            !agentId.isNullOrBlank() -> "Public Agent: ${agentId.take(8)}..."
            !conversationToken.isNullOrBlank() -> "Private Agent Token: ${conversationToken.take(8)}..."
            else -> "Invalid Configuration"
        }
    }

    companion object {
        /**
         * Create a public agent configuration
         */
        fun publicAgent(
            agentId: String,
            userId: String? = null,
            textOnly: Boolean = false
        ): AppConfiguration {
            return AppConfiguration(
                agentId = agentId,
                userId = userId,
                textOnly = textOnly
            )
        }

        /**
         * Create a private agent configuration using a conversation token
         * The token should be generated on your backend using your API key
         */
        fun privateAgent(
            conversationToken: String,
            userId: String? = null,
            textOnly: Boolean = false
        ): AppConfiguration {
            return AppConfiguration(
                conversationToken = conversationToken,
                userId = userId,
                textOnly = textOnly
            )
        }

        /**
         * Create a default demo configuration for testing
         * Uses a public agent ID that doesn't require authentication
         */
        fun demo(): AppConfiguration {
            return AppConfiguration(
                agentId = "J3Pbu5gP6NNKBscdCdwA", // Demo public agent ID
                userId = "demo-user",
                textOnly = false
            )
        }
    }
}