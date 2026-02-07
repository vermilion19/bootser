package com.booster.kotlin.sample.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "samples")
class Sample private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var description: String,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun update(name: String, description: String) {
        this.name = name
        this.description = description
        this.updatedAt = LocalDateTime.now()
    }

    companion object {
        fun create(name: String, description: String): Sample =
            Sample(name = name, description = description)
    }
}
