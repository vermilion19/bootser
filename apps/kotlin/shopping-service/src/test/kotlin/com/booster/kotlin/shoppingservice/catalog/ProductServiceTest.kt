package com.booster.kotlin.shoppingservice.catalog

import com.booster.kotlin.shoppingservice.catalog.application.CategoryService
import com.booster.kotlin.shoppingservice.catalog.application.ProductService
import com.booster.kotlin.shoppingservice.catalog.application.dto.AddOptionGroupCommand
import com.booster.kotlin.shoppingservice.catalog.application.dto.CreateProductCommand
import com.booster.kotlin.shoppingservice.catalog.application.dto.UpdateProductCommand
import com.booster.kotlin.shoppingservice.catalog.domain.Category
import com.booster.kotlin.shoppingservice.catalog.domain.Product
import com.booster.kotlin.shoppingservice.catalog.domain.ProductRepository
import com.booster.kotlin.shoppingservice.catalog.exception.CatalogException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class ProductServiceTest : DescribeSpec({

    val productRepository = mockk<ProductRepository>()
    val categoryService = mockk<CategoryService>()
    val service = ProductService(productRepository, categoryService)

    fun makeCategory() = Category.create(name = "전자기기")
    fun makeProduct() = Product.create("노트북", "고성능 노트북", 1000000L, makeCategory())

    describe("getById") {

        context("상품이 존재하면") {
            it("해당 Product를 반환한다") {
                val product = makeProduct()
                every { productRepository.findById(1L) } returns Optional.of(product)

                val result = service.getById(1L)

                result shouldBe product
            }
        }

        context("상품이 없으면") {
            it("PRODUCT_NOT_FOUND 예외를 던진다") {
                every { productRepository.findById(99L) } returns Optional.empty()

                val ex = shouldThrow<CatalogException> { service.getById(99L) }
                ex.errorCode shouldBe ErrorCode.PRODUCT_NOT_FOUND
            }
        }
    }

    describe("create") {

        context("정상 command를 전달하면") {
            it("카테고리를 조회 후 상품을 저장하고 반환한다") {
                val category = makeCategory()
                val command = CreateProductCommand(
                    name = "신상 노트북",
                    description = "최신 모델",
                    basePrice = 2000000L,
                    categoryId = 1L,
                )
                every { categoryService.getById(1L) } returns category
                every { productRepository.save(any()) } answers { firstArg() }

                val result = service.create(command)

                result.name shouldBe "신상 노트북"
                result.basePrice shouldBe 2000000L
                verify(exactly = 1) { productRepository.save(any()) }
            }
        }

        context("카테고리가 없으면") {
            it("CategoryService에서 예외를 전파한다") {
                val command = CreateProductCommand("상품", "설명", 1000L, categoryId = 99L)
                every { categoryService.getById(99L) } throws CatalogException(ErrorCode.CATEGORY_NOT_FOUND)

                val ex = shouldThrow<CatalogException> { service.create(command) }
                ex.errorCode shouldBe ErrorCode.CATEGORY_NOT_FOUND
            }
        }
    }

    describe("update") {

        context("정상 command를 전달하면") {
            it("상품 정보가 수정된다") {
                val product = makeProduct()
                val command = UpdateProductCommand(
                    productId = 1L,
                    name = "리뉴얼 노트북",
                    description = "업데이트된 설명",
                    basePrice = 1200000L,
                )
                every { productRepository.findById(1L) } returns Optional.of(product)

                val result = service.update(command)

                result.name shouldBe "리뉴얼 노트북"
                result.description shouldBe "업데이트된 설명"
                result.basePrice shouldBe 1200000L
            }
        }

        context("상품이 없으면") {
            it("PRODUCT_NOT_FOUND 예외를 던진다") {
                val command = UpdateProductCommand(99L, "이름", "설명", 1000L)
                every { productRepository.findById(99L) } returns Optional.empty()

                val ex = shouldThrow<CatalogException> { service.update(command) }
                ex.errorCode shouldBe ErrorCode.PRODUCT_NOT_FOUND
            }
        }
    }

    describe("addOptionGroup") {

        context("옵션 그룹과 옵션값을 전달하면") {
            it("상품에 옵션 그룹이 추가된다") {
                val product = makeProduct()
                val command = AddOptionGroupCommand(
                    productId = 1L,
                    name = "색상",
                    displayOrder = 1,
                    optionValues = listOf(
                        AddOptionGroupCommand.OptionValueItem("블랙", 0L, 1),
                        AddOptionGroupCommand.OptionValueItem("화이트", 0L, 2),
                    ),
                )
                every { productRepository.findById(1L) } returns Optional.of(product)

                service.addOptionGroup(command)

                product.optionGroups shouldHaveSize 1
                product.optionGroups[0].optionValues shouldHaveSize 2
                product.optionGroups[0].name shouldBe "색상"
            }
        }
    }
})