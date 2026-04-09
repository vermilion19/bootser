package com.booster.queryburst.product.application;

import com.booster.queryburst.product.application.dto.CategoryResult;
import com.booster.queryburst.product.domain.Category;
import com.booster.queryburst.product.domain.CategoryQueryRepository;
import com.booster.queryburst.product.domain.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryQueryRepository categoryQueryRepository;

    // v1: COUNT 쿼리 포함 페이지 기반 조회
    @Transactional(readOnly = true)
    public Page<CategoryResult> getCategories(Pageable pageable) {
        return categoryRepository.findAll(pageable)
                .map(c -> new CategoryResult(
                        c.getId(),
                        c.getName(),
                        c.getParent() != null ? c.getParent().getId() : null,
                        c.getDepth()
                ));
    }

    // v2: COUNT 쿼리 없음, OFFSET 없는 커서 기반 조회
    @Transactional(readOnly = true)
    public List<CategoryResult> getCategoriesByCursor(Long cursorId, int size) {
        return categoryQueryRepository.findByCursor(cursorId, size);
    }

    /**
     * 카테고리 생성.
     * parentId가 null이거나 존재하지 않으면 최상위(root) 카테고리로 생성한다.
     * 최대 depth는 3단계(대/중/소분류)이며, 초과 시 예외를 던진다.
     */
    public Long createCategory(String name, Long parentId) {
        if (parentId == null) {
            Category root = Category.createRoot(name);
            categoryRepository.save(root);
            return root.getId();
        }

        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상위 카테고리입니다. id=" + parentId));

        if (parent.getDepth() >= 3) {
            throw new IllegalStateException("카테고리는 최대 3단계까지만 생성할 수 있습니다.");
        }

        Category child = Category.createChild(name, parent);
        categoryRepository.save(child);
        return child.getId();
    }

    public void deleteCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new IllegalArgumentException("존재하지 않는 카테고리입니다. id=" + categoryId);
        }
        categoryRepository.deleteById(categoryId);
    }
}