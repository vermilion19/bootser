package com.booster.kotlin.shoppingservice.catalog.web.dto.request

import com.booster.kotlin.shoppingservice.catalog.application.dto.CreateCategoryCommand
import jakarta.validation.constraints.NotBlank

data class CreateCategoryRequest(
    @field:NotBlank val name: String,
    val parentId: Long? = null,
    ){
    fun toCommand() = CreateCategoryCommand(name = name, parentId = parentId)
}