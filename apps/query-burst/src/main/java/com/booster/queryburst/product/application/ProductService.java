package com.booster.queryburst.product.application;

import com.booster.queryburst.member.domain.Member;
import com.booster.queryburst.member.domain.MemberRepository;
import com.booster.queryburst.product.application.dto.ProductCreateCommand;
import com.booster.queryburst.product.application.dto.ProductResult;
import com.booster.queryburst.product.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductQueryRepository productQueryRepository;
    private final CategoryRepository categoryRepository;
    private final MemberRepository memberRepository;

    /**
     * 커서 기반 상품 목록 조회 (OFFSET 없음)
     *
     * 복합 인덱스 활용: idx_product_category_status_price
     * - categoryId, status 필터가 함께 있을 때 최적화
     */
    @Transactional(readOnly = true)
    public List<ProductResult> getProductsByCursor(
            Long cursorId,
            Long categoryId,
            ProductStatus status,
            Long minPrice,
            Long maxPrice,
            int size
    ) {
        return productQueryRepository.findByCursor(cursorId, categoryId, status, minPrice, maxPrice, size);
    }

    public Long createProduct(ProductCreateCommand command) {
        Category category = categoryRepository.findById(command.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다. id=" + command.categoryId()));

        Member seller = memberRepository.findById(command.sellerId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 판매자입니다. id=" + command.sellerId()));

        Product product = Product.create(
                command.name(),
                command.price(),
                command.stock(),
                command.status(),
                category,
                seller
        );
        productRepository.save(product);
        return product.getId();
    }

    public void updatePrice(Long productId, Long price) {
        Product product = getProductOrThrow(productId);
        product.updatePrice(price);
    }

    public void updateStatus(Long productId, ProductStatus status) {
        Product product = getProductOrThrow(productId);
        product.changeStatus(status);
    }

    public void deleteProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new IllegalArgumentException("존재하지 않는 상품입니다. id=" + productId);
        }
        productRepository.deleteById(productId);
    }

    private Product getProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다. id=" + productId));
    }
}