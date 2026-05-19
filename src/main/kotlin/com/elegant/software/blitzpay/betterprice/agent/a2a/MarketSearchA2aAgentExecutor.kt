package com.elegant.software.blitzpay.betterprice.agent.a2a

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import com.elegant.software.blitzpay.betterprice.agent.application.MarketSearchAgentService
import kotlinx.coroutines.Deferred
import kotlinx.datetime.Clock
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MarketSearchA2aAgentExecutor(
    private val marketSearchAgentService: MarketSearchAgentService
) : AgentExecutor {

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val input = context.params.message.parts
            .filterIsInstance<TextPart>()
            .joinToString(separator = "\n") { it.text }
            .trim()

        eventProcessor.sendTaskEvent(
            Task(
                id = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Submitted,
                    message = agentMessage(
                        contextId = context.contextId,
                        taskId = context.taskId,
                        content = if (input.isBlank()) "Request submitted" else "Request submitted: $input"
                    ),
                    timestamp = Clock.System.now()
                )
            )
        )

        eventProcessor.sendTaskEvent(
            taskUpdate(
                context = context,
                content = "Running KOOG product price comparison",
                state = TaskState.Working,
                final = false
            )
        )

        val response = marketSearchAgentService.handleTextRequest(input)
        eventProcessor.sendTaskEvent(
            taskUpdate(
                context = context,
                content = response.message,
                state = if (response.success) TaskState.Completed else TaskState.Failed,
                final = true
            )
        )
    }

    override suspend fun cancel(
        context: RequestContext<ai.koog.a2a.model.TaskIdParams>,
        eventProcessor: SessionEventProcessor,
        agentJob: Deferred<Unit>?
    ) {
        agentJob?.cancel()
        eventProcessor.sendTaskEvent(
            Task(
                id = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Canceled,
                    message = agentMessage(context.contextId, context.taskId, "A2A request canceled"),
                    timestamp = Clock.System.now()
                )
            )
        )
    }

    private fun taskUpdate(
        context: RequestContext<MessageSendParams>,
        content: String,
        state: TaskState,
        final: Boolean
    ): TaskStatusUpdateEvent = TaskStatusUpdateEvent(
        taskId = context.taskId,
        contextId = context.contextId,
        status = TaskStatus(
            state = state,
            message = agentMessage(context.contextId, context.taskId, content),
            timestamp = Clock.System.now()
        ),
        final = final
    )

    private fun agentMessage(
        contextId: String,
        taskId: String,
        content: String
    ): Message = Message(
        messageId = UUID.randomUUID().toString(),
        role = Role.Agent,
        parts = listOf(TextPart(content)),
        taskId = taskId,
        contextId = contextId
    )
}
