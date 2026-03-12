package com.booster.kotlin.shoppingservice.catalog.application

import com.booster.kotlin.shoppingservice.catalog.application.dto.CreateCategoryCommand
import com.booster.kotlin.shoppingservice.catalog.application.dto.UpdateCategoryCommand
import com.booster.kotlin.shoppingservice.catalog.domain.Category
import com.booster.kotlin.shoppingservice.catalog.domain.CategoryRepository
import com.booster.kotlin.shoppingservice.catalog.exception.CatalogException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CategoryService(
    private val categoryRepository: CategoryRepository,
) {
    @Cacheable(value = ["categories"])
    @Transactional(readOnly = true)
    fun getAll(): List<Category> = categoryRepository.findAllRootCategories()

    @Transactional(readOnly = true)
    fun getById(id: Long): Category =
        categoryRepository.findById(id)
            .orElseThrow { CatalogException(ErrorCode.CATEGORY_NOT_FOUND) }

    @CacheEvict(value = ["categories"], allEntries = true)
    fun create(command: CreateCategoryCommand): Category {
        val parent = command.parentId?.let { getById(it) }
        return categoryRepository.save(Category.create(name = command.name, parent = parent))
    }

    @CacheEvict(value = ["categories"], allEntries = true)
    fun update(command: UpdateCategoryCommand): Category {
        val category = getById(command.categoryId)
        category.update(name = command.name)
        return category
    }

}