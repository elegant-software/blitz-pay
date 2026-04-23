package com.elegant.software.blitzpay.merchant.domain

import jakarta.persistence.Column
import jakarta.persistence.CollectionTable
import jakarta.persistence.Embedded
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "merchant_branches",
    schema = "blitzpay",
    indexes = [Index(name = "idx_merchant_branches_merchant_application_id", columnList = "merchant_application_id")]
)
class MerchantBranch(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_application_id", nullable = false, updatable = false)
    val merchantApplicationId: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_application_id", insertable = false, updatable = false)
    val merchantApplication: MerchantApplication? = null,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "address_line1")
    var addressLine1: String? = null,

    @Column(name = "address_line2")
    var addressLine2: String? = null,

    @Column(name = "city")
    var city: String? = null,

    @Column(name = "postal_code")
    var postalCode: String? = null,

    @Column(name = "country")
    var country: String? = null,

    @Column(name = "contact_full_name")
    var contactFullName: String? = null,

    @Column(name = "contact_email")
    var contactEmail: String? = null,

    @Column(name = "contact_phone_number")
    var contactPhoneNumber: String? = null,

    @Column(name = "image_storage_key")
    var imageStorageKey: String? = null,

    @Embedded
    var location: MerchantLocation? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "merchant_branch_payment_channels",
        joinColumns = [JoinColumn(name = "merchant_branch_id")]
    )
    @Column(name = "payment_channel", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var activePaymentChannels: MutableSet<MerchantPaymentChannel> = linkedSetOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    fun deactivate(at: Instant = Instant.now()) {
        active = false
        updatedAt = at
    }

    fun updateDetails(
        name: String,
        active: Boolean,
        addressLine1: String?,
        addressLine2: String?,
        city: String?,
        postalCode: String?,
        country: String?,
        contactFullName: String?,
        contactEmail: String?,
        contactPhoneNumber: String?,
        activePaymentChannels: Set<MerchantPaymentChannel>,
        location: MerchantLocation?,
        at: Instant = Instant.now(),
    ) {
        this.name = name
        this.active = active
        this.addressLine1 = addressLine1
        this.addressLine2 = addressLine2
        this.city = city
        this.postalCode = postalCode
        this.country = country
        this.contactFullName = contactFullName
        this.contactEmail = contactEmail
        this.contactPhoneNumber = contactPhoneNumber
        this.activePaymentChannels = activePaymentChannels.toMutableSet()
        this.location = location
        this.updatedAt = at
    }

    fun updateImage(storageKey: String, at: Instant = Instant.now()) {
        imageStorageKey = storageKey
        updatedAt = at
    }
}
