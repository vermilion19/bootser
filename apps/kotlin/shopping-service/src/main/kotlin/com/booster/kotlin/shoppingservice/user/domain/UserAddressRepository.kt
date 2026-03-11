package com.booster.kotlin.shoppingservice.user.domain

import org.springframework.data.jpa.repository.JpaRepository

interface UserAddressRepository : JpaRepository<UserAddress, Long> {
    fun findAllByUser(user: User): List<UserAddress>
    fun findByUserAndIsDefaultTrue(user: User): UserAddress?
}