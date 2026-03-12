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
@Table(name = "product_images")
class ProductImage(
    product: Product,
    imageUrl: String,
    displayOrder: Int,
    isThumbnail: Boolean,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product = product

    @Column(nullable = false)
    var imageUrl: String = imageUrl
        private set

    @Column(nullable = false)
    var displayOrder: Int = displayOrder
        private set

    @Column(nullable = false)
    var isThumbnail: Boolean = isThumbnail
        private set

    companion object {
        fun create(
            product: Product,
            imageUrl: String,
            displayOrder: Int,
            isThumbnail: Boolean = false,
        ): ProductImage = ProductImage(product, imageUrl, displayOrder, isThumbnail)
    }
}
