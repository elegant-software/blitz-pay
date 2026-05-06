package com.elegant.software.blitzpay.merchant.iam.outbound

import java.util.UUID

object KeycloakGroupNames {
    const val ROOT = "merchants"
    const val ATTR_MERCHANT_ID = "merchant_id"
    const val ATTR_MERCHANT_NAME = "merchant_name"
    const val ATTR_BRANCH_ID = "branch_id"
    const val ATTR_BRANCH_NAME = "branch_name"

    fun merchantGroup(merchantId: UUID) = "merchant_$merchantId"
    fun branchGroup(branchId: UUID) = "branch_$branchId"
}
