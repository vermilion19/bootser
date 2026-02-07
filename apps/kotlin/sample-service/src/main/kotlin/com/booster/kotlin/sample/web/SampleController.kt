package com.booster.kotlin.sample.web

import com.booster.kotlin.sample.application.CreateSampleCommand
import com.booster.kotlin.sample.application.SampleService
import com.booster.kotlin.sample.application.UpdateSampleCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/samples")
class SampleController(
    private val sampleService: SampleService
) {

    @GetMapping
    fun findAll(): ResponseEntity<List<SampleResponse>> {
        val samples = sampleService.findAll()
            .map { SampleResponse.from(it) }
        return ResponseEntity.ok(samples)
    }

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<SampleResponse> {
        val sample = sampleService.findById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(SampleResponse.from(sample))
    }

    @PostMapping
    fun create(@Valid @RequestBody request: CreateSampleRequest): ResponseEntity<SampleResponse> {
        val sample = sampleService.create(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(SampleResponse.from(sample))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateSampleRequest
    ): ResponseEntity<SampleResponse> {
        val sample = sampleService.update(id, request.toCommand())
        return ResponseEntity.ok(SampleResponse.from(sample))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Unit> {
        sampleService.delete(id)
        return ResponseEntity.noContent().build()
    }
}

data class CreateSampleRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val description: String
) {
    fun toCommand() = CreateSampleCommand(name, description)
}

data class UpdateSampleRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val description: String
) {
    fun toCommand() = UpdateSampleCommand(name, description)
}

data class SampleResponse(
    val id: Long,
    val name: String,
    val description: String,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(sample: com.booster.kotlin.sample.domain.Sample) = SampleResponse(
            id = sample.id,
            name = sample.name,
            description = sample.description,
            createdAt = sample.createdAt.toString(),
            updatedAt = sample.updatedAt.toString()
        )
    }
}
