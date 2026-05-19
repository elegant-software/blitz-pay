package com.elegant.software.blitzpay.betterprice.agent.a2a

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.messages.ContextMessageStorage
import ai.koog.a2a.server.messages.InMemoryMessageStorage
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.server.tasks.ContextTaskStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import com.elegant.software.blitzpay.betterprice.agent.application.AgentResponse
import com.elegant.software.blitzpay.betterprice.agent.application.MarketSearchAgentService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MarketSearchA2aAgentExecutorTest {

    private val service = mock<MarketSearchAgentService>()
    private val executor = MarketSearchA2aAgentExecutor(service)
    private val taskStorage = InMemoryTaskStorage()
    private val messageStorage = InMemoryMessageStorage()

    @Test
    fun `execute publishes completed task event with structured payload`() = runBlocking {
        whenever(service.handleTextRequest("""{"productTitle":"Sony WH-1000XM5"}""")).thenReturn(
            AgentResponse("""{"status":"better_price_found"}""", true)
        )

        val requestContext = requestContext("""{"productTitle":"Sony WH-1000XM5"}""")
        val eventProcessor = SessionEventProcessor(requestContext.contextId, requestContext.taskId, taskStorage)

        executor.execute(requestContext, eventProcessor)

        val task = ContextTaskStorage(requestContext.contextId, taskStorage).get(requestContext.taskId, null, false)
        requireNotNull(task)
        assertEquals("completed", task.status.state.name.lowercase())
        assertEquals("""{"status":"better_price_found"}""", ((task.status.message ?: error("missing task message")).parts.single() as TextPart).text)
    }

    @Test
    fun `execute publishes failed task event when structured request processing fails`() = runBlocking {
        whenever(service.handleTextRequest("bad payload")).thenReturn(
            AgentResponse("Please provide a product price comparison JSON payload.", false)
        )

        val requestContext = requestContext("bad payload")
        val eventProcessor = SessionEventProcessor(requestContext.contextId, requestContext.taskId, taskStorage)

        executor.execute(requestContext, eventProcessor)

        val task = ContextTaskStorage(requestContext.contextId, taskStorage).get(requestContext.taskId, null, false)
        requireNotNull(task)
        assertEquals("failed", task.status.state.name.lowercase())
        assertEquals(
            "Please provide a product price comparison JSON payload.",
            ((task.status.message ?: error("missing task message")).parts.single() as TextPart).text
        )
    }

    private fun requestContext(text: String): RequestContext<MessageSendParams> = RequestContext(
        callContext = ServerCallContext(),
        params = MessageSendParams(
            message = Message(
                messageId = "msg-1",
                role = Role.User,
                parts = listOf(TextPart(text))
            )
        ),
        taskStorage = ContextTaskStorage("ctx-1", taskStorage),
        messageStorage = ContextMessageStorage("ctx-1", messageStorage),
        contextId = "ctx-1",
        taskId = "task-1",
        task = null
    )
}
