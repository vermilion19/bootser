package com.booster.kotlin.shoppingservice.user.domain

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
@Table(name = "user_addresses")
class UserAddress(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    label: String,
    recipientName: String,
    recipientPhone: String,
    zipCode: String,
    address1: String,
    address2: String?,
) : BaseEntity(){
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    var label: String = label
        private set

    @Column(nullable = false)
    var recipientName: String = recipientName
        private set

    @Column(nullable = false)
    var recipientPhone: String = recipientPhone
        private set

    @Column(nullable = false)
    var zipCode: String = zipCode
        private set

    @Column(nullable = false)
    var address1: String = address1
        private set

    @Column
    var address2: String? = address2
        private set

    @Column(nullable = false)
    var isDefault: Boolean = false
        private set

    fun markAsDefault() {
        this.isDefault = true
    }

    fun unmarkAsDefault() {
        this.isDefault = false
    }

    companion object {
        fun create(
            user: User,
            label: String,
            recipientName: String,
            recipientPhone: String,
            zipCode: String,
            address1: String,
            address2: String? = null,
        ): UserAddress = UserAddress(user, label, recipientName, recipientPhone, zipCode, address1, address2)
    }

}