package com.booster.kotlin.damagochiservice.auth.domain

import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findByLoginId(loginId: String): AppUser?
    fun existsByLoginId(loginId: String): Boolean
}



