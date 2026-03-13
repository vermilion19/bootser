package com.booster.kotlin.shoppingservice.coupon.application

import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.common.exception.orThrow
import com.booster.kotlin.shoppingservice.coupon.application.dto.CreateCouponCommand
import com.booster.kotlin.shoppingservice.coupon.domain.Coupon
import com.booster.kotlin.shoppingservice.coupon.domain.CouponCategoryTarget
import com.booster.kotlin.shoppingservice.coupon.domain.CouponProductTarget
import com.booster.kotlin.shoppingservice.coupon.domain.CouponRepository
import com.booster.kotlin.shoppingservice.coupon.domain.OrderCouponUsage
import com.booster.kotlin.shoppingservice.coupon.domain.OrderCouponUsageRepository
import com.booster.kotlin.shoppingservice.coupon.domain.UserCoupon
import com.booster.kotlin.shoppingservice.coupon.domain.UserCouponRepository
import com.booster.kotlin.shoppingservice.coupon.domain.UserCouponStatus
import com.booster.kotlin.shoppingservice.coupon.exception.CouponException
import com.booster.kotlin.shoppingservice.order.domain.Order
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CouponService(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val orderCouponUsageRepository: OrderCouponUsageRepository,
) {

    fun create(command: CreateCouponCommand): Coupon {
        if (couponRepository.existsByCode(command.code)) {
            throw CouponException(ErrorCode.COUPON_CODE_DUPLICATE)
        }

        val coupon = couponRepository.save(
            Coupon.create(
                code = command.code,
                name = command.name,
                couponType = command.couponType,
                discountValue = command.discountValue,
                maxDiscountAmount = command.maxDiscountAmount,
                minOrderAmount = command.minOrderAmount,
                startAt = command.startAt,
                endAt = command.endAt,
            )
        )

        command.productIds.forEach { productId ->
            coupon.productTargets.add(CouponProductTarget(coupon, productId))
        }
        command.categoryIds.forEach { categoryId ->
            coupon.categoryTargets.add(CouponCategoryTarget(coupon, categoryId))
        }

        return coupon
    }

    /**
     * 쿠폰 발급.
     * DB UNIQUE 제약(user_id, coupon_id)이 최후 방어선이며,
     * 선체크로 명확한 에러 메시지를 반환한다.
     */
    fun issue(userId: Long, couponId: Long): UserCoupon {
        val coupon = couponRepository.findById(couponId)
            .orThrow { CouponException(ErrorCode.COUPON_NOT_FOUND) }

        if (!coupon.isAvailable()) {
            throw CouponException(ErrorCode.COUPON_NOT_AVAILABLE)
        }

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw CouponException(ErrorCode.COUPON_ALREADY_ISSUED)
        }

        return userCouponRepository.save(UserCoupon.create(userId, coupon))
    }

    /**
     * 주문에 쿠폰 적용.
     * OrderService에서 주문 생성 시 호출된다.
     * UserCoupon 상태를 USED로 전이하고, OrderCouponUsage 스냅샷을 저장한다.
     */
    fun applyToOrder(userId: Long, userCouponId: Long, order: Order) {
        val userCoupon = userCouponRepository.findById(userCouponId)
            .orThrow { CouponException(ErrorCode.USER_COUPON_NOT_FOUND) }

        if (userCoupon.userId != userId) throw CouponException(ErrorCode.FORBIDDEN)

        val discount = userCoupon.coupon.calculateDiscount(order.totalPrice)
        userCoupon.use(order.id)
        order.applyDiscount(discount)

        orderCouponUsageRepository.save(OrderCouponUsage.create(order.id, userCoupon, discount))
    }

    @Transactional(readOnly = true)
    fun getAvailableCoupons(userId: Long): List<UserCoupon> =
        userCouponRepository.findByUserIdAndStatus(userId, UserCouponStatus.AVAILABLE)

    @Transactional(readOnly = true)
    fun getAllMyCoupons(userId: Long): List<UserCoupon> =
        userCouponRepository.findAllByUserId(userId)

    @Transactional(readOnly = true)
    fun getCoupon(couponId: Long): Coupon =
        couponRepository.findById(couponId).orThrow { CouponException(ErrorCode.COUPON_NOT_FOUND) }
}