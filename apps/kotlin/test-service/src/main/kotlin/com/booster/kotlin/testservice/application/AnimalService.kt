package com.booster.kotlin.testservice.application

import com.booster.kotlin.testservice.application.dto.AnimalCreateRequest
import com.booster.kotlin.testservice.application.dto.AnimalResponse
import com.booster.kotlin.testservice.domain.Animal
import com.booster.kotlin.testservice.domain.AnimalRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AnimalService(
    private val animalRepository: AnimalRepository
) {

    @Transactional
    fun registerAnimal(request : AnimalCreateRequest) : Long{
        val animal = Animal(
            name = request.name,
            species = request.species,
            age = request.age
        )

        val savedAnimal = animalRepository.save(animal)
        return savedAnimal.id!!
    }

    @Transactional
    fun getAnimal(id: Long): AnimalResponse {
        val animal = animalRepository.findByIdOrNull(id) ?: throw IllegalArgumentException("Animal not found with id $id")
        return AnimalResponse.from(animal)
    }
}