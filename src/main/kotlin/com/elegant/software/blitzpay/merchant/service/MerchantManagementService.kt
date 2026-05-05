package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.UpdateMerchantRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.repository.MonitoringRecordRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MerchantManagementService(
    private val repository: MerchantApplicationRepository,
    private val branchRepository: MerchantBranchRepository,
    private val monitoringRecordRepository: MonitoringRecordRepository,
    private val storageService: StorageService
) {
    private val log = LoggerFactory.getLogger(MerchantManagementService::class.java)

    fun get(applicationId: UUID): MerchantDetailsResponse =
        repository.findById(applicationId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $applicationId") }
            .toDetailsResponse()

    fun update(applicationId: UUID, request: UpdateMerchantRequest): MerchantDetailsResponse {
        require(request.legalBusinessName.isNotBlank()) { "legalBusinessName must not be blank" }
        require(request.primaryBusinessAddress.isNotBlank()) { "primaryBusinessAddress must not be blank" }
        require(request.contactFullName.isNotBlank()) { "contactFullName must not be blank" }
        require(request.contactEmail.isNotBlank()) { "contactEmail must not be blank" }
        require(request.contactPhoneNumber.isNotBlank()) { "contactPhoneNumber must not be blank" }

        val application = repository.findById(applicationId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $applicationId") }

        application.updateProfile(
            legalBusinessName = request.legalBusinessName,
            primaryBusinessAddress = request.primaryBusinessAddress,
            primaryContact = PrimaryContact(
                fullName = request.contactFullName,
                email = request.contactEmail,
                phoneNumber = request.contactPhoneNumber
            ),
            activePaymentChannels = request.activePaymentChannels,
            nextStatus = request.status
        )

        return repository.save(application).toDetailsResponse()
    }

    @Transactional
    fun deleteMerchantApplication(applicationId: UUID) {
        val application = repository.findById(applicationId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $applicationId") }
        branchRepository.deleteAllByMerchantApplicationId(applicationId)
        application.monitoringRecord?.let { monitoringRecordRepository.deleteById(it.id) }
        repository.delete(application)
        log.info("merchant application deleted id={}", applicationId)
    }

    private fun MerchantApplication.toDetailsResponse() = MerchantDetailsResponse(
        applicationId = id,
        applicationReference = applicationReference,
        registrationNumber = businessProfile.registrationNumber,
        businessType = businessProfile.businessType,
        operatingCountry = businessProfile.operatingCountry,
        legalBusinessName = businessProfile.legalBusinessName,
        primaryBusinessAddress = businessProfile.primaryBusinessAddress,
        contactFullName = primaryContact.fullName,
        contactEmail = primaryContact.email,
        contactPhoneNumber = primaryContact.phoneNumber,
        activePaymentChannels = activePaymentChannels.toSet(),
        status = status,
        submittedAt = submittedAt,
        lastUpdatedAt = lastUpdatedAt,
        logoStorageKey = businessProfile.logoStorageKey,
        logoUrl = signedUrl(businessProfile.logoStorageKey)
    )

    private fun signedUrl(storageKey: String?): String? =
        storageKey?.let { runCatching { storageService.presignDownload(it) }.getOrNull() }
}
