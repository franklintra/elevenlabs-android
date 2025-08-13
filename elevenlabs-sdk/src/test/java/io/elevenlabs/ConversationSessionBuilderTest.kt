package io.elevenlabs

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class ConversationSessionBuilderTest {

    private lateinit var mockContext: Context
    private lateinit var mockSession: ConversationSession
    private lateinit var mockTool: ClientTool

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockSession = mockk(relaxed = true)
        mockTool = mockk(relaxed = true)

        clearAllMocks()

        // Mock ConversationClientImpl
        mockkObject(ConversationClientImpl)
        coEvery {
            ConversationClientImpl.startSession(any(), any())
        } returns mockSession

        // Mock the registerTool method on ConversationSession
        every { mockSession.registerTool(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `build throws exception when no config is set`() {
        val builder = ConversationSessionBuilder(mockContext)

        val exception = assertThrows(IllegalStateException::class.java) {
            runTest {
                builder.build()
            }
        }

        assertEquals("Configuration is required", exception.message)
    }

    @Test
    fun `config method returns builder for chaining`() {
        val builder = ConversationSessionBuilder(mockContext)
        val config = ConversationConfig(conversationToken = "test-token")

        val result = builder.config(config)

        assertEquals(builder, result)
    }

    @Test
    fun `addTool with ClientTool returns builder for chaining`() {
        val builder = ConversationSessionBuilder(mockContext)

        val result = builder.addTool("testTool", mockTool)

        assertEquals(builder, result)
    }

    @Test
    fun `addTool with function returns builder for chaining`() {
        val builder = ConversationSessionBuilder(mockContext)
        val mockFunction: suspend (Map<String, Any>) -> String = mockk()

        val result = builder.addTool("testTool", mockFunction)

        assertEquals(builder, result)
    }

    @Test
    fun `build works with valid config`() = runTest {
        val config = ConversationConfig(conversationToken = "test-token")
        val builder = ConversationSessionBuilder(mockContext)
            .config(config)

        val result = builder.build()

        assertEquals(mockSession, result)
        coVerify { ConversationClientImpl.startSession(config, mockContext) }
    }

    @Test
    fun `build registers custom ClientTool`() = runTest {
        val config = ConversationConfig(conversationToken = "test-token")
        val builder = ConversationSessionBuilder(mockContext)
            .config(config)
            .addTool("customTool", mockTool)

        builder.build()

        verify { mockSession.registerTool("customTool", mockTool) }
    }

    @Test
    fun `build registers function-based tool`() = runTest {
        val config = ConversationConfig(conversationToken = "test-token")
        val testFunction: suspend (Map<String, Any>) -> String = { params ->
            "result: ${params["input"]}"
        }

        val builder = ConversationSessionBuilder(mockContext)
            .config(config)
            .addTool("functionTool", testFunction)

        builder.build()

        verify {
            mockSession.registerTool(eq("functionTool"), any<ClientTool>())
        }
    }

    @Test
    fun `function-based tool executes correctly`() = runTest {
        var capturedTool: ClientTool? = null

        // Capture the tool that gets registered
        every { mockSession.registerTool(any(), any()) } answers {
            capturedTool = secondArg()
        }

        val config = ConversationConfig(conversationToken = "test-token")
        val testFunction: suspend (Map<String, Any>) -> String = { params ->
            "result: ${params["input"]}"
        }

        val builder = ConversationSessionBuilder(mockContext)
            .config(config)
            .addTool("functionTool", testFunction)

        builder.build()

        // Test the captured tool
        assertNotNull(capturedTool)
        val result = capturedTool!!.execute(mapOf("input" to "test"))
        assertTrue(result.success)
        assertEquals("result: test", result.result)
    }

    @Test
    fun `function-based tool handles exceptions`() = runTest {
        var capturedTool: ClientTool? = null

        every { mockSession.registerTool(any(), any()) } answers {
            capturedTool = secondArg()
        }

        val config = ConversationConfig(conversationToken = "test-token")
        val testFunction: suspend (Map<String, Any>) -> String = { _ ->
            throw RuntimeException("Test exception")
        }

        val builder = ConversationSessionBuilder(mockContext)
            .config(config)
            .addTool("functionTool", testFunction)

        builder.build()

        // Test the captured tool
        assertNotNull(capturedTool)
        val result = capturedTool!!.execute(emptyMap())
        assertFalse(result.success)
        assertEquals("Function execution failed: Test exception", result.error)
    }

    @Test
    fun `builder pattern allows method chaining`() = runTest {
        val config = ConversationConfig(conversationToken = "test-token")

        val result = ConversationSessionBuilder(mockContext)
            .config(config)
            .addTool("tool1", mockTool)
            .addTool("tool2") { params -> "result" }
            .build()

        assertEquals(mockSession, result)
        verify { mockSession.registerTool("tool1", mockTool) }
        verify { mockSession.registerTool(eq("tool2"), any<ClientTool>()) }
    }
}
