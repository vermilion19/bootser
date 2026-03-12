package com.booster.kotlin.shoppingservice.catalog.domain

import com.booster.kotlin.shoppingservice.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "product_option_values")
class ProductOptionValue(
    optionGroup: ProductOptionGroup,
    value: String,
    additionalPrice: Long,
    displayOrder: Int,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_group_id", nullable = false)
    val optionGroup: ProductOptionGroup = optionGroup

    @Column(nullable = false)
    var value: String = value
        private set

    @Column(nullable = false)
    var additionalPrice: Long = additionalPrice
        private set

    @Column(nullable = false)
    var displayOrder: Int = displayOrder
        private set

    companion object {
        fun create(
            optionGroup: ProductOptionGroup,
            value: String,
            additionalPrice: Long = 0,
            displayOrder: Int,
        ): ProductOptionValue =
            ProductOptionValue(optionGroup, value, additionalPrice, displayOrder)
    }
}
