package com.booster.kotlin.shoppingservice.auth.domain

import org.springframework.data.repository.CrudRepository

interface RefreshTokenRepository : CrudRepository<RefreshToken, Long>{
}