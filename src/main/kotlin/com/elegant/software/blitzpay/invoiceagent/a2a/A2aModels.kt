package com.elegant.software.blitzpay.invoiceagent.a2a

data class A2aMessageSendRequest(
    val id: String,
    val method: String,
    val params: A2aMessageSendParams
)

data class A2aMessageSendParams(
    val message: A2aMessage
)

data class A2aMessage(
    val role: String,
    val parts: List<A2aPart>,
    val messageId: String? = null,
    val contextId: String? = null,
    val taskId: String? = null
)

data class A2aPart(
    val type: String,
    val text: String
)

data class A2aResponse(
    val id: String,
    val result: A2aResult
)

data class A2aResult(
    val message: A2aMessage,
    val status: String
)
