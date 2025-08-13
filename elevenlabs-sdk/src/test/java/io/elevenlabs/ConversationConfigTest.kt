package io.elevenlabs

import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.*

class ConversationConfigTest {

    @Test
    fun `config with agentId is not private agent`() {
        val config = ConversationConfig(
            agentId = "test-agent-id",
            conversationToken = null
        )

        assertFalse(config.isPrivateAgent)
    }

    @Test
    fun `config with conversationToken is private agent`() {
        val config = ConversationConfig(
            agentId = null,
            conversationToken = "test-token"
        )

        assertTrue(config.isPrivateAgent)
    }

    @Test
    fun `config with both agentId and conversationToken is private agent`() {
        val config = ConversationConfig(
            agentId = "test-agent-id",
            conversationToken = "test-token"
        )

        assertTrue(config.isPrivateAgent)
    }

    @Test
    fun `config with blank agentId throws exception`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConversationConfig(
                agentId = "",
                conversationToken = null
            )
        }

        assertEquals("agentId cannot be blank", exception.message)
    }

    @Test
    fun `config with whitespace agentId throws exception`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConversationConfig(
                agentId = "   ",
                conversationToken = null
            )
        }

        assertEquals("agentId cannot be blank", exception.message)
    }

    @Test
    fun `config with blank conversationToken throws exception`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConversationConfig(
                agentId = null,
                conversationToken = ""
            )
        }

        assertEquals("conversationToken cannot be blank", exception.message)
    }

    @Test
    fun `config with whitespace conversationToken throws exception`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConversationConfig(
                agentId = null,
                conversationToken = "   "
            )
        }

        assertEquals("conversationToken cannot be blank", exception.message)
    }

    @Test
    fun `config with valid agentId succeeds`() {
        val config = ConversationConfig(
            agentId = "valid-agent-id",
            conversationToken = null
        )

        assertEquals("valid-agent-id", config.agentId)
        assertNull(config.conversationToken)
        assertFalse(config.isPrivateAgent)
    }

    @Test
    fun `config with valid conversationToken succeeds`() {
        val config = ConversationConfig(
            agentId = null,
            conversationToken = "valid-token"
        )

        assertNull(config.agentId)
        assertEquals("valid-token", config.conversationToken)
        assertTrue(config.isPrivateAgent)
    }

    @Test
    fun `config with all optional fields`() {
        val mockTool = mockk<ClientTool>()
        val onConnect: (String) -> Unit = {}
        val onMessage: (String, String) -> Unit = { _, _ -> }

        val overrides = Overrides(
            client = ClientOverrides(source = "test-source", version = "1.0.0")
        )

        val config = ConversationConfig(
            agentId = "test-agent",
            conversationToken = null,
            userId = "test-user",
            textOnly = true,
            overrides = overrides,
            customLlmExtraBody = mapOf("key" to "value"),
            dynamicVariables = mapOf("var1" to "value1"),
            clientTools = mapOf("tool1" to mockTool),
            onConnect = onConnect,
            onMessage = onMessage
        )

        assertEquals("test-agent", config.agentId)
        assertEquals("test-user", config.userId)
        assertTrue(config.textOnly)
        assertEquals(overrides, config.overrides)
        assertEquals(mapOf("key" to "value"), config.customLlmExtraBody)
        assertEquals(mapOf("var1" to "value1"), config.dynamicVariables)
        assertEquals(mapOf("tool1" to mockTool), config.clientTools)
        assertEquals(onConnect, config.onConnect)
        assertEquals(onMessage, config.onMessage)
        assertFalse(config.isPrivateAgent)
    }

    @Test
    fun `config defaults are correct`() {
        val config = ConversationConfig(
            agentId = "test-agent"
        )

        assertNull(config.conversationToken)
        assertNull(config.userId)
        assertFalse(config.textOnly)
        assertNull(config.overrides)
        assertNull(config.customLlmExtraBody)
        assertNull(config.dynamicVariables)
        assertTrue(config.clientTools.isEmpty())
        assertNull(config.onConnect)
        assertNull(config.onMessage)
        assertNull(config.onModeChange)
        assertNull(config.onStatusChange)
        assertNull(config.onCanSendFeedbackChange)
        assertNull(config.onUnhandledClientToolCall)
    }
}
