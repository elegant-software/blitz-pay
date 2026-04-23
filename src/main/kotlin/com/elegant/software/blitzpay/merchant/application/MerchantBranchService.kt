package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.BranchResponse
import com.elegant.software.blitzpay.merchant.api.CreateBranchRequest
import com.elegant.software.blitzpay.merchant.api.UpdateBranchRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantBranch
import com.elegant.software.blitzpay.merchant.domain.MerchantLocation
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MerchantBranchService(
    private val merchantBranchRepository: MerchantBranchRepository,
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val storageService: StorageService,
) {

    fun create(merchantId: UUID, request: CreateBranchRequest): BranchResponse {
        require(request.name.isNotBlank()) { "Branch name must not be blank" }
        require((request.latitude == null) == (request.longitude == null)) {
            "latitude and longitude must both be provided or both be omitted"
        }
        request.geofenceRadiusMeters?.let { require(it > 0) { "geofenceRadiusMeters must be positive" } }
        requireMerchantExists(merchantId)
        val branch = MerchantBranch(
            merchantApplicationId = merchantId,
            name = request.name,
            addressLine1 = request.addressLine1,
            addressLine2 = request.addressLine2,
            city = request.city,
            postalCode = request.postalCode,
            country = request.country,
            contactFullName = request.contactFullName,
            contactEmail = request.contactEmail,
            contactPhoneNumber = request.contactPhoneNumber,
            activePaymentChannels = request.activePaymentChannels.toMutableSet(),
            location = request.toLocation(),
        )
        return merchantBranchRepository.save(branch).toResponse()
    }

    fun list(merchantId: UUID): List<BranchResponse> {
        requireMerchantExists(merchantId)
        return merchantBranchRepository
            .findAllByMerchantApplicationIdAndActiveTrue(merchantId)
            .map { it.toResponse() }
    }

    fun get(merchantId: UUID, branchId: UUID): BranchResponse {
        requireMerchantExists(merchantId)
        val branch = merchantBranchRepository.findByIdAndActiveTrue(branchId)
            ?: throw NoSuchElementException("Branch not found: $branchId")
        if (branch.merchantApplicationId != merchantId) {
            throw NoSuchElementException("Branch not found: $branchId")
        }
        return branch.toResponse()
    }

    fun update(merchantId: UUID, branchId: UUID, request: UpdateBranchRequest): BranchResponse {
        require(request.name.isNotBlank()) { "Branch name must not be blank" }
        require((request.latitude == null) == (request.longitude == null)) {
            "latitude and longitude must both be provided or both be omitted"
        }
        request.geofenceRadiusMeters?.let { require(it > 0) { "geofenceRadiusMeters must be positive" } }
        requireMerchantExists(merchantId)

        val branch = merchantBranchRepository.findById(branchId)
            .orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        if (branch.merchantApplicationId != merchantId) {
            throw NoSuchElementException("Branch not found: $branchId")
        }

        branch.updateDetails(
            name = request.name,
            active = request.active,
            addressLine1 = request.addressLine1,
            addressLine2 = request.addressLine2,
            city = request.city,
            postalCode = request.postalCode,
            country = request.country,
            contactFullName = request.contactFullName,
            contactEmail = request.contactEmail,
            contactPhoneNumber = request.contactPhoneNumber,
            activePaymentChannels = request.activePaymentChannels,
            location = request.toLocation(),
        )

        return merchantBranchRepository.save(branch).toResponse()
    }

    fun updateImage(merchantId: UUID, branchId: UUID, storageKey: String): BranchResponse {
        require(storageKey.isNotBlank()) { "storageKey must not be blank" }
        requireMerchantExists(merchantId)
        val branch = merchantBranchRepository.findById(branchId)
            .orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        if (branch.merchantApplicationId != merchantId) {
            throw NoSuchElementException("Branch not found: $branchId")
        }
        branch.updateImage(storageKey)
        return merchantBranchRepository.save(branch).toResponse()
    }

    fun delete(merchantId: UUID, branchId: UUID) {
        requireMerchantExists(merchantId)
        val branch = merchantBranchRepository.findById(branchId)
            .orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        if (branch.merchantApplicationId != merchantId) {
            throw NoSuchElementException("Branch not found: $branchId")
        }
        branch.deactivate()
        merchantBranchRepository.save(branch)
    }

    private fun requireMerchantExists(merchantId: UUID) {
        if (!merchantApplicationRepository.existsById(merchantId)) {
            throw NoSuchElementException("Merchant not found: $merchantId")
        }
    }

    private fun signedUrl(storageKey: String?): String? =
        storageKey?.let { runCatching { storageService.presignDownload(it) }.getOrNull() }

    private fun MerchantBranch.toResponse() = BranchResponse(
        id = id,
        merchantId = merchantApplicationId,
        name = name,
        active = active,
        addressLine1 = addressLine1,
        addressLine2 = addressLine2,
        city = city,
        postalCode = postalCode,
        country = country,
        contactFullName = contactFullName,
        contactEmail = contactEmail,
        contactPhoneNumber = contactPhoneNumber,
        activePaymentChannels = activePaymentChannels.toSet(),
        latitude = location?.latitude,
        longitude = location?.longitude,
        geofenceRadiusMeters = location?.geofenceRadiusMeters,
        googlePlaceId = location?.googlePlaceId,
        placeFormattedAddress = location?.placeFormattedAddress,
        placeRating = location?.placeRating,
        placeReviewCount = location?.placeReviewCount,
        placeEnrichmentStatus = location?.placeEnrichmentStatus,
        placeEnrichedAt = location?.placeEnrichedAt,
        imageUrl = signedUrl(imageStorageKey),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun CreateBranchRequest.toLocation(): MerchantLocation? {
        return toLocation(
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            postalCode = postalCode,
            country = country,
            latitude = latitude,
            longitude = longitude,
            geofenceRadiusMeters = geofenceRadiusMeters,
            googlePlaceId = googlePlaceId,
        )
    }

    private fun UpdateBranchRequest.toLocation(): MerchantLocation? {
        return toLocation(
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            postalCode = postalCode,
            country = country,
            latitude = latitude,
            longitude = longitude,
            geofenceRadiusMeters = geofenceRadiusMeters,
            googlePlaceId = googlePlaceId,
        )
    }

    private fun toLocation(
        addressLine1: String?,
        addressLine2: String?,
        city: String?,
        postalCode: String?,
        country: String?,
        latitude: Double?,
        longitude: Double?,
        geofenceRadiusMeters: Int?,
        googlePlaceId: String?,
    ): MerchantLocation? {
        val hasCoordinates = latitude != null && longitude != null
        if (!hasCoordinates) return null
        return MerchantLocation(
            latitude = requireNotNull(latitude),
            longitude = requireNotNull(longitude),
            geofenceRadiusMeters = geofenceRadiusMeters ?: 500,
            googlePlaceId = googlePlaceId,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            postalCode = postalCode,
            country = country,
            placeEnrichmentStatus = googlePlaceId?.let { "PENDING" }
        )
    }
}
