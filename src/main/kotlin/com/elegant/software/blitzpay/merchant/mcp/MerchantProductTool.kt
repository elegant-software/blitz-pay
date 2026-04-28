package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.*
import com.elegant.software.blitzpay.merchant.application.MerchantBranchService
import com.elegant.software.blitzpay.merchant.application.MerchantProductService
import com.elegant.software.blitzpay.merchant.application.MerchantRegistrationService
import com.elegant.software.blitzpay.merchant.application.ProductImagePolicy
import com.elegant.software.blitzpay.merchant.application.ProductImageUpload
import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.api.MerchantBusinessProfileRequest
import com.elegant.software.blitzpay.merchant.api.MerchantPrimaryContactRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantBranch
import com.elegant.software.blitzpay.merchant.domain.MerchantLocation
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import javax.imageio.ImageIO
import java.util.*

@Component
class MerchantProductTools(
    private val merchantProductService: MerchantProductService,
    private val merchantBranchService: MerchantBranchService,
    private val merchantRegistrationService: MerchantRegistrationService,
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
    private val merchantProductRepository: MerchantProductRepository
) {

    @McpTool(
        name = "merchant_product_update",
        description = "Update a product's name, description, and price for a merchant"
    )
    fun updateProduct(
        merchantId: String,
        productId: String,
        branchId: String, // <-- branchId is now required
        name: String,
        unitPrice: String,
        description: String? = null,
        imageBase64: String? = null,
        imageFilePath: String? = null,
        imageContentType: String? = null,
        cropX: Int? = null,
        cropY: Int? = null,
        cropWidth: Int? = null,
        cropHeight: Int? = null
    ): ProductResponse {

        return merchantProductService.update(
            merchantId = UUID.fromString(merchantId),
            productId = UUID.fromString(productId),
            request = UpdateProductRequest(
                name = name,
                branchId = UUID.fromString(branchId),
                unitPrice = BigDecimal(unitPrice),
                description = description
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
        return merchantApplicationRepository.findByBusinessProfileLegalBusinessName(merchantName)?.id?.toString()
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
        val existing = merchantApplicationRepository.findByBusinessProfileLegalBusinessName(merchantName)
        if (existing != null) {
            ensureBranchExists(existing.id, defaultBranchName)
            return existing.id.toString()
        }

        val normalizedRegistrationNumber = registrationNumber?.trim()?.takeIf { it.isNotEmpty() }
            ?: "MCP-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"
        val generatedContactEmail = contactEmail?.trim()?.takeIf { it.isNotEmpty() }
            ?: "${merchantName.lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.')}.merchant@example.com"

        val created = merchantRegistrationService.register(
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
        return merchantBranchRepository.findByNameAndMerchantApplicationIdAndActiveTrue(
            branchName,
            UUID.fromString(merchantId)
        )?.id?.toString() ?: throw IllegalArgumentException("Branch not found with name: $branchName")
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
        val existing = merchantBranchRepository.findByNameAndMerchantApplicationId(branchName, mId)
        if (existing != null) {
            if (!hasBranchDetails(
                    addressLine1 = addressLine1,
                    addressLine2 = addressLine2,
                    city = city,
                    postalCode = postalCode,
                    country = country,
                    latitude = latitude,
                    longitude = longitude,
                    geofenceRadiusMeters = geofenceRadiusMeters,
                    googlePlaceId = googlePlaceId
                )
            ) {
                return existing.id.toString()
            }
            return merchantBranchRepository.save(
                existing.applyBranchDetails(
                    addressLine1 = addressLine1,
                    addressLine2 = addressLine2,
                    city = city,
                    postalCode = postalCode,
                    country = country,
                    latitude = latitude,
                    longitude = longitude,
                    geofenceRadiusMeters = geofenceRadiusMeters,
                    googlePlaceId = googlePlaceId
                )
            ).id.toString()
        }

        if (latitude == null && (geofenceRadiusMeters != null || googlePlaceId != null)) {
            throw IllegalArgumentException(
                "latitude and longitude are required when setting geofenceRadiusMeters or googlePlaceId on a new branch"
            )
        }

        return merchantBranchService.create(
            mId,
            CreateBranchRequest(
                name = branchName,
                addressLine1 = addressLine1,
                addressLine2 = addressLine2,
                city = city,
                postalCode = postalCode,
                country = country,
                latitude = latitude,
                longitude = longitude,
                geofenceRadiusMeters = geofenceRadiusMeters,
                googlePlaceId = googlePlaceId
            )
        ).id.toString()
    }

    @McpTool(
        name = "product_id_by_name",
        description = "Get product ID by product name, merchant ID, and branch ID"
    )
    fun getProductIdByName(merchantId: String, branchId: String, productName: String): String {
        return merchantProductRepository.findByNameAndMerchantApplicationIdAndMerchantBranchIdAndActiveTrue(
            productName,
            UUID.fromString(merchantId),
            UUID.fromString(branchId)
        )?.id?.toString() ?: throw IllegalArgumentException("Product not found with name: $productName")
    }

    @McpTool(
        name = "product_id_by_name_or_create",
        description = "Get or create product ID by product name, merchant ID, and branch ID"
    )
    fun getOrCreateProductId(
        merchantId: String,
        branchId: String,
        productName: String,
        unitPrice: String,
        description: String? = null,
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
        val image = productImageUploadOrNull(
            imageBase64 = imageBase64,
            imageFilePath = imageFilePath,
            imageContentType = imageContentType,
            cropX = cropX,
            cropY = cropY,
            cropWidth = cropWidth,
            cropHeight = cropHeight
        )
        val existing = merchantProductRepository.findByNameAndMerchantApplicationIdAndMerchantBranchId(
            productName, mId, bId
        )
        if (existing != null) {
            if (image != null) {
                merchantProductService.update(
                    mId,
                    existing.id,
                    UpdateProductRequest(
                        name = productName,
                        branchId = bId,
                        unitPrice = BigDecimal(unitPrice),
                        description = description ?: existing.description
                    ),
                    image
                )
            }
            return existing.id.toString()
        }

        return merchantProductService.create(
            mId,
            CreateProductRequest(
                name = productName,
                branchId = bId,
                unitPrice = BigDecimal(unitPrice),
                description = description
            ),
            image
        ).productId.toString()
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

    private fun hasBranchDetails(
        addressLine1: String?,
        addressLine2: String?,
        city: String?,
        postalCode: String?,
        country: String?,
        latitude: Double?,
        longitude: Double?,
        geofenceRadiusMeters: Int?,
        googlePlaceId: String?
    ): Boolean {
        return listOf(
            addressLine1,
            addressLine2,
            city,
            postalCode,
            country,
            latitude,
            longitude,
            geofenceRadiusMeters,
            googlePlaceId
        ).any { it != null }
    }

    private fun ensureBranchExists(merchantId: UUID, defaultBranchName: String) {
        require(defaultBranchName.isNotBlank()) { "defaultBranchName must not be blank" }
        val existingBranch = merchantBranchRepository.findByNameAndMerchantApplicationId(defaultBranchName, merchantId)
        if (existingBranch == null) {
            merchantBranchService.create(
                merchantId,
                CreateBranchRequest(name = defaultBranchName)
            )
        }
    }

    private fun MerchantBranch.applyBranchDetails(
        addressLine1: String?,
        addressLine2: String?,
        city: String?,
        postalCode: String?,
        country: String?,
        latitude: Double?,
        longitude: Double?,
        geofenceRadiusMeters: Int?,
        googlePlaceId: String?
    ): MerchantBranch {
        addressLine1?.let { this.addressLine1 = it }
        addressLine2?.let { this.addressLine2 = it }
        city?.let { this.city = it }
        postalCode?.let { this.postalCode = it }
        country?.let { this.country = it }

        val hasCoordinateUpdate = latitude != null && longitude != null
        if (!hasCoordinateUpdate && location == null && (geofenceRadiusMeters != null || googlePlaceId != null)) {
            throw IllegalArgumentException(
                "latitude and longitude are required when setting geofenceRadiusMeters or googlePlaceId on a branch without location"
            )
        }

        val currentLocation = location
        location = when {
            hasCoordinateUpdate -> MerchantLocation(
                latitude = requireNotNull(latitude),
                longitude = requireNotNull(longitude),
                geofenceRadiusMeters = geofenceRadiusMeters ?: currentLocation?.geofenceRadiusMeters ?: 500,
                googlePlaceId = googlePlaceId ?: currentLocation?.googlePlaceId,
                addressLine1 = this.addressLine1,
                addressLine2 = this.addressLine2,
                city = this.city,
                postalCode = this.postalCode,
                country = this.country,
                placeEnrichmentStatus = enrichmentStatusFor(googlePlaceId, currentLocation)
            )

            currentLocation != null -> currentLocation.copy(
                geofenceRadiusMeters = geofenceRadiusMeters ?: currentLocation.geofenceRadiusMeters,
                googlePlaceId = googlePlaceId ?: currentLocation.googlePlaceId,
                addressLine1 = this.addressLine1,
                addressLine2 = this.addressLine2,
                city = this.city,
                postalCode = this.postalCode,
                country = this.country,
                placeEnrichmentStatus = enrichmentStatusFor(googlePlaceId, currentLocation)
            )

            else -> null
        }
        updatedAt = Instant.now()
        return this
    }

    private fun enrichmentStatusFor(googlePlaceId: String?, currentLocation: MerchantLocation?): String? {
        return when {
            googlePlaceId == null -> currentLocation?.placeEnrichmentStatus
            googlePlaceId != currentLocation?.googlePlaceId -> "PENDING"
            else -> currentLocation.placeEnrichmentStatus
        }
    }
}
