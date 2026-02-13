package com.booster.kotlin.damagochiservice.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "app_users")
class AppUser private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    val loginId: String,

    @Column(nullable = false, length = 255)
    private var passwordHash: String,

    @Column(nullable = false, length = 30)
    var nickname: String,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun matchesPassword(rawPassword: String, matcher: (String, String) -> Boolean): Boolean =
        matcher(rawPassword, passwordHash)

    companion object {
        fun create(loginId: String, passwordHash: String, nickname: String): AppUser =
            AppUser(loginId = loginId, passwordHash = passwordHash, nickname = nickname)
    }
}



