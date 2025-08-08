package com.elevenlabs.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Service for fetching conversation tokens from ElevenLabs API
 *
 * This service handles authentication with the ElevenLabs API to obtain
 * conversation tokens for private agents and connection details.
 */
class TokenService(
    private val baseUrl: String = "https://api.elevenlabs.io",
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {

    /**
     * Fetch a conversation token for a public agent (no API key required)
     *
     * @param agentId The ID of the public agent to get a token for
     * @return TokenResponse containing the token and connection details
     * @throws TokenServiceException if the request fails or returns an error
     */
    suspend fun fetchPublicAgentToken(agentId: String): TokenResponse = withContext(Dispatchers.IO) {
        val url = buildTokenUrl(agentId)

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .get()
            .build()

        try {
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw TokenServiceException(
                    "Failed to fetch public agent token: HTTP ${response.code} - $errorBody"
                )
            }

            val responseBody = response.body?.string()
                ?: throw TokenServiceException("Empty response body")

            try {
                val parsed = gson.fromJson(responseBody, TokenResponse::class.java)
                    ?: throw TokenServiceException("Failed to parse token response")

                Log.d("TokenService", "Public agent token fetched for agentId=$agentId, tokenLength=${parsed.token.length}, wsUrl=${parsed.webSocketUrl}")
                parsed
            } catch (e: Exception) {
                throw TokenServiceException("Failed to parse token response: ${e.message}", e)
            }

        } catch (e: IOException) {
            throw TokenServiceException("Network error: ${e.message}", e)
        }
    }

    /**
     * Fetch a conversation token for a private agent (requires API key)
     * This method should typically be called from your backend, not client-side apps
     *
     * @param agentId The ID of the agent to get a token for
     * @param apiKey The ElevenLabs API key for authentication
     * @return TokenResponse containing the token and connection details
     * @throws TokenServiceException if the request fails or returns an error
     */
    suspend fun fetchPrivateAgentToken(
        agentId: String,
        apiKey: String
    ): TokenResponse = withContext(Dispatchers.IO) {
        val url = buildTokenUrl(agentId)

        val request = Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey)
            .addHeader("Accept", "application/json")
            .get()
            .build()

        try {
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw TokenServiceException(
                    "Failed to fetch private agent token: HTTP ${response.code} - $errorBody"
                )
            }

            val responseBody = response.body?.string()
                ?: throw TokenServiceException("Empty response body")

            try {
                val parsed = gson.fromJson(responseBody, TokenResponse::class.java)
                    ?: throw TokenServiceException("Failed to parse token response")

                Log.d("TokenService", "Private agent token fetched for agentId=$agentId, tokenLength=${parsed.token.length}, wsUrl=${parsed.webSocketUrl}")
                parsed
            } catch (e: Exception) {
                throw TokenServiceException("Failed to parse token response: ${e.message}", e)
            }

        } catch (e: IOException) {
            throw TokenServiceException("Network error: ${e.message}", e)
        }
    }

    /**
     * Build the URL for fetching conversation tokens
     *
     * @param agentId The agent ID to include in the request
     * @return Complete URL for the token request
     */
    private fun buildTokenUrl(agentId: String): String {
        return "$baseUrl/v1/convai/conversation/token?agent_id=$agentId"
    }
}

/**
 * Response from the ElevenLabs token API
 *
 * @param token The conversation token to use for authentication
 * @param webSocketUrl The WebSocket URL for the conversation connection
 * @param expires Optional expiration timestamp for the token
 */
data class TokenResponse(
    @SerializedName("token")
    val token: String,

    @SerializedName("websocket_url")
    val webSocketUrl: String? = null,

    @SerializedName("expires")
    val expires: Long? = null
)

/**
 * Exception thrown when token service operations fail
 */
class TokenServiceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)