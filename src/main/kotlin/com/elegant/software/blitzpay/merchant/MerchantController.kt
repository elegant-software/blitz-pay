package com.elegant.software.blitzpay.merchant

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Merchants", description = "Operations related to merchant management")
@RestController
@RequestMapping("/api/merchants")
class MerchantController(private val service: MerchantService) {

    @Operation(summary = "Create a new merchant")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Merchant created successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request body")
    )
    @PostMapping
    fun create(@RequestBody merchant: Merchant): ResponseEntity<Merchant> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(merchant))

    @Operation(summary = "List all merchants")
    @ApiResponse(responseCode = "200", description = "List of merchants returned successfully")
    @GetMapping
    fun findAll(): ResponseEntity<List<Merchant>> =
        ResponseEntity.ok(service.findAll())

    @Operation(summary = "Get a merchant by ID")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Merchant found"),
        ApiResponse(responseCode = "404", description = "Merchant not found")
    )
    @GetMapping("/{id}")
    fun findById(
        @Parameter(description = "ID of the merchant to retrieve") @PathVariable id: Long
    ): ResponseEntity<Merchant> =
        service.findById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @Operation(summary = "Delete a merchant by ID")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Merchant deleted successfully"),
        ApiResponse(responseCode = "404", description = "Merchant not found")
    )
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(description = "ID of the merchant to delete") @PathVariable id: Long
    ): ResponseEntity<Void> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }
}
