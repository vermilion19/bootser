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
){
    companion object{
        fun from(animal: Animal) : AnimalResponse{
            return AnimalResponse(
                id = animal.id!!,
                name = animal.name,
                species = animal.species,
                age = animal.age
            )
        }
    }
}

data class AnimalUpdateRequest(
    val age: Int
)