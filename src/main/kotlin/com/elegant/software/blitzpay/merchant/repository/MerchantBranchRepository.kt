package com.elegant.software.blitzpay.merchant.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantBranch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MerchantBranchRepository : JpaRepository<MerchantBranch, UUID> {
    fun findByNameAndMerchantApplicationIdAndActiveTrue(name: String, merchantApplicationId: UUID): MerchantBranch?
    fun findByNameAndMerchantApplicationId(name: String, merchantApplicationId: UUID): MerchantBranch?

    fun findAllByMerchantApplicationIdAndActiveTrue(merchantApplicationId: UUID): List<MerchantBranch>
    fun findAllByActiveTrue(): List<MerchantBranch>
    fun findAllByMerchantApplicationIdInAndActiveTrue(merchantIds: Collection<UUID>): List<MerchantBranch>
    fun findByIdAndActiveTrue(id: UUID): MerchantBranch?
    fun existsByMerchantApplicationIdAndIdAndActiveTrue(merchantApplicationId: UUID, id: UUID): Boolean

    @Query(
        value = """
            SELECT *
            FROM blitzpay.merchant_branches
            WHERE google_place_id IS NOT NULL
              AND active = TRUE
              AND (place_enrichment_status IS NULL OR place_enrichment_status IN ('PENDING', 'FAILED'))
            ORDER BY updated_at
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findPlaceIdsNeedingEnrichment(@Param("limit") limit: Int): List<MerchantBranch>
}
