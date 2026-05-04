package com.elegant.software.blitzpay.merchant.repository

import com.elegant.software.blitzpay.merchant.domain.MonitoringRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MonitoringRecordRepository : JpaRepository<MonitoringRecord, UUID>
