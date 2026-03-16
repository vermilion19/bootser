package com.booster.kotlin.shoppingservice.order

import com.booster.kotlin.shoppingservice.order.domain.Order
import com.booster.kotlin.shoppingservice.order.domain.OrderItem
import com.booster.kotlin.shoppingservice.order.domain.OrderStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class OrderTest : DescribeSpec({

    fun createOrder() = Order.create(
        userId = 1L,
        recipientName = "홍길동",
        recipientPhone = "010-1234-5678",
        zipCode = "12345",
        address1 = "서울시 강남구",
        address2 = "101호",
    )

    fun addItemToOrder(order: Order, unitPrice: Long = 10000L, quantity: Int = 1) {
        val item = OrderItem.create(
            order = order,
            variantId = 1L,
            productName = "테스트 상품",
            variantSku = "SKU-001",
            unitPrice = unitPrice,
            quantity = quantity,
        )
        order.addItem(item)
    }

    describe("아이템 추가 (addItem)") {

        context("아이템을 추가하면") {
            it("totalPrice가 아이템 금액만큼 증가한다") {
                val order = createOrder()
                val item = OrderItem.create(order, 1L, "상품", "SKU-001", 3000L, 2)

                order.addItem(item)

                order.totalPrice shouldBe 6000L
            }

            it("여러 아이템 추가 시 누적 합산된다") {
                val order = createOrder()
                order.addItem(OrderItem.create(order, 1L, "상품A", "SKU-001", 3000L, 2))
                order.addItem(OrderItem.create(order, 2L, "상품B", "SKU-002", 5000L, 1))

                order.totalPrice shouldBe 11000L
            }
        }
    }

    describe("할인 적용 (applyDiscount)") {

        context("유효한 할인 금액이면") {
            it("discountAmount가 설정된다") {
                val order = createOrder()
                addItemToOrder(order, unitPrice = 10000L)

                order.applyDiscount(3000L)

                order.discountAmount shouldBe 3000L
            }

            it("paymentAmount = totalPrice - discountAmount 이다") {
                val order = createOrder()
                addItemToOrder(order, unitPrice = 10000L)
                order.applyDiscount(3000L)

                order.paymentAmount shouldBe 7000L
            }
        }

        context("할인 금액이 0 이하이면") {
            it("예외가 발생한다") {
                val order = createOrder()
                addItemToOrder(order, unitPrice = 10000L)

                shouldThrow<IllegalArgumentException> { order.applyDiscount(0L) }
            }
        }

        context("할인 금액이 주문 금액을 초과하면") {
            it("예외가 발생한다") {
                val order = createOrder()
                addItemToOrder(order, unitPrice = 5000L)

                shouldThrow<IllegalArgumentException> { order.applyDiscount(6000L) }
            }
        }
    }

    describe("주문 상태 전환") {

        context("CREATED → PAYMENT_PENDING (toPaymentPending)") {
            it("정상 전환된다") {
                val order = createOrder()
                order.toPaymentPending()
                order.status shouldBe OrderStatus.PAYMENT_PENDING
            }

            it("CREATED가 아닌 상태에서 호출하면 예외가 발생한다") {
                val order = createOrder()
                order.toPaymentPending()

                shouldThrow<IllegalStateException> { order.toPaymentPending() }
            }
        }

        context("PAYMENT_PENDING → PAID (pay)") {
            it("정상 전환된다") {
                val order = createOrder()
                order.toPaymentPending()
                order.pay()
                order.status shouldBe OrderStatus.PAID
            }

            it("PAYMENT_PENDING이 아닌 상태에서 호출하면 예외가 발생한다") {
                val order = createOrder()

                shouldThrow<IllegalStateException> { order.pay() }
            }
        }

        context("PAYMENT_PENDING → PAYMENT_FAILED (fail)") {
            it("정상 전환된다") {
                val order = createOrder()
                order.toPaymentPending()
                order.fail()
                order.status shouldBe OrderStatus.PAYMENT_FAILED
            }

            it("PAYMENT_PENDING이 아닌 상태에서 호출하면 예외가 발생한다") {
                val order = createOrder()

                shouldThrow<IllegalStateException> { order.fail() }
            }
        }

        context("주문 취소 (cancel)") {
            it("CREATED 상태에서 취소할 수 있다") {
                val order = createOrder()
                order.cancel()
                order.status shouldBe OrderStatus.CANCELED
            }

            it("PAYMENT_PENDING 상태에서 취소할 수 있다") {
                val order = createOrder()
                order.toPaymentPending()
                order.cancel()
                order.status shouldBe OrderStatus.CANCELED
            }

            it("PAID 상태에서 취소할 수 있다") {
                val order = createOrder()
                order.toPaymentPending()
                order.pay()
                order.cancel()
                order.status shouldBe OrderStatus.CANCELED
            }

            it("PREPARING 상태에서는 취소할 수 없다") {
                val order = createOrder()
                order.toPaymentPending()
                order.pay()
                order.prepare()

                shouldThrow<IllegalStateException> { order.cancel() }
            }
        }

        context("PAID → PREPARING → SHIPPED → DELIVERED 정상 흐름") {
            it("각 단계가 순서대로 전환된다") {
                val order = createOrder()
                order.toPaymentPending()
                order.pay()
                order.prepare()
                order.ship()
                order.deliver()

                order.status shouldBe OrderStatus.DELIVERED
            }

            it("PAID 아닌 상태에서 prepare 호출 시 예외가 발생한다") {
                val order = createOrder()

                shouldThrow<IllegalStateException> { order.prepare() }
            }

            it("PREPARING 아닌 상태에서 ship 호출 시 예외가 발생한다") {
                val order = createOrder()
                order.toPaymentPending()
                order.pay()

                shouldThrow<IllegalStateException> { order.ship() }
            }

            it("SHIPPED 아닌 상태에서 deliver 호출 시 예외가 발생한다") {
                val order = createOrder()

                shouldThrow<IllegalStateException> { order.deliver() }
            }
        }
    }
})