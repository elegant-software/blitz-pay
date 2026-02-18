package com.elegant.software.blitzpay.merchant

import org.springframework.data.jpa.repository.JpaRepository

interface MerchantRepository : JpaRepository<Merchant, Long>
