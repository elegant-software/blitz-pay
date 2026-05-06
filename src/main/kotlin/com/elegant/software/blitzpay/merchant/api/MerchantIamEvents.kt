package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

data class MerchantActivated(
    val merchantId: UUID,
    val merchantName: String,
)

data class BranchCreated(
    val branchId: UUID,
    val merchantId: UUID,
    val branchName: String,
    val merchantName: String,
)

data class MerchantNameUpdated(
    val merchantId: UUID,
    val newName: String,
)

data class BranchNameUpdated(
    val branchId: UUID,
    val merchantId: UUID,
    val newName: String,
)
