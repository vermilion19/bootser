package com.booster.queryburstmsa.catalog.application;

import com.booster.queryburstmsa.catalog.domain.ProductStatus;
import com.booster.queryburstmsa.catalog.domain.entity.CategoryEntity;
import com.booster.queryburstmsa.catalog.domain.entity.InventoryReservationEntity;
import com.booster.queryburstmsa.catalog.domain.entity.ProductEntity;
import com.booster.queryburstmsa.catalog.domain.repository.CategoryRepository;
import com.booster.queryburstmsa.catalog.domain.repository.InventoryReservationRepository;
import com.booster.queryburstmsa.catalog.domain.repository.ProductRepository;
import com.booster.queryburstmsa.catalog.web.dto.CategoryCreateRequest;
import com.booster.queryburstmsa.catalog.web.dto.CategoryResponse;
import com.booster.queryburstmsa.catalog.web.dto.ProductCreateRequest;
import com.booster.queryburstmsa.catalog.web.dto.ProductResponse;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationRequest;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationResultItem;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationResponse;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    public CatalogService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            InventoryReservationRepository inventoryReservationRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
    }

    public List<ProductResponse> getProducts(Long cursor, Long categoryId, ProductStatus status, int size) {
        return productRepository.findProducts(cursor, categoryId, status, PageRequest.of(0, size)).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public Long createProduct(ProductCreateRequest request) {
        ProductEntity entity = ProductEntity.create(
                request.name(),
                request.price(),
                request.stock(),
                request.status(),
                request.categoryId(),
                request.sellerId()
        );
        return productRepository.save(entity).getId();
    }

    @Transactional
    public void updatePrice(Long productId, long price) {
        productRepository.findById(productId)
                .ifPresent(product -> product.updatePrice(price));
    }

    @Transactional
    public void updateStatus(Long productId, ProductStatus status) {
        productRepository.findById(productId)
                .ifPresent(product -> product.updateStatus(status));
    }

    @Transactional
    public void deleteProduct(Long productId) {
        productRepository.deleteById(productId);
    }

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public Long createCategory(CategoryCreateRequest request) {
        int depth = request.parentId() == null
                ? 1
                : categoryRepository.findById(request.parentId())
                        .map(parent -> parent.getDepth() + 1)
                        .orElseThrow(() -> new IllegalArgumentException("상위 카테고리를 찾을 수 없습니다."));
        CategoryEntity entity = CategoryEntity.create(request.name(), request.parentId(), depth);
        return categoryRepository.save(entity).getId();
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        categoryRepository.deleteById(categoryId);
    }

    @Transactional
    public InventoryReservationResponse reserve(InventoryReservationRequest request) {
        return inventoryReservationRepository.findByRequestId(request.requestId())
                .map(existing -> new InventoryReservationResponse(existing.getId(), existing.getStatus(), null, existing.getItems().stream()
                        .map(item -> {
                            ProductEntity product = productRepository.findById(item.getProductId()).orElseThrow();
                            return new InventoryReservationResultItem(product.getId(), product.getCategoryId(), item.getQuantity(), product.getPrice());
                        })
                        .toList()))
                .orElseGet(() -> createReservation(request));
    }

    @Transactional
    public InventoryReservationResponse release(String reservationId) {
        return inventoryReservationRepository.findById(reservationId)
                .map(reservation -> {
                    Map<Long, ProductEntity> productsById = productRepository.findAllById(
                                    reservation.getItems().stream().map(item -> item.getProductId()).toList())
                            .stream()
                            .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
                    reservation.getItems().forEach(item -> productsById.get(item.getProductId()).restore(item.getQuantity()));
                    reservation.release();
                    return new InventoryReservationResponse(reservation.getId(), reservation.getStatus(), null, List.of());
                })
                .orElse(new InventoryReservationResponse(reservationId, InventoryReservationStatus.REJECTED, "RESERVATION_NOT_FOUND", List.of()));
    }

    @Transactional
    public InventoryReservationResponse commit(String reservationId) {
        return inventoryReservationRepository.findById(reservationId)
                .map(reservation -> {
                    reservation.commit();
                    return new InventoryReservationResponse(reservation.getId(), reservation.getStatus(), null, List.of());
                })
                .orElse(new InventoryReservationResponse(reservationId, InventoryReservationStatus.REJECTED, "RESERVATION_NOT_FOUND", List.of()));
    }

    private InventoryReservationResponse createReservation(InventoryReservationRequest request) {
        List<Long> productIds = request.items().stream()
                .map(item -> item.productId())
                .distinct()
                .toList();

        Map<Long, ProductEntity> productsById = productRepository.findAllByIdInForUpdate(productIds).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        for (var item : request.items()) {
            ProductEntity product = productsById.get(item.productId());
            if (product == null) {
                return new InventoryReservationResponse(null, InventoryReservationStatus.REJECTED, "PRODUCT_NOT_FOUND", List.of());
            }
            if (product.getStock() < item.quantity()) {
                return new InventoryReservationResponse(null, InventoryReservationStatus.REJECTED, "INSUFFICIENT_STOCK", List.of());
            }
        }

        InventoryReservationEntity reservation = InventoryReservationEntity.create(
                request.requestId(),
                request.orderId(),
                request.memberId()
        );

        for (var item : request.items()) {
            ProductEntity product = productsById.get(item.productId());
            product.reserve(item.quantity());
            reservation.addItem(item.productId(), item.quantity());
        }

        InventoryReservationEntity saved = inventoryReservationRepository.save(reservation);
        List<InventoryReservationResultItem> resultItems = request.items().stream()
                .map(item -> {
                    ProductEntity product = productsById.get(item.productId());
                    return new InventoryReservationResultItem(product.getId(), product.getCategoryId(), item.quantity(), product.getPrice());
                })
                .toList();
        return new InventoryReservationResponse(saved.getId(), saved.getStatus(), null, resultItems);
    }
}
