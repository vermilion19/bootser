package com.booster.kotlin.sample.application

import com.booster.kotlin.core.logger
import com.booster.kotlin.sample.domain.Sample
import com.booster.kotlin.sample.domain.SampleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SampleService(
    private val sampleRepository: SampleRepository
) {
    private val log = logger()

    fun findAll(): List<Sample> = sampleRepository.findAll()

    fun findById(id: Long): Sample? = sampleRepository.findById(id).orElse(null)

    @Transactional
    fun create(command: CreateSampleCommand): Sample {
        log.info("Creating sample: {}", command.name)
        val sample = Sample.create(command.name, command.description)
        return sampleRepository.save(sample)
    }

    @Transactional
    fun update(id: Long, command: UpdateSampleCommand): Sample {
        val sample = sampleRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Sample not found: $id") }

        sample.update(command.name, command.description)
        return sample
    }

    @Transactional
    fun delete(id: Long) {
        sampleRepository.deleteById(id)
    }
}

data class CreateSampleCommand(
    val name: String,
    val description: String
)

data class UpdateSampleCommand(
    val name: String,
    val description: String
)
