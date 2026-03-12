package com.booster.kotlin.shoppingservice.user.domain

import com.booster.kotlin.shoppingservice.common.entity.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    email: String,
    passwordHash: String,
    name: String,
    phone: String,
    role: Role = Role.USER
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(unique = true, nullable = false)
    var email: String = email
        private set

    @Column(nullable = false)
    var passwordHash: String = passwordHash
        private set

    @Column(nullable = false)
    var name: String = name
        private set

    @Column
    var phone: String = phone
        private set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.ACTIVE
        private set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role = role
        private set

    @OneToMany(mappedBy = "user",cascade = [CascadeType.ALL], orphanRemoval = true)
    val addresses: MutableList<UserAddress> = mutableListOf()

    fun updateProfile(name: String, phone: String) {
        this.name = name
        this.phone = phone
    }

    fun deactivate() {
        this.status = UserStatus.INACTIVE
        softDelete()
    }

    companion object {
        fun create(email: String, passwordHash: String, name: String, phone: String, role: Role = Role.USER): User =
            User(email, passwordHash, name, phone, role)
    }

}

enum class UserStatus {
    ACTIVE, INACTIVE
}

enum class Role {
    USER, ADMIN
}

