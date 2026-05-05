package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.CatalogProduct
import com.elegant.software.blitzpay.merchant.api.MerchantProductCatalogGateway
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.repository.ProximityEventRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MerchantProductCatalogService(
    private val productRepository: MerchantProductRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
    private val proximityEventRepository: ProximityEventRepository,
    private val storageService: StorageService,
) : MerchantProductCatalogGateway {
    private val log = LoggerFactory.getLogger(MerchantProductCatalogService::class.java)

    override fun findActiveProducts(merchantId: UUID, branchId: UUID): List<CatalogProduct> {
        if (!merchantBranchRepository.existsByMerchantApplicationIdAndIdAndActiveTrue(merchantId, branchId)) {
            log.warn(
                "merchant product catalog branch not found or inactive merchantId={} branchId={}",
                merchantId,
                branchId,
            )
            return emptyList()
        }

        val catalog = productRepository.findAllByActiveTrueAndMerchantBranchId(branchId)
            .asSequence()
            .filter { it.merchantApplicationId == merchantId }
            .map { product ->
                CatalogProduct(
                    productId = product.id,
                    branchId = branchId,
                    name = product.name,
                    description = product.description,
                    unitPrice = product.unitPrice,
                    imageUrl = product.imageStorageKey?.let(storageService::presignDownload),
                )
            }
            .toList()

        log.info(
            "merchant product catalog resolved merchantId={} branchId={} productCount={} products={}",
            merchantId,
            branchId,
            catalog.size,
            catalog.take(5).map { "${it.productId}:${it.name.take(32)}" }
        )
        return catalog
    }

    override fun findActiveProductsBySubject(subject: String): List<CatalogProduct> {
        val latestEvent = proximityEventRepository.findTopByUserSubjectOrderByReceivedAtDesc(subject)
            ?: run {
                log.info("merchant product catalog no proximity context found for subject={}", subject)
                return emptyList()
            }

        if (latestEvent.eventType != "ENTER") {
            log.info(
                "merchant product catalog latest proximity event is not ENTER subject={} eventType={} receivedAt={}",
                subject,
                latestEvent.eventType,
                latestEvent.receivedAt,
            )
            return emptyList()
        }

        if (latestEvent.receivedAt.isBefore(Instant.now().minus(CONTEXT_TTL))) {
            log.info(
                "merchant product catalog proximity context expired subject={} receivedAt={} ttlMinutes={}",
                subject,
                latestEvent.receivedAt,
                CONTEXT_TTL.toMinutes(),
            )
            return emptyList()
        }

        val catalog = when (latestEvent.regionType) {
            "BRANCH" -> {
                val branch = merchantBranchRepository.findByIdAndActiveTrue(latestEvent.sourceId)
                    ?: return emptyList()
                findActiveProducts(branch.merchantApplicationId, branch.id)
            }
            "MERCHANT" -> findActiveProductsForMerchant(latestEvent.sourceId)
            else -> emptyList()
        }

        log.info(
            "merchant product catalog resolved from subject proximity subject={} regionType={} sourceId={} productCount={}",
            subject,
            latestEvent.regionType,
            latestEvent.sourceId,
            catalog.size,
        )
        return catalog
    }

    override fun searchActiveProducts(searchText: String, limit: Int): List<CatalogProduct> {
        val terms = tokenizeSearchText(searchText)
        if (terms.isEmpty()) {
            log.info("merchant product catalog global search skipped due to empty search terms")
            return emptyList()
        }

        val perTermLimit = maxOf(5, minOf(limit, 10))
        val results = linkedMapOf<UUID, CatalogProduct>()
        terms.forEach { term ->
            productRepository.searchActiveProductsByTerm(term, PageRequest.of(0, perTermLimit))
                .mapNotNull(::toCatalogProduct)
                .forEach { product ->
                    if (results.size < limit) {
                        results.putIfAbsent(product.productId, product)
                    }
                }
        }

        val catalog = results.values.toList()
        log.info(
            "merchant product catalog global search terms={} limit={} productCount={} products={}",
            terms,
            limit,
            catalog.size,
            catalog.take(5).map { "${it.productId}:${it.name.take(32)}" }
        )
        return catalog
    }

    private fun findActiveProductsForMerchant(merchantId: UUID): List<CatalogProduct> {
        val activeBranchIds = merchantBranchRepository.findAllByMerchantApplicationIdAndActiveTrue(merchantId)
            .map { it.id }
            .toSet()

        val catalog = productRepository.findAllByActiveTrueAndMerchantApplicationId(merchantId)
            .asSequence()
            .filter { it.merchantBranchId in activeBranchIds }
            .mapNotNull(::toCatalogProduct)
            .toList()

        log.info(
            "merchant product catalog resolved merchant-wide merchantId={} activeBranchCount={} productCount={}",
            merchantId,
            activeBranchIds.size,
            catalog.size,
        )
        return catalog
    }

    private fun toCatalogProduct(product: MerchantProduct): CatalogProduct? {
        val branchId = product.merchantBranchId ?: return null
        return CatalogProduct(
            productId = product.id,
            branchId = branchId,
            name = product.name,
            description = product.description,
            unitPrice = product.unitPrice,
            imageUrl = product.imageStorageKey?.let(storageService::presignDownload),
        )
    }

    private fun tokenizeSearchText(searchText: String): List<String> =
        searchText.lowercase()
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .split(' ')
            .asSequence()
            .map(String::trim)
            .filter { it.length >= 3 }
            .distinct()
            .take(5)
            .toList()

    private companion object {
        val CONTEXT_TTL: Duration = Duration.ofHours(4)
    }
}
