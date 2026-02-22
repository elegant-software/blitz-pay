package com.elegant.software.blitzpay.merchant

import org.springframework.stereotype.Service

@Service
class MerchantService(private val repository: MerchantRepository) {

    fun create(merchant: Merchant): Merchant = repository.save(merchant)

    fun findAll(): List<Merchant> = repository.findAll()

    fun findById(id: Long): Merchant? = repository.findById(id).orElse(null)

    fun delete(id: Long) = repository.deleteById(id)
}
