package com.booster.queryburstmsa.analytics.application;

import com.booster.queryburstmsa.analytics.domain.entity.DailySalesSummaryEntity;
import com.booster.queryburstmsa.analytics.domain.entity.ProcessedOrderEventEntity;
import com.booster.queryburstmsa.analytics.domain.entity.ProductDailySalesEntity;
import com.booster.queryburstmsa.analytics.domain.repository.DailySalesSummaryRepository;
import com.booster.queryburstmsa.analytics.domain.repository.ProcessedOrderEventRepository;
import com.booster.queryburstmsa.analytics.domain.repository.ProductDailySalesRepository;
import com.booster.queryburstmsa.analytics.web.dto.DailySalesSummaryView;
import com.booster.queryburstmsa.analytics.web.dto.ProductDailySalesView;
import com.booster.queryburstmsa.contracts.event.OrderEventPayload;
import com.booster.queryburstmsa.contracts.event.OrderEventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final String CONSUMER_GROUP = "query-burst-msa-analytics";

    private final DailySalesSummaryRepository dailySalesSummaryRepository;
    private final ProductDailySalesRepository productDailySalesRepository;
    private final ProcessedOrderEventRepository processedOrderEventRepository;

    public AnalyticsService(
            DailySalesSummaryRepository dailySalesSummaryRepository,
            ProductDailySalesRepository productDailySalesRepository,
            ProcessedOrderEventRepository processedOrderEventRepository
    ) {
        this.dailySalesSummaryRepository = dailySalesSummaryRepository;
        this.productDailySalesRepository = productDailySalesRepository;
        this.processedOrderEventRepository = processedOrderEventRepository;
    }

    public List<DailySalesSummaryView> getDailySales(LocalDate from, LocalDate to) {
        return dailySalesSummaryRepository.findByDateBetweenOrderByDateAsc(from, to).stream()
                .map(DailySalesSummaryView::from)
                .toList();
    }

    public List<ProductDailySalesView> getTopProducts(LocalDate date) {
        return productDailySalesRepository.findTop10ByDateOrderBySoldCountDesc(date).stream()
                .map(ProductDailySalesView::from)
                .toList();
    }

    public List<ProductDailySalesView> getProductTrend(Long productId, LocalDate from, LocalDate to) {
        return productDailySalesRepository.findByDateBetweenAndProductIdOrderByDateAsc(from, to, productId).stream()
                .map(ProductDailySalesView::from)
                .toList();
    }

    @Transactional
    public void apply(OrderEventPayload payload) {
        if (payload.eventType() != OrderEventType.ORDER_CREATED && payload.eventType() != OrderEventType.ORDER_CANCELED) {
            return;
        }

        String eventKey = payload.eventType() + ":" + payload.orderId() + ":" + payload.occurredAt();
        if (processedOrderEventRepository.existsByEventKey(eventKey)) {
            return;
        }

        int multiplier = payload.eventType() == OrderEventType.ORDER_CREATED ? 1 : -1;
        LocalDate targetDate = payload.occurredAt().toLocalDate();

        payload.items().forEach(item -> {
            DailySalesSummaryEntity dailySummary = dailySalesSummaryRepository.findByDateAndCategoryId(targetDate, item.categoryId())
                    .orElseGet(() -> DailySalesSummaryEntity.create(targetDate, item.categoryId()));
            dailySummary.apply(item.quantity() * item.unitPrice() * multiplier, multiplier);
            dailySalesSummaryRepository.save(dailySummary);

            ProductDailySalesEntity productSales = productDailySalesRepository.findByDateAndProductId(targetDate, item.productId())
                    .orElseGet(() -> ProductDailySalesEntity.create(targetDate, item.productId()));
            productSales.apply(item.quantity() * multiplier, item.quantity() * item.unitPrice() * multiplier);
            productDailySalesRepository.save(productSales);
        });

        processedOrderEventRepository.save(ProcessedOrderEventEntity.create(CONSUMER_GROUP, eventKey));
    }
}
