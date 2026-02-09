package com.booster.kotlin.testservice.domain

import org.springframework.data.jpa.repository.JpaRepository

interface AnimalRepository : JpaRepository<Animal, Long> {
}