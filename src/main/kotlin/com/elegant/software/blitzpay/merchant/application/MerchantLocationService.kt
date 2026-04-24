package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.MerchantLocationResponse
import com.elegant.software.blitzpay.merchant.api.NearbyBranchResponse
import com.elegant.software.blitzpay.merchant.api.NearbyMerchantResponse
import com.elegant.software.blitzpay.merchant.api.NearbyMerchantsResponse
import com.elegant.software.blitzpay.merchant.api.SetMerchantLocationRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantLocation
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

@Service
class MerchantLocationService(
    private val repository: MerchantApplicationRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
) {
    private val log = LoggerFactory.getLogger(MerchantLocationService::class.java)

    @Transactional
    fun setLocation(merchantId: UUID, request: SetMerchantLocationRequest): MerchantLocationResponse {
        require(request.geofenceRadiusMeters > 0) { "geofenceRadiusMeters must be positive" }
        val merchant = repository.findById(merchantId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found") }
        merchant.updateLocation(
            MerchantLocation(
                latitude = request.latitude,
                longitude = request.longitude,
                geofenceRadiusMeters = request.geofenceRadiusMeters,
                googlePlaceId = request.googlePlaceId,
                addressLine1 = request.addressLine1,
                addressLine2 = request.addressLine2,
                city = request.city,
                postalCode = request.postalCode,
                country = request.country,
                placeEnrichmentStatus = request.googlePlaceId?.let { "PENDING" }
            )
        )
        repository.save(merchant)
        log.info("Location set for merchant {}", merchantId)
        return merchant.location!!.toResponse(merchantId)
    }

    @Transactional(readOnly = true)
    fun getLocation(merchantId: UUID): MerchantLocationResponse {
        val merchant = repository.findById(merchantId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found") }
        val location = merchant.location
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No location set for merchant")
        return location.toResponse(merchantId)
    }

    @Transactional
    fun deleteLocation(merchantId: UUID) {
        val merchant = repository.findById(merchantId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found") }
        merchant.clearLocation()
        repository.save(merchant)
    }

    @Transactional(readOnly = true)
    fun findNearby(lat: Double, lng: Double, radiusMeters: Double): NearbyMerchantsResponse {
        require(radiusMeters > 0) { "radiusMeters must be positive" }
        val merchants = repository.findNearby(lat, lng, radiusMeters)
        val activeBranchesByMerchantId = merchantBranchRepository
            .findAllByMerchantApplicationIdInAndActiveTrue(merchants.map { it.id })
            .groupBy { it.merchantApplicationId }

        return NearbyMerchantsResponse(
            merchants = merchants.map { m ->
                val loc = m.location!!
                NearbyMerchantResponse(
                    merchantId = m.id,
                    legalBusinessName = m.businessProfile.legalBusinessName,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    geofenceRadiusMeters = loc.geofenceRadiusMeters,
                    googlePlaceId = loc.googlePlaceId,
                    distanceMeters = haversineMeters(lat, lng, loc.latitude, loc.longitude),
                    activeBranches = activeBranchesByMerchantId[m.id]
                        .orEmpty()
                        .map { branch ->
                            val branchLocation = branch.location
                            NearbyBranchResponse(
                                branchId = branch.id,
                                name = branch.name,
                                distanceMeters = branchLocation?.let {
                                    haversineMeters(lat, lng, it.latitude, it.longitude)
                                },
                                latitude = branchLocation?.latitude,
                                longitude = branchLocation?.longitude,
                                addressLine1 = branch.addressLine1,
                                city = branch.city,
                                postalCode = branch.postalCode,
                                country = branch.country,
                                contactFullName = branch.contactFullName,
                                contactEmail = branch.contactEmail,
                                contactPhoneNumber = branch.contactPhoneNumber,
                                activePaymentChannels = branch.activePaymentChannels.toSet(),
                            )
                        }
                        .sortedWith(compareBy(nullsLast()) { it.distanceMeters })
                )
            }
        )
    }

    private fun MerchantLocation.toResponse(merchantId: UUID) = MerchantLocationResponse(
        merchantId = merchantId,
        latitude = latitude,
        longitude = longitude,
        geofenceRadiusMeters = geofenceRadiusMeters,
        googlePlaceId = googlePlaceId,
        addressLine1 = addressLine1,
        addressLine2 = addressLine2,
        city = city,
        postalCode = postalCode,
        country = country,
        placeFormattedAddress = placeFormattedAddress,
        placeRating = placeRating,
        placeReviewCount = placeReviewCount,
        placeEnrichmentStatus = placeEnrichmentStatus,
        placeEnrichedAt = placeEnrichedAt
    )

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        return r * acos(
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2))
                * cos(Math.toRadians(lng2) - Math.toRadians(lng1))
                + sin(Math.toRadians(lat1)) * sin(Math.toRadians(lat2))
        )
    }
}
