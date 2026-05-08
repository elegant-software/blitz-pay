package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.*
import com.elegant.software.blitzpay.merchant.application.MerchantBranchService
import com.elegant.software.blitzpay.merchant.application.MerchantProductCategoryService
import com.elegant.software.blitzpay.merchant.application.MerchantProductService
import com.elegant.software.blitzpay.merchant.application.MerchantRegistrationService
import com.elegant.software.blitzpay.merchant.application.ProductImagePolicy
import com.elegant.software.blitzpay.merchant.application.ProductImageUpload
import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.api.MerchantBusinessProfileRequest
import com.elegant.software.blitzpay.merchant.api.MerchantPrimaryContactRequest
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import java.util.*

@Component
class MerchantProductTools(
    private val merchantProductService: MerchantProductService,
    private val merchantBranchService: MerchantBranchService,
    private val merchantRegistrationService: MerchantRegistrationService,
    private val merchantProductCategoryService: MerchantProductCategoryService
) {

    @McpTool(
        name = "merchant_api_guide",
        description = "Call this before starting any merchant, category, or product workflow. " +
            "Returns tool-selection rules and the recommended operation order."
    )
    fun merchantApiGuide(): String = """
        TOOL SELECTION
        ══════════════
        Products
          1 product  → product_id_by_name_or_create
          2+ products → products_bulk_upsert  (never loop the single-product tool)

        Categories
          1 category  → category_id_by_name_or_create
          2+ categories → categories_bulk_create  (never loop the single-category tool)

        RECOMMENDED WORKFLOW ORDER
        ══════════════════════════
        1. merchant_id_by_name_or_create   — resolve or create the merchant
        2. branch_id_by_name_or_create     — resolve or create the branch
        3. categories_bulk_create          — (optional) pre-create all categories in one call
        4. products_bulk_upsert            — create all products, referencing categoryName

        IDEMPOTENCY & RESPONSES
        ═══════════════════════
        • productCode is the idempotency key — re-running the same payload is safe.
        • Bulk responses include product IDs in all three buckets (created / skipped / failed)
          so no follow-up lookup is needed.
        • categoryName is resolved or created automatically; no separate category lookup required.
    """.trimIndent()

    @McpTool(
        name = "merchant_product_update",
        description = "Update a product's name, description, price, and category for a merchant. " +
            "Supply categoryName to have the server resolve or create the category automatically, or supply categoryId directly."
    )
    fun updateProduct(
        merchantId: String,
        productId: String,
        branchId: String,
        name: String,
        unitPrice: String,
        description: String? = null,
        categoryId: String? = null,
        categoryName: String? = null,
        productCode: String? = null,
        imageBase64: String? = null,
        imageFilePath: String? = null,
        imageContentType: String? = null,
        cropX: Int? = null,
        cropY: Int? = null,
        cropWidth: Int? = null,
        cropHeight: Int? = null
    ): ProductResponse {
        val mId = UUID.fromString(merchantId)
        val pId = UUID.fromString(productId)
        return merchantProductService.updateIncludingInactive(
            merchantId = mId,
            productId = pId,
            request = UpdateProductRequest(
                name = name,
                branchId = UUID.fromString(branchId),
                unitPrice = BigDecimal(unitPrice),
                description = description,
                categoryId = resolveOrCreateCategory(mId, categoryId, categoryName),
                productCode = productCode?.trim()?.takeIf { it.isNotEmpty() }?.toLong()
            ),
            image = productImageUploadOrNull(
                imageBase64 = imageBase64,
                imageFilePath = imageFilePath,
                imageContentType = imageContentType,
                cropX = cropX,
                cropY = cropY,
                cropWidth = cropWidth,
                cropHeight = cropHeight
            )
        )
    }

    // --- MCP helper tools for ID lookup/creation by name ---

    @McpTool(
        name = "merchant_id_by_name",
        description = "Get merchant ID by merchant name"
    )
    fun getMerchantIdByName(merchantName: String): String {
        return merchantRegistrationService.findByName(merchantName)?.id?.toString()
            ?: throw IllegalArgumentException("Merchant not found with name: $merchantName")
    }

    @McpTool(
        name = "merchant_id_by_name_or_create",
        description = "Get or create a merchant ID by merchant name and ensure the merchant has a default branch"
    )
    fun getOrCreateMerchantId(
        merchantName: String,
        registrationNumber: String? = null,
        businessType: String = "RETAIL",
        operatingCountry: String = "US",
        primaryBusinessAddress: String = "Unknown",
        contactFullName: String = "Merchant Owner",
        contactEmail: String? = null,
        contactPhoneNumber: String = "0000000000",
        defaultBranchName: String = "Main Branch"
    ): String {
        val existing = merchantRegistrationService.findByName(merchantName)
        if (existing != null) {
            ensureBranchExists(existing.id, defaultBranchName)
            return existing.id.toString()
        }

        val normalizedRegistrationNumber = registrationNumber?.trim()?.takeIf { it.isNotEmpty() }
            ?: "MCP-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"
        val generatedContactEmail = contactEmail?.trim()?.takeIf { it.isNotEmpty() }
            ?: "${merchantName.lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.')}.merchant@example.com"

        val created = merchantRegistrationService.registerDraft(
            RegisterMerchantRequest(
                businessProfile = MerchantBusinessProfileRequest(
                    legalBusinessName = merchantName,
                    businessType = businessType,
                    registrationNumber = normalizedRegistrationNumber,
                    operatingCountry = operatingCountry,
                    primaryBusinessAddress = primaryBusinessAddress
                ),
                primaryContact = MerchantPrimaryContactRequest(
                    fullName = contactFullName,
                    email = generatedContactEmail,
                    phoneNumber = contactPhoneNumber
                )
            )
        )
        ensureBranchExists(created.id, defaultBranchName)
        return created.id.toString()
    }

    @McpTool(
        name = "branch_id_by_name",
        description = "Get branch ID by branch name and merchant ID"
    )
    fun getBranchIdByName(merchantId: String, branchName: String): String {
        return merchantBranchService.findByNameIncludingInactive(UUID.fromString(merchantId), branchName)?.id?.toString()
            ?: throw IllegalArgumentException("Branch not found with name: $branchName")
    }

    @McpTool(
        name = "branch_id_by_name_or_create",
        description = "Get or create branch ID by branch name and merchant ID. Optional address, latitude, longitude, geofenceRadiusMeters, and googlePlaceId update the branch when provided."
    )
    fun getOrCreateBranchId(
        merchantId: String,
        branchName: String,
        addressLine1: String? = null,
        addressLine2: String? = null,
        city: String? = null,
        postalCode: String? = null,
        country: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        geofenceRadiusMeters: Int? = null,
        googlePlaceId: String? = null
    ): String {
        require((latitude == null) == (longitude == null)) {
            "latitude and longitude must both be provided or both be omitted"
        }
        geofenceRadiusMeters?.let { require(it > 0) { "geofenceRadiusMeters must be positive" } }

        val mId = UUID.fromString(merchantId)
        return merchantBranchService.upsertByName(
            mId,
            branchName = branchName,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            postalCode = postalCode,
            country = country,
            latitude = latitude,
            longitude = longitude,
            geofenceRadiusMeters = geofenceRadiusMeters,
            googlePlaceId = googlePlaceId
        ).id.toString()
    }

    @McpTool(
        name = "category_id_by_name",
        description = "Get product category ID by name for a given merchant"
    )
    fun getCategoryIdByName(merchantId: String, categoryName: String): String {
        return merchantProductCategoryService.findByName(UUID.fromString(merchantId), categoryName)?.id?.toString()
            ?: throw IllegalArgumentException("Category not found: $categoryName")
    }

    @McpTool(
        name = "category_id_by_name_or_create",
        description = "Get or create a SINGLE product category by name. " +
            "WARNING: if you need to create 2 or more categories, call categories_bulk_create instead — do NOT call this tool in a loop."
    )
    fun getOrCreateCategoryId(merchantId: String, categoryName: String): String {
        val mId = UUID.fromString(merchantId)
        return merchantProductCategoryService.findByName(mId, categoryName)?.id?.toString()
            ?: merchantProductCategoryService.create(
                mId,
                CreateProductCategoryRequest(name = categoryName)
            ).id.toString()
    }

    @McpTool(
        name = "merchant_list_product_categories",
        description = "List all product categories for a merchant"
    )
    fun listProductCategories(merchantId: String): List<ProductCategoryResponse> =
        merchantProductCategoryService.list(UUID.fromString(merchantId))

    @McpTool(
        name = "product_id_by_name",
        description = "Get product ID by product name, merchant ID, and branch ID"
    )
    fun getProductIdByName(merchantId: String, branchId: String, productName: String): String {
        return merchantProductService.findByNameIncludingInactive(
            UUID.fromString(merchantId),
            UUID.fromString(branchId),
            productName
        )?.productId?.toString() ?: throw IllegalArgumentException("Product not found with name: $productName")
    }

    @McpTool(
        name = "product_id_by_name_or_create",
        description = "Get or create a SINGLE product by name. " +
            "Supply categoryName to have the server resolve or create the category automatically, or supply categoryId directly. " +
            "WARNING: if you need to create or update 2 or more products, call products_bulk_upsert instead — do NOT call this tool in a loop."
    )
    fun getOrCreateProductId(
        merchantId: String,
        branchId: String,
        productName: String,
        unitPrice: String,
        description: String? = null,
        categoryId: String? = null,
        categoryName: String? = null,
        productCode: String? = null,
        imageBase64: String? = null,
        imageFilePath: String? = null,
        imageContentType: String? = null,
        cropX: Int? = null,
        cropY: Int? = null,
        cropWidth: Int? = null,
        cropHeight: Int? = null
    ): String {
        val mId = UUID.fromString(merchantId)
        val bId = UUID.fromString(branchId)
        val resolvedCategoryId = resolveOrCreateCategory(mId, categoryId, categoryName)
        val parsedCode = productCode?.trim()?.takeIf { it.isNotEmpty() }?.toLong()
        val image = productImageUploadOrNull(
            imageBase64 = imageBase64,
            imageFilePath = imageFilePath,
            imageContentType = imageContentType,
            cropX = cropX,
            cropY = cropY,
            cropWidth = cropWidth,
            cropHeight = cropHeight
        )
        val existing = merchantProductService.findByNameIncludingInactive(mId, bId, productName)
        if (existing != null) {
            if (image != null) {
                merchantProductService.update(
                    mId,
                    existing.productId,
                    UpdateProductRequest(
                        name = productName,
                        branchId = bId,
                        unitPrice = BigDecimal(unitPrice),
                        description = description ?: existing.description,
                        categoryId = resolvedCategoryId,
                        productCode = parsedCode
                    ),
                    image
                )
            }
            return existing.productId.toString()
        }

        return merchantProductService.create(
            mId,
            CreateProductRequest(
                name = productName,
                branchId = bId,
                unitPrice = BigDecimal(unitPrice),
                description = description,
                categoryId = resolvedCategoryId,
                productCode = parsedCode
            ),
            image,
            active = false
        ).productId.toString()
    }

    @McpTool(
        name = "products_bulk_upsert",
        description = "PREFERRED tool when working with 2 or more products — use this instead of calling product_id_by_name_or_create in a loop. " +
            "Creates new products and updates existing ones in a single call. " +
            "Lookup key: productCode when provided, otherwise product name. " +
            "Returns created, updated, skipped (within-batch duplicates only), and failed items with their IDs. " +
            "Accepts categoryName per item (resolved or created automatically) or categoryId. " +
            "Re-running the same payload is safe — existing products are updated, not duplicated. " +
            "Max 200 items per call. Images are not supported in bulk; use merchant_product_update after import."
    )
    fun bulkUpsertProducts(
        merchantId: String,
        branchId: String,
        products: List<BulkProductInput>
    ): BulkProductUpsertResult {
        require(products.size <= 200) { "Batch size must not exceed 200 items" }
        val mId = UUID.fromString(merchantId)
        val bId = UUID.fromString(branchId)
        val created = mutableListOf<ProductResponse>()
        val updated = mutableListOf<ProductResponse>()
        val skipped = mutableListOf<BulkSkippedItem>()
        val failed = mutableListOf<BulkFailedItem>()
        val seenNames = LinkedHashSet<String>()
        val seenCodes = LinkedHashSet<Long>()

        for (input in products) {
            val normalizedName = input.productName.trim()
            val parsedCode = input.productCode?.trim()?.takeIf { it.isNotEmpty() }?.toLong()

            if (!seenNames.add(normalizedName.lowercase())) {
                skipped.add(BulkSkippedItem(name = normalizedName, reason = "duplicate within batch"))
                continue
            }
            if (parsedCode != null && !seenCodes.add(parsedCode)) {
                skipped.add(BulkSkippedItem(name = normalizedName, reason = "duplicate productCode within batch"))
                continue
            }

            val resolvedCategoryId: UUID? = try {
                resolveOrCreateCategory(mId, input.categoryId, input.categoryName)
            } catch (ex: Exception) {
                failed.add(BulkFailedItem(name = normalizedName, reason = "Category error: ${ex.message ?: "Unknown error"}"))
                continue
            }

            val existing = parsedCode?.let { merchantProductService.findByProductCode(mId, bId, it) }
                ?: merchantProductService.findByNameIncludingInactive(mId, bId, normalizedName)

            if (existing != null) {
                runCatching {
                    merchantProductService.updateIncludingInactive(
                        mId,
                        existing.productId,
                        UpdateProductRequest(
                            name = normalizedName,
                            branchId = bId,
                            unitPrice = BigDecimal(input.unitPrice),
                            description = input.description,
                            categoryId = resolvedCategoryId,
                            productCode = parsedCode
                        )
                    )
                }.onSuccess { response ->
                    updated.add(response)
                }.onFailure { ex ->
                    failed.add(BulkFailedItem(name = normalizedName, reason = ex.message ?: "Unknown error"))
                }
            } else {
                runCatching {
                    merchantProductService.create(
                        mId,
                        CreateProductRequest(
                            name = normalizedName,
                            branchId = bId,
                            unitPrice = BigDecimal(input.unitPrice),
                            description = input.description,
                            categoryId = resolvedCategoryId,
                            productCode = parsedCode
                        ),
                        null,
                        active = false
                    )
                }.onSuccess { response ->
                    created.add(response)
                }.onFailure { ex ->
                    failed.add(BulkFailedItem(name = normalizedName, reason = ex.message ?: "Unknown error"))
                }
            }
        }
        return BulkProductUpsertResult(created = created, updated = updated, skipped = skipped, failed = failed)
    }

    @McpTool(
        name = "categories_bulk_create",
        description = "PREFERRED tool when creating 2 or more categories — use this instead of calling category_id_by_name_or_create in a loop. " +
            "Creates multiple product categories for a merchant in a single call. " +
            "Returns created, skipped (already-existing or within-batch duplicates), and failed items with their IDs. " +
            "Max 200 items per call."
    )
    fun bulkCreateCategories(
        merchantId: String,
        categories: List<BulkCategoryInput>
    ): BulkCategoryCreateResult {
        require(categories.size <= 200) { "Batch size must not exceed 200 items" }
        val mId = UUID.fromString(merchantId)
        val created = mutableListOf<ProductCategoryResponse>()
        val skipped = mutableListOf<BulkSkippedItem>()
        val failed = mutableListOf<BulkFailedItem>()
        val seenNames = LinkedHashSet<String>()

        for (input in categories) {
            val normalizedName = input.categoryName.trim()
            if (!seenNames.add(normalizedName.lowercase())) {
                skipped.add(BulkSkippedItem(name = normalizedName, reason = "duplicate within batch"))
                continue
            }
            val existing = merchantProductCategoryService.findByName(mId, normalizedName)
            if (existing != null) {
                skipped.add(BulkSkippedItem(name = normalizedName, reason = "already exists", existingId = existing.id.toString()))
                continue
            }
            runCatching {
                merchantProductCategoryService.create(mId, CreateProductCategoryRequest(name = normalizedName))
            }.onSuccess { response ->
                created.add(response)
            }.onFailure { ex ->
                failed.add(BulkFailedItem(name = normalizedName, reason = ex.message ?: "Unknown error"))
            }
        }
        return BulkCategoryCreateResult(created = created, skipped = skipped, failed = failed)
    }

    private fun resolveOrCreateCategory(merchantId: UUID, categoryId: String?, categoryName: String?): UUID? {
        val trimmedId = categoryId?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedName = categoryName?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            trimmedId != null -> UUID.fromString(trimmedId)
            trimmedName != null -> merchantProductCategoryService.findByName(merchantId, trimmedName)?.id
                ?: merchantProductCategoryService.create(merchantId, CreateProductCategoryRequest(name = trimmedName)).id
            else -> null
        }
    }

    private fun productImageUploadOrNull(
        imageBase64: String?,
        imageFilePath: String?,
        imageContentType: String?,
        cropX: Int?,
        cropY: Int?,
        cropWidth: Int?,
        cropHeight: Int?
    ): ProductImageUpload? {
        val normalizedBase64 = imageBase64?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedFilePath = imageFilePath?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedContentType = imageContentType?.trim()?.takeIf { it.isNotEmpty() }

        require(normalizedBase64 == null || normalizedFilePath == null) {
            "Provide either imageBase64 or imageFilePath, not both"
        }
        if (normalizedBase64 == null && normalizedFilePath == null) {
            require(listOf(normalizedContentType, cropX, cropY, cropWidth, cropHeight).all { it == null }) {
                "imageBase64 or imageFilePath is required when imageContentType or crop parameters are provided"
            }
            return null
        }

        val imagePath = normalizedFilePath?.let { Path.of(it).normalize() }
        val contentType = normalizedContentType
            ?: imagePath?.let { Files.probeContentType(it) }
            ?: throw IllegalArgumentException("imageContentType is required when it cannot be inferred from imageFilePath")
        ProductImagePolicy.extensionFor(contentType)
        val originalBytes = when {
            normalizedBase64 != null -> Base64.getDecoder().decode(normalizedBase64.substringAfter("base64,", normalizedBase64))
            imagePath != null -> Files.readAllBytes(imagePath)
            else -> error("No product image source provided")
        }
        val imageBytes = cropImageIfRequested(
            bytes = originalBytes,
            contentType = contentType,
            cropX = cropX,
            cropY = cropY,
            cropWidth = cropWidth,
            cropHeight = cropHeight
        )
        return ProductImageUpload(contentType = contentType, bytes = imageBytes)
    }

    private fun cropImageIfRequested(
        bytes: ByteArray,
        contentType: String,
        cropX: Int?,
        cropY: Int?,
        cropWidth: Int?,
        cropHeight: Int?
    ): ByteArray {
        val cropValues = listOf(cropX, cropY, cropWidth, cropHeight)
        if (cropValues.all { it == null }) return bytes
        require(cropValues.all { it != null }) {
            "cropX, cropY, cropWidth, and cropHeight must all be provided to crop an image"
        }
        require(cropWidth!! > 0 && cropHeight!! > 0) { "cropWidth and cropHeight must be positive" }
        require(contentType.lowercase() != "image/webp") { "Server-side crop is supported for JPEG and PNG images only" }

        val source = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("Unable to decode product image")
        require(cropX!! >= 0 && cropY!! >= 0) { "cropX and cropY must be >= 0" }
        require(cropX + cropWidth <= source.width && cropY + cropHeight <= source.height) {
            "Crop rectangle exceeds image bounds ${source.width}x${source.height}"
        }

        val cropped = source.getSubimage(cropX, cropY, cropWidth, cropHeight)
        val normalized = normalizeForContentType(cropped, contentType)
        val output = ByteArrayOutputStream()
        val format = ProductImagePolicy.extensionFor(contentType).let { if (it == "jpg") "jpeg" else it }
        require(ImageIO.write(normalized, format, output)) {
            "Unable to encode cropped product image as $contentType"
        }
        return output.toByteArray()
    }

    private fun normalizeForContentType(image: BufferedImage, contentType: String): BufferedImage {
        if (contentType.lowercase() != "image/jpeg") return image
        val normalized = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val graphics = normalized.createGraphics()
        try {
            graphics.drawImage(image, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        return normalized
    }

    private fun ensureBranchExists(merchantId: UUID, defaultBranchName: String) {
        require(defaultBranchName.isNotBlank()) { "defaultBranchName must not be blank" }
        val existingBranch = merchantBranchService.findByNameIncludingInactive(merchantId, defaultBranchName)
        if (existingBranch == null) {
            merchantBranchService.create(
                merchantId,
                CreateBranchRequest(name = defaultBranchName),
                active = false
            )
        }
    }
}
