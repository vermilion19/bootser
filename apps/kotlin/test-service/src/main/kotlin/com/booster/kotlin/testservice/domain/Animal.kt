package com.booster.kotlin.testservice.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id

@Entity
class Animal(
    var name: String,
    var species: String,
    var age: Int,
    @Id @GeneratedValue var id: Long? = null
) : BaseEntity(){
    companion object {
        fun create(name: String, species: String, age: Int): Animal {
            return Animal(
                name = name,
                species = species,
                age = age
            )
        }
    }

    fun update(name: String, species: String, age: Int) {
        this.name = name
        this.species = species
        this.age = age
    }

}