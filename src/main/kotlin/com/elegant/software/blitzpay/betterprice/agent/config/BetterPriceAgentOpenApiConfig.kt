package com.elegant.software.blitzpay.betterprice.agent.config

import com.elegant.software.blitzpay.betterprice.agent.a2a.MarketSearchA2aServerBootstrap
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

@Configuration
class BetterPriceAgentOpenApiConfig(
    private val properties: MarketSearchAgentProperties
) {

    @Bean
    fun betterPriceAgentApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("Better Price Agent")
            .packagesToScan(MarketSearchA2aServerBootstrap::class.java.packageName)
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info()
                    .title("Better Price Agent API")
                    .description("Better Price Agent exposed through the dedicated A2A listener. This group documents the agent card and JSON-RPC endpoint used for `message/send`, `tasks/get`, and `tasks/cancel`.")
                openApi.externalDocs = ExternalDocumentation()
                    .description("Better Price Agent OpenAPI schema")
                    .url("/api-docs/BetterPriceAgent")

                openApi.path(
                    "/.well-known/agent-card.json",
                    PathItem().get(
                        Operation()
                            .operationId("getBetterPriceAgentCard")
                            .tags(listOf("Better Price Agent"))
                            .summary("Fetch the Better Price Agent A2A business card")
                            .description(
                                "Agent discovery endpoint served by the KOOG A2A listener. The live URL is exposed on the dedicated A2A port, for example `http://localhost:8099/.well-known/agent-card.json`."
                            )
                            .servers(listOf(a2aServer()))
                            .responses(
                                ApiResponses().addApiResponse(
                                    "200",
                                    ApiResponse().description("A2A agent card metadata for the Better Price Agent")
                                )
                            )
                    )
                )

                openApi.path(
                    "/a2a/market-search",
                    PathItem().post(
                        Operation()
                            .operationId("sendBetterPriceA2aMessage")
                            .tags(listOf("Better Price Agent"))
                            .summary("Send a KOOG A2A message to the Better Price Agent")
                            .description(
                                "JSON-RPC A2A endpoint served by the KOOG A2A transport. The live listener runs on the configured A2A port, for example `http://localhost:8099/a2a/market-search`, not on the main Spring Boot HTTP port. Supported JSON-RPC methods include `message/send`, `tasks/get`, and `tasks/cancel`."
                            )
                            .servers(listOf(a2aServer()))
                            .requestBody(
                                RequestBody().required(true).content(
                                    Content().addMediaType(
                                        "application/json",
                                        io.swagger.v3.oas.models.media.MediaType()
                                            .schema(
                                                ObjectSchema()
                                                    .addProperty("jsonrpc", Schema<String>().example("2.0"))
                                                    .addProperty("id", Schema<String>().example("req-1"))
                                                    .addProperty("method", Schema<String>().example("message/send"))
                                                    .addProperty("params", ObjectSchema())
                                            )
                                            .example(
                                                mapOf(
                                                    "jsonrpc" to "2.0",
                                                    "id" to "req-1",
                                                    "method" to "message/send",
                                                    "params" to mapOf(
                                                        "message" to mapOf(
                                                            "messageId" to "msg-1",
                                                            "role" to "user",
                                                            "parts" to listOf(
                                                                mapOf(
                                                                    "kind" to "text",
                                                                    "text" to """{"inputPrice":329.99,"currency":"USD","productTitle":"Sony WH-1000XM5","brandName":"Sony","modelName":"WH-1000XM5","sku":"SONY-WH1000XM5-BLK"}"""
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                            .addExamples(
                                                "message/send",
                                                Example()
                                                    .summary("Submit a product comparison request")
                                                    .description("Starts a Better Price Agent task using a user message payload.")
                                                    .value(
                                                        mapOf(
                                                            "jsonrpc" to "2.0",
                                                            "id" to "req-1",
                                                            "method" to "message/send",
                                                            "params" to mapOf(
                                                                "message" to mapOf(
                                                                    "messageId" to "msg-1",
                                                                    "role" to "user",
                                                                    "parts" to listOf(
                                                                        mapOf(
                                                                            "kind" to "text",
                                                                            "text" to """{"inputPrice":329.99,"currency":"USD","productTitle":"Sony WH-1000XM5","brandName":"Sony","modelName":"WH-1000XM5","sku":"SONY-WH1000XM5-BLK"}"""
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                            )
                                            .addExamples(
                                                "tasks/get",
                                                Example()
                                                    .summary("Fetch task status")
                                                    .description("Retrieves the latest state and recent history for an existing task.")
                                                    .value(
                                                        mapOf(
                                                            "jsonrpc" to "2.0",
                                                            "id" to "req-2",
                                                            "method" to "tasks/get",
                                                            "params" to mapOf(
                                                                "id" to "task-123",
                                                                "historyLength" to 10
                                                            )
                                                        )
                                                    )
                                            )
                                            .addExamples(
                                                "tasks/cancel",
                                                Example()
                                                    .summary("Cancel a running task")
                                                    .description("Requests cancellation for an existing Better Price Agent task.")
                                                    .value(
                                                        mapOf(
                                                            "jsonrpc" to "2.0",
                                                            "id" to "req-3",
                                                            "method" to "tasks/cancel",
                                                            "params" to mapOf(
                                                                "id" to "task-123"
                                                            )
                                                        )
                                                    )
                                            )
                                    )
                                )
                            )
                            .responses(
                                ApiResponses().addApiResponse(
                                    "200",
                                    ApiResponse().description("KOOG A2A JSON-RPC response containing a task or terminal result event")
                                )
                            )
                    )
                )
            }
            .build()

    private fun a2aServer(): Server {
        val baseUri = URI.create(properties.baseUrl)
        val url = URI(
            baseUri.scheme ?: "http",
            baseUri.userInfo,
            baseUri.host ?: "localhost",
            properties.a2a.port,
            null,
            null,
            null
        ).toString()

        return Server()
            .url(url)
            .description("Dedicated KOOG A2A listener")
    }
}
