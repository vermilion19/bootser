package com.booster.kotlin.shoppingservice.coupon.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface CouponRepository : JpaRepository<Coupon, Long> {
    fun findByCode(code: String): Optional<Coupon>
    fun existsByCode(code: String): Boolean
}