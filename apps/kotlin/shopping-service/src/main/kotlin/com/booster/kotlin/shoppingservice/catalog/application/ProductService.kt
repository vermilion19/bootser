package com.booster.kotlin.shoppingservice.catalog.application

import com.booster.kotlin.shoppingservice.catalog.application.dto.AddOptionGroupCommand
import com.booster.kotlin.shoppingservice.catalog.application.dto.CreateProductCommand
import com.booster.kotlin.shoppingservice.catalog.application.dto.UpdateProductCommand
import com.booster.kotlin.shoppingservice.catalog.domain.Product
import com.booster.kotlin.shoppingservice.catalog.domain.ProductOptionGroup
import com.booster.kotlin.shoppingservice.catalog.domain.ProductOptionValue
import com.booster.kotlin.shoppingservice.catalog.domain.ProductRepository
import com.booster.kotlin.shoppingservice.catalog.domain.ProductStatus
import com.booster.kotlin.shoppingservice.catalog.exception.CatalogException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.common.exception.orThrow
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ProductService(
    private val productRepository: ProductRepository,
    private val categoryService: CategoryService,
) {
    @Transactional(readOnly = true)
    fun getAll(categoryId: Long?, pageable: Pageable): Page<Product> =
        productRepository.findOnSaleProducts(ProductStatus.ON_SALE, categoryId, pageable)

    @Transactional(readOnly = true)
    fun getById(productId: Long): Product =
        productRepository.findById(productId).orThrow { CatalogException(ErrorCode.PRODUCT_NOT_FOUND) }

    fun create(command: CreateProductCommand): Product {
        val category = categoryService.getById(command.categoryId)
        val product = Product.create(
            name = command.name,
            description = command.description,
            basePrice = command.basePrice,
            category = category,
        )
        return productRepository.save(product)
    }

    fun update(command: UpdateProductCommand): Product {
        val product = getById(command.productId)
        product.update(
            name = command.name,
            description = command.description,
            basePrice = command.basePrice,
        )
        return product
    }

    fun addOptionGroup(command: AddOptionGroupCommand): Product {
        val product = getById(command.productId)
        val group = ProductOptionGroup.create(
            product = product,
            name = command.name,
            displayOrder = command.displayOrder,
        )
        group.optionValues.addAll(
            command.optionValues.map { item ->
                ProductOptionValue.create(
                    optionGroup = group,
                    value = item.value,
                    additionalPrice = item.additionalPrice,
                    displayOrder = item.displayOrder,
                )
            }
        )
        product.optionGroups.add(group)
        return product
    }

}