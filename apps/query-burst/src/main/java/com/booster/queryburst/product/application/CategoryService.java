package com.booster.queryburst.product.application;

import com.booster.queryburst.product.domain.Category;
import com.booster.queryburst.product.domain.CategoryQueryRepository;
import com.booster.queryburst.product.domain.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryQueryRepository categoryQueryRepository;

    public Page<Category> getCategories(Pageable pageable) {
        return categoryRepository.findAll(pageable);
    }

    public Long createCategory(String name, Long parentId) {
        Category parent = null;
        Optional<Category> categoryOp = categoryRepository.findById(parentId);
        if (categoryOp.isPresent()) {
            parent = categoryOp.get();
            Category category = Category.createChild(name, parent);
            categoryRepository.save(category);
            return category.getId();
        } else {
            Category category = Category.createRoot(name);
            categoryRepository.save(category);
            return category.getId();
        }
    }

}
