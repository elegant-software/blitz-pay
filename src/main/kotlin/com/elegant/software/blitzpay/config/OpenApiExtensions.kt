package com.elegant.software.blitzpay.config

import io.swagger.v3.oas.models.Paths

/**
 * Rewrites /{version}/... paths to /v{version}/... in the generated OpenAPI spec
 * so Swagger UI sends concrete URLs instead of the literal {version} placeholder.
 * Also strips the redundant "version" path parameter from every operation.
 */
fun rewriteVersionPaths(originalPaths: Paths?, version: String): Paths {
    val rewritten = Paths()
    originalPaths?.forEach { (path, item) ->
        val fixedPath = path.replace(Regex("^/\\{version\\}"), "/v$version")
        item.readOperations().forEach { op ->
            op.parameters?.removeIf { it.name == "version" }
        }
        rewritten.addPathItem(fixedPath, item)
    }
    return rewritten
}
