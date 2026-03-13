package com.booster.kotlin.shoppingservice.coupon.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserCouponRepository : JpaRepository<UserCoupon, Long> {
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    @Query("SELECT uc FROM UserCoupon uc JOIN FETCH uc.coupon WHERE uc.userId = :userId AND uc.status = :status")
    fun findByUserIdAndStatus(userId: Long, status: UserCouponStatus): List<UserCoupon>

    @Query("SELECT uc FROM UserCoupon uc JOIN FETCH uc.coupon WHERE uc.userId = :userId")
    fun findAllByUserId(userId: Long): List<UserCoupon>
}