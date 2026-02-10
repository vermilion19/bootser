package com.booster.kotlin.testservice.application.dto

import com.booster.kotlin.testservice.domain.Animal

data class AnimalCreateRequest(
    val name: String,
    val species: String,
    val age: Int
)

data class AnimalResponse(
    val id: Long,
    val name: String,
    val species: String,
    val age: Int
)
data class AnimalUpdateRequest(
    val age: Int
)


fun Animal.toResponse() : AnimalResponse{
    return AnimalResponse(
        id = this.id!!,
        name = this.name,
        species = this.species,
        age = this.age
    )
}