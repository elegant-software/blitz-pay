package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductListResponse
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.application.MerchantProductService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

@Tag(name = "Merchant Products", description = "Product catalog management for merchants")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/products", version = "1")
class MerchantProductController(
    private val merchantProductService: MerchantProductService
) {

    @Operation(summary = "Create a product for the merchant")
    @PostMapping
    fun create(
        @PathVariable merchantId: UUID,
        @RequestBody request: CreateProductRequest
    ): Mono<ResponseEntity<ProductResponse>> =
        Mono.fromCallable { merchantProductService.create(merchantId, request) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }

    @Operation(summary = "List all active products for the merchant")
    @GetMapping
    fun list(@PathVariable merchantId: UUID): Mono<ResponseEntity<ProductListResponse>> =
        Mono.fromCallable { merchantProductService.list(merchantId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }

    @Operation(summary = "Get a single active product")
    @GetMapping("/{productId}")
    fun get(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID
    ): Mono<ResponseEntity<ProductResponse>> =
        Mono.fromCallable { merchantProductService.get(merchantId, productId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }

    @Operation(summary = "Update a product's name, price, and image")
    @PutMapping("/{productId}")
    fun update(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID,
        @RequestBody request: UpdateProductRequest
    ): Mono<ResponseEntity<ProductResponse>> =
        Mono.fromCallable { merchantProductService.update(merchantId, productId, request) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }

    @Operation(summary = "Soft-delete a product (sets active = false)")
    @DeleteMapping("/{productId}")
    fun deactivate(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID
    ): Mono<ResponseEntity<Void>> =
        Mono.fromCallable { merchantProductService.deactivate(merchantId, productId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.noContent().build<Void>() }
}
