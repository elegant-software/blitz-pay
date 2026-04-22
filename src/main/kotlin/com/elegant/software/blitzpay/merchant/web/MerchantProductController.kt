package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductListResponse
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.application.MerchantProductService
import com.elegant.software.blitzpay.merchant.application.ProductImageUpload
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

data class ProductErrorResponse(val error: String)

@Tag(name = "Merchant Products", description = "Product catalog management for merchants")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/products", version = "1")
class MerchantProductController(
    private val merchantProductService: MerchantProductService
) {

    @Operation(summary = "Create a product for the merchant")
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun create(
        @PathVariable merchantId: UUID,
        @RequestPart("name") name: String,
        @RequestPart("branchId") branchId: String,
        @RequestPart("unitPrice") unitPrice: String,
        @RequestPart("description", required = false) description: String?,
        @RequestPart("image", required = false) image: FilePart?
    ): Mono<ResponseEntity<ProductResponse>> =
        image.toProductImageUpload()
            .flatMap { upload ->
                Mono.fromCallable {
                    merchantProductService.create(
                        merchantId,
                        CreateProductRequest(
                            name = name,
                            branchId = UUID.fromString(branchId),
                            unitPrice = BigDecimal(unitPrice),
                            description = description
                        ),
                        upload.orElse(null)
                    )
                }.subscribeOn(Schedulers.boundedElastic())
            }
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }

    @Operation(summary = "List all active products for the merchant")
    @GetMapping
    fun list(@PathVariable merchantId: UUID, @RequestParam("branchId") branchId: UUID): Mono<ResponseEntity<ProductListResponse>> =
        Mono.fromCallable { merchantProductService.list(merchantId, branchId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }

    @Operation(summary = "Get a single active product")
    @GetMapping("/{productId}")
    fun get(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID,
        @RequestParam("branchId") branchId: UUID
    ): Mono<ResponseEntity<ProductResponse>> =
        Mono.fromCallable { merchantProductService.get(merchantId, productId, branchId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }

    @Operation(summary = "Update a product's name, price, and image")
    @PutMapping("/{productId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun update(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID,
        @RequestPart("name") name: String,
        @RequestPart("branchId") branchId: String,
        @RequestPart("unitPrice") unitPrice: String,
        @RequestPart("description", required = false) description: String?,
        @RequestPart("image", required = false) image: FilePart?
    ): Mono<ResponseEntity<ProductResponse>> =
        image.toProductImageUpload()
            .flatMap { upload ->
                Mono.fromCallable {
                    merchantProductService.update(
                        merchantId,
                        productId,
                        UpdateProductRequest(
                            name = name,
                            branchId = UUID.fromString(branchId),
                            unitPrice = BigDecimal(unitPrice),
                            description = description
                        ),
                        upload.orElse(null)
                    )
                }.subscribeOn(Schedulers.boundedElastic())
            }
            .map { ResponseEntity.ok(it) }

    @Operation(summary = "Soft-delete a product (sets active = false)")
    @DeleteMapping("/{productId}")
    fun deactivate(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID,
        @RequestParam("branchId") branchId: UUID
    ): Mono<ResponseEntity<Void>> =
        Mono.fromCallable { merchantProductService.deactivate(merchantId, productId, branchId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.noContent().build<Void>() }

    private fun FilePart?.toProductImageUpload(): Mono<Optional<ProductImageUpload>> {
        if (this == null) return Mono.just(Optional.empty())
        val contentType = headers().contentType?.toString()
            ?: throw IllegalArgumentException("Product image content type is required")
        return DataBufferUtils.join(content())
            .map { buffer ->
                try {
                    val bytes = ByteArray(buffer.readableByteCount())
                    buffer.read(bytes)
                    Optional.of(ProductImageUpload(contentType = contentType, bytes = bytes))
                } finally {
                    DataBufferUtils.release(buffer)
                }
            }
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(ex: IllegalArgumentException): ResponseEntity<ProductErrorResponse> =
        ResponseEntity.badRequest().body(ProductErrorResponse(ex.message ?: "Invalid product request"))

    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(ex: NoSuchElementException): ResponseEntity<ProductErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProductErrorResponse(ex.message ?: "Product not found"))

    @ExceptionHandler(IllegalStateException::class)
    fun storageUnavailable(ex: IllegalStateException): ResponseEntity<ProductErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ProductErrorResponse(ex.message ?: "Product storage unavailable"))
}
