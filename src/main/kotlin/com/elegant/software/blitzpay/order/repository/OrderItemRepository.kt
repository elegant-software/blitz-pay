package com.elegant.software.blitzpay.order.repository

import com.elegant.software.blitzpay.order.domain.OrderItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderItemRepository : JpaRepository<OrderItem, UUID> {
    fun findAllByOrderIdFk(orderIdFk: UUID): List<OrderItem>
}
