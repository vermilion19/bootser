package com.booster.kotlin.shoppingservice.catalog.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductRepository : JpaRepository<Product, Long>{

    @Query(
        """
        SELECT p FROM Product p
        WHERE p.status = :status
        AND (:categoryId IS NULL OR p.category.id = :categoryId)
    """
    )
    fun findOnSaleProducts(
        @Param("status") status: ProductStatus,
        @Param("categoryId") categoryId: Long?,
        pageable: Pageable,
    ): Page<Product>
}