package com.booster.kotlin.shoppingservice.catalog.domain

import com.booster.kotlin.shoppingservice.common.entity.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "product_variants")
class ProductVariant(
    product: Product,
    sku: String,
    additionalPrice: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product = product

    @Column(unique = true, nullable = false)
    var sku: String = sku
        private set

    @Column(nullable = false)
    var additionalPrice: Long = additionalPrice
        private set

    @OneToMany(mappedBy = "variant", cascade = [CascadeType.ALL], orphanRemoval = true)
    val variantOptionValues: MutableList<ProductVariantOptionValue> = mutableListOf()

    fun finalPrice(): Long = product.basePrice + additionalPrice

    companion object {
        fun create(product: Product, sku: String, additionalPrice: Long = 0): ProductVariant =
            ProductVariant(product, sku, additionalPrice)
    }
}

