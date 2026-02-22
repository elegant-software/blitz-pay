package com.elegant.software.blitzpay.merchant

import jakarta.persistence.*

@Entity
@Table(name = "merchant")
data class Merchant(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val name: String = ""
)
