package io.elevenlabs

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class ConversationClientTest {

    private lateinit var mockContext: Context
    private lateinit var mockSession: ConversationSession

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockSession = mockk(relaxed = true)

        clearAllMocks()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `startSession calls ConversationClientImpl startSession`() = runTest {
        val config = ConversationConfig(
            conversationToken = "test-token"
        )

        // Mock the ConversationClientImpl singleton object
        mockkObject(ConversationClientImpl)
        coEvery {
            ConversationClientImpl.startSession(config, mockContext)
        } returns mockSession

        val result = ConversationClient.startSession(config, mockContext)

        assertEquals(mockSession, result)
        coVerify { ConversationClientImpl.startSession(config, mockContext) }
    }

    @Test
    fun `builder calls ConversationClientImpl builder`() {
        val mockBuilder = mockk<ConversationSessionBuilder>()

        // Mock the ConversationClientImpl singleton object
        mockkObject(ConversationClientImpl)
        every {
            ConversationClientImpl.builder(mockContext)
        } returns mockBuilder

        val result = ConversationClient.builder(mockContext)

        assertEquals(mockBuilder, result)
        verify { ConversationClientImpl.builder(mockContext) }
    }
}
