package com.booster.kotlin.shoppingservice.catalog.domain

import com.booster.kotlin.shoppingservice.common.entity.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "products")
class Product(
    name: String,
    description: String,
    basePrice: Long,
    category: Category,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    var name: String = name
        private set

    @Column(columnDefinition = "TEXT")
    var description: String = description
        private set

    @Column(nullable = false)
    var basePrice: Long = basePrice
        private set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProductStatus = ProductStatus.ON_SALE
        private set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    var category: Category = category
        private set

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    val optionGroups: MutableList<ProductOptionGroup> = mutableListOf()

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    val images: MutableList<ProductImage> = mutableListOf()

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    val variants: MutableList<ProductVariant> = mutableListOf()

    fun update(name: String, description: String, basePrice: Long) {
        this.name = name
        this.description = description
        this.basePrice = basePrice
    }

    fun changeStatus(status: ProductStatus) {
        this.status = status
    }

    companion object {
        fun create(name: String, description: String, basePrice: Long, category: Category): Product =
            Product(name, description, basePrice, category)
    }

}