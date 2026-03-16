package com.booster.kotlin.shoppingservice.catalog

import com.booster.kotlin.shoppingservice.catalog.domain.Category
import com.booster.kotlin.shoppingservice.catalog.domain.Product
import com.booster.kotlin.shoppingservice.catalog.domain.ProductStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ProductTest : DescribeSpec({

    fun createCategory() = Category.create(name = "전자기기")

    fun createProduct(
        name: String = "테스트 상품",
        description: String = "설명",
        basePrice: Long = 10000L,
    ) = Product.create(name, description, basePrice, createCategory())

    describe("상품 생성 (create)") {

        context("정상 생성 시") {
            it("기본 상태는 ON_SALE 이다") {
                val product = createProduct()
                product.status shouldBe ProductStatus.ON_SALE
            }

            it("전달한 name, description, basePrice가 저장된다") {
                val product = createProduct(name = "노트북", description = "고성능 노트북", basePrice = 1500000L)

                product.name shouldBe "노트북"
                product.description shouldBe "고성능 노트북"
                product.basePrice shouldBe 1500000L
            }
        }
    }

    describe("상품 정보 수정 (update)") {

        context("새로운 name, description, basePrice를 전달하면") {
            it("각 필드가 업데이트된다") {
                val product = createProduct(name = "구형 노트북", basePrice = 500000L)

                product.update(name = "신형 노트북", description = "최신 모델", basePrice = 2000000L)

                product.name shouldBe "신형 노트북"
                product.description shouldBe "최신 모델"
                product.basePrice shouldBe 2000000L
            }
        }
    }

    describe("상품 상태 변경 (changeStatus)") {

        context("ON_SALE → SOLD_OUT") {
            it("상태가 SOLD_OUT으로 변경된다") {
                val product = createProduct()

                product.changeStatus(ProductStatus.SOLD_OUT)

                product.status shouldBe ProductStatus.SOLD_OUT
            }
        }

        context("ON_SALE → HIDDEN") {
            it("상태가 HIDDEN으로 변경된다") {
                val product = createProduct()

                product.changeStatus(ProductStatus.HIDDEN)

                product.status shouldBe ProductStatus.HIDDEN
            }
        }

        context("HIDDEN → ON_SALE (재판매 활성화)") {
            it("상태가 ON_SALE로 복구된다") {
                val product = createProduct()
                product.changeStatus(ProductStatus.HIDDEN)

                product.changeStatus(ProductStatus.ON_SALE)

                product.status shouldBe ProductStatus.ON_SALE
            }
        }
    }
})