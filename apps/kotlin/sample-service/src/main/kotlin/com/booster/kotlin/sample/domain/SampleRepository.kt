package com.booster.kotlin.sample.domain

import org.springframework.data.jpa.repository.JpaRepository

interface SampleRepository : JpaRepository<Sample, Long> {
    fun findByName(name: String): Sample?
}
