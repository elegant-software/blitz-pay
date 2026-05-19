package com.elegant.software.blitzpay.betterprice.agent.a2a

import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import com.elegant.software.blitzpay.betterprice.agent.config.MarketSearchAgentProperties
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import io.ktor.server.netty.Netty

@Component
@ConditionalOnProperty(prefix = "market-search.koog", name = ["enabled"], havingValue = "true")
class MarketSearchA2aServerBootstrap(
    private val properties: MarketSearchAgentProperties,
    private val cardFactory: MarketSearchAgentCardFactory,
    private val agentExecutor: MarketSearchA2aAgentExecutor
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val agentCard by lazy { cardFactory.create() }
    private val server by lazy { A2AServer(agentExecutor = agentExecutor, agentCard = agentCard) }
    private val transport by lazy { HttpJSONRPCServerTransport(server) }

    @PostConstruct
    fun start() = runBlocking {
        transport.start(
            engineFactory = Netty,
            port = properties.a2a.port,
            path = properties.a2a.path,
            wait = false,
            agentCard = agentCard
        )

        log.info(
            "KOOG A2A server listening at {}",
            agentCard.url
        )
    }

    @PreDestroy
    fun stop() = runBlocking {
        runCatching { transport.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000) }
            .onFailure { error ->
                log.warn("Failed to stop KOOG A2A transport cleanly: {}", error.message)
            }
        runCatching { server.cancel() }
            .onFailure { error ->
                log.warn("Failed to cancel KOOG A2A server cleanly: {}", error.message)
            }
    }
}
