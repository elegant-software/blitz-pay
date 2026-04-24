package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.MerchantLocationResponse
import com.elegant.software.blitzpay.merchant.api.NearbyMerchantsResponse
import com.elegant.software.blitzpay.merchant.api.SetMerchantLocationRequest
import com.elegant.software.blitzpay.merchant.application.MerchantLocationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

@Tag(name = "Merchant Location", description = "Geolocation and geofencing for merchants")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants", version = "1")
class MerchantLocationController(
    private val merchantLocationService: MerchantLocationService
) {
    private val log = LoggerFactory.getLogger(MerchantLocationController::class.java)

    @Operation(
        summary = "Set or update a merchant's location and geofence radius",
        description = "Idempotent — replaces any existing location. " +
            "geofenceRadiusMeters must be > 0. googlePlaceId is optional and used to correlate with Google Maps."
    )
    @PutMapping("/{merchantId}/location")
    fun setLocation(
        @PathVariable merchantId: UUID,
        @RequestBody request: SetMerchantLocationRequest
    ): Mono<ResponseEntity<MerchantLocationResponse>> =
        Mono.fromCallable { merchantLocationService.setLocation(merchantId, request) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }

    @Operation(
        summary = "Get a merchant's current location",
        description = "Returns 404 if the merchant has no location set."
    )
    @GetMapping("/{merchantId}/location")
    fun getLocation(
        @PathVariable merchantId: UUID
    ): Mono<ResponseEntity<MerchantLocationResponse>> =
        Mono.fromCallable { merchantLocationService.getLocation(merchantId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }

    @Operation(
        summary = "Remove a merchant's location",
        description = "Clears latitude, longitude, geofence radius, and googlePlaceId. Returns 204 on success."
    )
    @DeleteMapping("/{merchantId}/location")
    fun deleteLocation(
        @PathVariable merchantId: UUID
    ): Mono<ResponseEntity<Void>> =
        Mono.fromCallable { merchantLocationService.deleteLocation(merchantId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.noContent().build<Void>() }

    @Operation(
        summary = "Find merchants near a position",
        description = "Returns merchants whose store location falls within radiusMeters of (lat, lng). " +
            "Intended for mobile geofence-enter events. Default search radius is 500 m. " +
            "Results are ordered by ascending distance. Only merchants with a location set are returned."
    )
    @GetMapping("/nearby")
    fun findNearby(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "500") radiusMeters: Double
    ): Mono<ResponseEntity<NearbyMerchantsResponse>> =
        Mono.fromCallable {
            log.info("Nearby merchants requested: lat={} lng={} radiusMeters={}", lat, lng, radiusMeters)
            merchantLocationService.findNearby(lat, lng, radiusMeters)
        }
            .subscribeOn(Schedulers.boundedElastic())
            .map {
                log.info("Nearby merchants response: merchantCount={}", it.merchants.size)
                ResponseEntity.ok(it)
            }
}
