package com.elegant.software.blitzpay.contract

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths

class OpenApiGeneratorTest : ContractVerifierBase() {

    @Test
    fun `generate OpenAPI specification`() {
        val response = webTestClient.get()
            .uri("/api-docs.yaml") // Springdoc provides YAML at .yaml suffix
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()

        val yamlContent = response.responseBody
        requireNotNull(yamlContent) { "OpenAPI content was null" }

        // Save to api-docs folder
        val outputDir = File("api-docs")
        if (!outputDir.exists()) outputDir.mkdirs()
        
        val outputFile = File(outputDir, "api-doc.yml")
        outputFile.writeText(yamlContent)
        
        println("OpenAPI documentation updated at: ${outputFile.absolutePath}")
    }
}
