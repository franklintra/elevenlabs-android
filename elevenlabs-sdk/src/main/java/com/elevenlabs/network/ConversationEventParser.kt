package com.elevenlabs.network

import android.util.Log
import com.elevenlabs.models.ConversationEvent
import com.elevenlabs.models.ConversationMode
import com.elevenlabs.models.ConversationStatus
import com.google.gson.*
import com.google.gson.annotations.SerializedName

/**
 * JSON event processing for real-time conversation protocol
 *
 * This object handles parsing incoming events from ElevenLabs servers and
 * serializing outgoing user actions and responses. It provides robust
 * error handling for malformed messages.
 */
object ConversationEventParser {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    /**
     * Parse an incoming JSON event string into a ConversationEvent
     *
     * @param json The JSON string to parse
     * @return ConversationEvent instance or null if parsing fails
     */
    fun parseIncomingEvent(json: String): ConversationEvent? {
        return try {
            val jsonObject = JsonParser.parseString(json).asJsonObject
            val eventType = getEventType(jsonObject)

            when (eventType) {
                "agent_response" -> parseAgentResponse(jsonObject)
                "user_transcript" -> parseUserTranscript(jsonObject)
                "interruption" -> parseInterruption(jsonObject)
                "client_tool_call" -> parseClientToolCall(jsonObject)
                "mode_change" -> parseModeChange(jsonObject)
                "vad_score" -> parseVadScore(jsonObject)
                "connection_state_change" -> parseConnectionStateChange(jsonObject)
                "error" -> parseError(jsonObject)
                "ping" -> parsePing(jsonObject)
                "agent_tool_response" -> {
                    // Ignore quietly; this is the server acknowledging a tool result
                    logAgentToolResponse(jsonObject)
                    null
                }
                else -> {
                    handleParsingError(json, IllegalArgumentException("Unknown event type: $eventType"))
                    null
                }
            }
        } catch (e: Exception) {
            handleParsingError(json, e)
            null
        }
    }

    /**
     * Parse ping event
     * Matches payload: {"ping_event":{"event_id":3,"ping_ms":null},"type":"ping"}
     */
    private fun parsePing(jsonObject: JsonObject): ConversationEvent.Ping {
        val ping = jsonObject.getAsJsonObject("ping_event")
        val eventId = ping?.get("event_id")?.asInt ?: 0
        val pingMs = ping?.get("ping_ms")?.let { if (it.isJsonNull) null else it.asLong }
        return ConversationEvent.Ping(eventId = eventId, pingMs = pingMs)
    }

    /**
     * Serialize an outgoing event to JSON string
     *
     * @param event The event to serialize
     * @return JSON string representation of the event
     */
    fun serializeOutgoingEvent(event: OutgoingEvent): String {
        return gson.toJson(event)
    }

    /**
     * Extract the event type from a JSON object
     */
    private fun getEventType(jsonObject: JsonObject): String? {
        return jsonObject.get("type")?.asString
    }

    /**
     * Parse agent response event
     */
    private fun parseAgentResponse(jsonObject: JsonObject): ConversationEvent.AgentResponse {
        val obj = jsonObject.getAsJsonObject("agent_response_event")
            ?: jsonObject.getAsJsonObject("agent_response")
            ?: jsonObject

        val content = when {
            obj.get("agent_response") != null && !obj.get("agent_response").isJsonNull -> obj.get("agent_response").asString
            obj.get("content") != null && !obj.get("content").isJsonNull -> obj.get("content").asString
            obj.get("text") != null && !obj.get("text").isJsonNull -> obj.get("text").asString
            obj.get("message") != null && !obj.get("message").isJsonNull -> obj.get("message").asString
            else -> ""
        }

        val eventId = obj.get("event_id")?.asString?.takeIf { it.isNotBlank() }
            ?: "evt_${System.currentTimeMillis()}"

        val timestamp = obj.get("timestamp")?.asLong ?: System.currentTimeMillis()

        return ConversationEvent.AgentResponse(
            content = content,
            eventId = eventId,
            timestamp = timestamp
        )
    }

    /**
     * Parse user transcript event
     */
    private fun parseUserTranscript(jsonObject: JsonObject): ConversationEvent.UserTranscript {
        val obj = jsonObject.getAsJsonObject("user_transcription_event")
            ?: jsonObject.getAsJsonObject("user_transcript")
            ?: jsonObject

        val content = when {
            obj.get("user_transcript") != null && !obj.get("user_transcript").isJsonNull -> obj.get("user_transcript").asString
            obj.get("content") != null && !obj.get("content").isJsonNull -> obj.get("content").asString
            obj.get("text") != null && !obj.get("text").isJsonNull -> obj.get("text").asString
            obj.get("transcript") != null && !obj.get("transcript").isJsonNull -> obj.get("transcript").asString
            else -> ""
        }

        val eventId = obj.get("event_id")?.asString?.takeIf { it.isNotBlank() }
            ?: "evt_${System.currentTimeMillis()}"

        val timestamp = obj.get("timestamp")?.asLong ?: System.currentTimeMillis()

        val isFinal = when {
            obj.get("is_final") != null && !obj.get("is_final").isJsonNull -> obj.get("is_final").asBoolean
            obj.get("final") != null && !obj.get("final").isJsonNull -> obj.get("final").asBoolean
            else -> true
        }

        return ConversationEvent.UserTranscript(
            content = content,
            eventId = eventId,
            timestamp = timestamp,
            isFinal = isFinal
        )
    }

    /**
     * Parse interruption event
     */
    private fun parseInterruption(jsonObject: JsonObject): ConversationEvent.Interruption {
        return ConversationEvent.Interruption(
            eventId = jsonObject.get("event_id")?.asString ?: "",
            timestamp = jsonObject.get("timestamp")?.asLong ?: System.currentTimeMillis()
        )
    }

    /**
     * Parse client tool call event
     */
    private fun parseClientToolCall(jsonObject: JsonObject): ConversationEvent.ClientToolCall {
        // Payloads can be either flat or nested under "client_tool_call"
        val obj = jsonObject.getAsJsonObject("client_tool_call") ?: jsonObject

        val parametersJson = obj.get("parameters")?.asJsonObject
        val parameters = mutableMapOf<String, Any>()

        parametersJson?.entrySet()?.forEach { entry ->
            parameters[entry.key] = when {
                entry.value.isJsonPrimitive -> {
                    val primitive = entry.value.asJsonPrimitive
                    when {
                        primitive.isString -> primitive.asString
                        primitive.isNumber -> primitive.asNumber
                        primitive.isBoolean -> primitive.asBoolean
                        else -> primitive.asString
                    }
                }
                entry.value.isJsonArray -> gson.fromJson(entry.value, List::class.java)
                entry.value.isJsonObject -> gson.fromJson(entry.value, Map::class.java)
                else -> entry.value.toString()
            }
        }

        return ConversationEvent.ClientToolCall(
            toolName = obj.get("tool_name")?.asString ?: "",
            parameters = parameters,
            toolCallId = obj.get("tool_call_id")?.asString ?: "",
            expectsResponse = obj.get("expects_response")?.asBoolean ?: true,
            timestamp = obj.get("timestamp")?.asLong ?: System.currentTimeMillis()
        )
    }

    private fun logAgentToolResponse(jsonObject: JsonObject) {
        try {
            Log.d("ConversationEventParser", "Agent tool response: ${jsonObject}")
        } catch (_: Exception) { }
    }

    /**
     * Parse mode change event
     */
    private fun parseModeChange(jsonObject: JsonObject): ConversationEvent.ModeChange {
        val modeString = jsonObject.get("mode")?.asString ?: "listening"
        val mode = when (modeString.lowercase()) {
            "speaking" -> ConversationMode.SPEAKING
            "listening" -> ConversationMode.LISTENING
            else -> ConversationMode.LISTENING
        }

        return ConversationEvent.ModeChange(
            mode = mode,
            timestamp = jsonObject.get("timestamp")?.asLong ?: System.currentTimeMillis()
        )
    }

    /**
     * Parse VAD score event
     */
    private fun parseVadScore(jsonObject: JsonObject): ConversationEvent.VadScore {
        return ConversationEvent.VadScore(
            score = jsonObject.get("score")?.asFloat ?: 0.0f,
            timestamp = jsonObject.get("timestamp")?.asLong ?: System.currentTimeMillis()
        )
    }

    /**
     * Parse connection state change event
     */
    private fun parseConnectionStateChange(jsonObject: JsonObject): ConversationEvent.ConnectionStateChange {
        val statusString = jsonObject.get("status")?.asString ?: "disconnected"
        val status = when (statusString.lowercase()) {
            "connected" -> ConversationStatus.CONNECTED
            "connecting" -> ConversationStatus.CONNECTING
            "disconnecting" -> ConversationStatus.DISCONNECTING
            "error" -> ConversationStatus.ERROR
            else -> ConversationStatus.DISCONNECTED
        }

        return ConversationEvent.ConnectionStateChange(
            status = status,
            reason = jsonObject.get("reason")?.asString,
            timestamp = jsonObject.get("timestamp")?.asLong ?: System.currentTimeMillis()
        )
    }

    /**
     * Parse error event
     */
    private fun parseError(jsonObject: JsonObject): ConversationEvent.Error {
        return ConversationEvent.Error(
            error = jsonObject.get("error")?.asString ?: "Unknown error",
            code = jsonObject.get("code")?.asString,
            timestamp = jsonObject.get("timestamp")?.asLong ?: System.currentTimeMillis()
        )
    }

    /**
     * Handle parsing errors
     */
    private fun handleParsingError(json: String, error: Exception) {
        Log.d("ConversationEventParser", "Failed to parse conversation event: ${error.message}")
        Log.d("ConversationEventParser", "JSON: $json")
    }
}

/**
 * Base class for outgoing events that can be sent to the server
 */
sealed class OutgoingEvent {
    abstract val type: String
    abstract val eventId: String

    /**
     * User message event
     */
    data class UserMessage(
        val content: String,
        @SerializedName("event_id")
        override val eventId: String = generateEventId()
    ) : OutgoingEvent() {
        override val type = "user_message"
    }

    /**
     * Feedback event
     */
    data class Feedback(
        @SerializedName("is_positive")
        val isPositive: Boolean,
        @SerializedName("target_event_id")
        val targetEventId: String,
        @SerializedName("event_id")
        override val eventId: String = generateEventId()
    ) : OutgoingEvent() {
        override val type = "feedback"
    }

    /**
     * Contextual update event
     */
    data class ContextualUpdate(
        val content: String,
        @SerializedName("event_id")
        override val eventId: String = generateEventId()
    ) : OutgoingEvent() {
        override val type = "contextual_update"
    }

    /**
     * Tool result event
     */
    data class ToolResult(
        @SerializedName("tool_call_id")
        val toolCallId: String,
        val result: Map<String, Any>,
        @SerializedName("is_error")
        val isError: Boolean = false,
        @SerializedName("event_id")
        override val eventId: String = generateEventId()
    ) : OutgoingEvent() {
        override val type = "tool_result"
    }

    /**
     * Pong reply for ping
     */
    data class Pong(
        @SerializedName("event_id")
        override val eventId: String
    ) : OutgoingEvent() {
        override val type: String = "pong"
    }

    companion object {
        /**
         * Generate a unique event ID
         */
        private fun generateEventId(): String {
            return "evt_${System.currentTimeMillis()}_${(1000..9999).random()}"
        }
    }
}