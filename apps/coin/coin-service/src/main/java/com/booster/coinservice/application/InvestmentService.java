package com.booster.coinservice.application;

import com.booster.coinservice.domain.*;
import com.booster.coinservice.domain.enums.OrderStatus;
import com.booster.coinservice.domain.enums.OrderType;
import com.booster.coinservice.dto.WalletResponse;
import com.booster.coinservice.event.WalletUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentService {

    private final InvestmentWalletRepository walletRepository;
    private final CoinAssetRepository assetRepository;
    private final InvestmentOrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void createWallet(String userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            return;
        }
        walletRepository.save(new InvestmentWallet(userId));
    }

    // 2. 시장가 매수 (즉시 체결)
    @Transactional
    public void buyMarket(String userId, String coinCode, BigDecimal amountToBuy) {
        // [중요] 가격은 서버가 Redis에서 직접 조회 (클라이언트 조작 방지)
        BigDecimal currentPrice = getCurrentPrice(coinCode);
        if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("현재 가격 정보를 가져올 수 없습니다.");
        }

        // 1. 지갑 조회 (비관적 락 사용 - 동시성 제어)
        InvestmentWallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("지갑이 존재하지 않습니다."));

        // 2. 가격 계산 및 출금
        BigDecimal totalPrice = currentPrice.multiply(amountToBuy);
        wallet.withdraw(totalPrice); // 잔액 부족 시 예외 발생

        // 3. 코인 지급 (자산 추가)
        addCoinAsset(wallet, coinCode, amountToBuy, currentPrice);

        // 4. 주문 기록 저장
        saveOrder(userId, coinCode, OrderType.BUY_MARKET, OrderStatus.FILLED, currentPrice, amountToBuy);

        // 5. 변경 알림 이벤트 발행
        eventPublisher.publishEvent(new WalletUpdatedEvent(userId));
    }

    // 3. 지정가 매수 (주문 예약)
    @Transactional
    public void placeLimitOrder(String userId, String coinCode, BigDecimal targetPrice, BigDecimal amountToBuy) {
        // 1. 지갑 조회 (비관적 락)
        InvestmentWallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("지갑이 존재하지 않습니다."));

        // 2. 예약 금액만큼 미리 출금 (Lock-In)
        BigDecimal totalCost = targetPrice.multiply(amountToBuy);
        wallet.withdraw(totalCost);

        // 3. 대기 주문 생성 (PENDING)
        InvestmentOrder order = InvestmentOrder.builder()
                .userId(userId)
                .coinCode(coinCode)
                .orderType(OrderType.BUY_LIMIT)
                .status(OrderStatus.PENDING)
                .price(targetPrice)
                .quantity(amountToBuy)
                .build();
        orderRepository.save(order);

        // 4. 알림 (돈이 빠져나갔으니 갱신 필요)
        eventPublisher.publishEvent(new WalletUpdatedEvent(userId));
    }

    // 4. 매칭 엔진 (시세 변동 시 호출됨)
    @Transactional
    public void matchOrders(String coinCode, BigDecimal currentPrice) {
        // 1. 체결 가능한 주문 조회 (비관적 락 걸려 있음)
        List<InvestmentOrder> orders = orderRepository.findBuyableOrders(coinCode, currentPrice);

        if (orders.isEmpty()) return;

        log.info("매칭 엔진 동작: {} 주문 {}건 체결 시도 (현재가: {})", coinCode, orders.size(), currentPrice);

        for (InvestmentOrder order : orders) {
            processOrderMatch(order, currentPrice);
        }
    }

    // 내부 메서드: 단일 주문 체결 처리
    private void processOrderMatch(InvestmentOrder order, BigDecimal currentPrice) {
        // 1. 주문 상태 변경
        order.fill(); // FILLED, time update

        // 2. 자산 지급을 위해 지갑 조회 (여기서도 락 필요)
        InvestmentWallet wallet = walletRepository.findByUserIdWithLock(order.getUserId())
                .orElseThrow(() -> new IllegalStateException("주문자의 지갑을 찾을 수 없습니다."));

        // 3. 코인 지급
        addCoinAsset(wallet, order.getCoinCode(), order.getQuantity(), currentPrice);

        // 4. 차액 환불 (Best Execution Policy)
        // 사용자가 100원에 주문했는데 90원에 체결되면 10원 * 수량만큼 돌려줌
        BigDecimal orderedCost = order.getPrice().multiply(order.getQuantity());
        BigDecimal actualCost = currentPrice.multiply(order.getQuantity());

        if (orderedCost.compareTo(actualCost) > 0) {
            BigDecimal refundAmount = orderedCost.subtract(actualCost);
            wallet.deposit(refundAmount);
        }

        // 5. 체결 알림
        eventPublisher.publishEvent(new WalletUpdatedEvent(order.getUserId()));
    }

    // 5. 내 지갑 조회 (수익률 계산)
    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(String userId) {
        InvestmentWallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("지갑이 없습니다."));

        WalletResponse response = new WalletResponse();
        response.setTotalKrw(wallet.getBalance());

        BigDecimal totalCoinValue = BigDecimal.ZERO;
        List<WalletResponse.CoinDetail> coinDetails = new ArrayList<>();

        for (CoinAsset asset : wallet.getAssets()) {
            BigDecimal currentPrice = getCurrentPrice(asset.getCoinCode());

            // 평가금액 = 수량 * 현재가
            BigDecimal evaluationValue = asset.getQuantity().multiply(currentPrice);
            totalCoinValue = totalCoinValue.add(evaluationValue);

            // 수익률 계산
            BigDecimal profitRate = BigDecimal.ZERO;
            BigDecimal profitAmount = BigDecimal.ZERO;

            if (asset.getAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diff = currentPrice.subtract(asset.getAveragePrice());
                // (현재가 - 평단가) / 평단가 * 100
                profitRate = diff.divide(asset.getAveragePrice(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                // (현재가 - 평단가) * 수량
                profitAmount = diff.multiply(asset.getQuantity());
            }

            WalletResponse.CoinDetail detail = new WalletResponse.CoinDetail();
            detail.setCode(asset.getCoinCode());
            detail.setAmount(asset.getQuantity());
            detail.setAveragePrice(asset.getAveragePrice());
            detail.setCurrentPrice(currentPrice);
            detail.setProfitRate(profitRate);
            detail.setProfitAmount(profitAmount);
            coinDetails.add(detail);
        }

        response.setCoins(coinDetails);
        response.setTotalAssetValue(wallet.getBalance().add(totalCoinValue));
        // 전체 수익률 계산은 필요 시 추가 ( (총자산 - 원금) / 원금 * 100 )

        return response;
    }


    // --- Helper Methods ---

    private void addCoinAsset(InvestmentWallet wallet, String coinCode, BigDecimal amount, BigDecimal price) {
        CoinAsset asset = assetRepository.findByWalletAndCoinCode(wallet, coinCode)
                .orElseGet(() -> new CoinAsset(wallet, coinCode));

        asset.addQuantity(amount, price); // 평단가 자동 계산됨
        assetRepository.save(asset);
    }

    private void saveOrder(String userId, String coinCode, OrderType type, OrderStatus status, BigDecimal price, BigDecimal quantity) {
        InvestmentOrder order = InvestmentOrder.builder()
                .userId(userId)
                .coinCode(coinCode)
                .orderType(type)
                .status(status)
                .price(price)
                .quantity(quantity)
                .build();

        if (status == OrderStatus.FILLED) {
            order.fill();
        }
        orderRepository.save(order);
    }

    private BigDecimal getCurrentPrice(String coinCode) {
        String priceStr = redisTemplate.opsForValue().get("coin:price:" + coinCode);
        return (priceStr != null) ? new BigDecimal(priceStr) : BigDecimal.ZERO;
    }
}
