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
@Table(name = "product_option_groups")
class ProductOptionGroup(
    product: Product,
    name: String,
    displayOrder: Int,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product = product

    @Column(nullable = false)
    var name: String = name
        private set

    @Column(nullable = false)
    var displayOrder: Int = displayOrder
        private set

    @OneToMany(mappedBy = "optionGroup", cascade = [CascadeType.ALL], orphanRemoval = true)
    val optionValues: MutableList<ProductOptionValue> = mutableListOf()

    companion object {
        fun create(product: Product, name: String, displayOrder: Int): ProductOptionGroup =
            ProductOptionGroup(product, name, displayOrder)
    }
}
