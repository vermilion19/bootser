package com.booster.coinservice.application;

import com.booster.coinservice.application.dto.WalletResponse;
import com.booster.coinservice.domain.coinasset.CoinAsset;
import com.booster.coinservice.domain.coinasset.CoinAssetRepository;
import com.booster.coinservice.domain.investmentorder.InvestmentOrder;
import com.booster.coinservice.domain.investmentorder.InvestmentOrderRepository;
import com.booster.coinservice.domain.investmentorder.OrderStatus;
import com.booster.coinservice.domain.investmentorder.OrderType;
import com.booster.coinservice.domain.wallet.InvestmentWallet;
import com.booster.coinservice.domain.wallet.InvestmentWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvestmentService {

    private final InvestmentWalletRepository walletRepository;
    private final CoinAssetRepository assetRepository;
    private final InvestmentOrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;

    // 1. 초기화 (지갑 생성)
    @Transactional
    public void createWallet(String userId) {
        if (walletRepository.findByUserId(userId).isPresent()) return;
        InvestmentWallet wallet = new InvestmentWallet();
        wallet.setUserId(userId);
        walletRepository.save(wallet);
    }

    // 2. 시장가 매수 (즉시 체결)
    @Transactional
    public void buyMarket(String userId, String coinCode, BigDecimal currentPrice, BigDecimal amountToBuy) {
        InvestmentWallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("지갑이 없습니다."));

        BigDecimal totalPrice = currentPrice.multiply(amountToBuy);

        // 잔액 확인 및 차감
        wallet.withdraw(totalPrice);

        // 자산 추가
        CoinAsset asset = assetRepository.findByWalletAndCoinCode(wallet, coinCode)
                .orElseGet(() -> {
                    CoinAsset newAsset = new CoinAsset();
                    newAsset.setWallet(wallet);
                    newAsset.setCoinCode(coinCode);
                    return newAsset;
                });
        asset.addAsset(amountToBuy, currentPrice);
        assetRepository.save(asset);

        // 주문 기록 저장 (체결 완료)
        InvestmentOrder order = InvestmentOrder.builder()
                .userId(userId)
                .coinCode(coinCode)
                .orderType(OrderType.BUY_MARKET)
                .status(OrderStatus.FILLED)
                .targetPrice(currentPrice)
                .amount(amountToBuy)
                .build();
        order.setFilledAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    // 3. 지정가 매수 예약 (대기 주문)
    @Transactional
    public void placeLimitOrder(String userId, String coinCode, BigDecimal targetPrice, BigDecimal amountToBuy) {
        InvestmentWallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("지갑이 없습니다."));

        BigDecimal totalPrice = targetPrice.multiply(amountToBuy);

        // 지정가 주문은 미리 돈을 묶어둬야 함 (출금 처리)
        wallet.withdraw(totalPrice);

        InvestmentOrder order = InvestmentOrder.builder()
                .userId(userId)
                .coinCode(coinCode)
                .orderType(OrderType.BUY_LIMIT)
                .status(OrderStatus.PENDING) // 대기 상태
                .targetPrice(targetPrice)
                .amount(amountToBuy)
                .build();
        orderRepository.save(order);
    }

    // 4. 매칭 엔진 (실시간 가격이 들어올 때 호출)
    // 주의: 동시성 이슈가 있을 수 있으므로 실제로는 Lock이 필요할 수 있음
    @Transactional
    public void processLimitOrders(String coinCode, BigDecimal currentPrice) {
        // 해당 코인의 '대기(PENDING)' 상태인 '매수(BUY)' 주문을 모두 조회
        // 조건: 설정한 가격 >= 현재 가격 (가격이 떨어져서 내가 원하는 가격이 옴)
        List<InvestmentOrder> pendingOrders = orderRepository.findPendingBuyOrders(coinCode, currentPrice, OrderStatus.PENDING);

        for (InvestmentOrder order : pendingOrders) {
            executeLimitOrder(order, currentPrice);
        }
    }

    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(String userId) {
        // 1. 지갑 조회
        InvestmentWallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("지갑을 찾을 수 없습니다."));

        WalletResponse response = new WalletResponse();
        response.setTotalKrw(wallet.getKrwBalance());

        BigDecimal totalAssetEvaluation = BigDecimal.ZERO; // 총 자산 평가액 (KRW + 코인평가금)
        List<WalletResponse.CoinDetail> coinDetails = new ArrayList<>();

        // 2. 보유 코인별 수익률 계산
        for (CoinAsset asset : wallet.getAssets()) {
            // Redis에서 실시간 현재가 가져오기 (없으면 평단가로 가정하거나 에러 처리)
            BigDecimal currentPrice = getCurrentPriceFromRedis(asset.getCoinCode());

            // 평가금액 = 수량 * 현재가
            BigDecimal evaluationAmount = asset.getAmount().multiply(currentPrice);

            // 평가손익 = 평가금액 - (평단가 * 수량)
            BigDecimal investAmount = asset.getAveragePrice().multiply(asset.getAmount());
            BigDecimal profitOrLoss = evaluationAmount.subtract(investAmount);

            // 수익률 = (현재가 - 평단가) / 평단가 * 100
            BigDecimal profitRate = BigDecimal.ZERO;
            if (asset.getAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
                profitRate = currentPrice.subtract(asset.getAveragePrice())
                        .divide(asset.getAveragePrice(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            WalletResponse.CoinDetail detail = new WalletResponse.CoinDetail();
            detail.setCode(asset.getCoinCode());
            detail.setAmount(asset.getAmount());
            detail.setAveragePrice(asset.getAveragePrice());
            detail.setCurrentPrice(currentPrice);
            detail.setProfitRate(profitRate);
            detail.setProfitAmount(profitOrLoss);

            coinDetails.add(detail);
            totalAssetEvaluation = totalAssetEvaluation.add(evaluationAmount);
        }

        response.setCoins(coinDetails);
        response.setTotalAssetValue(wallet.getKrwBalance().add(totalAssetEvaluation));

        // (선택) 총 수익률 계산 로직 추가 가능

        return response;
    }


    // Redis에서 현재가를 가져오는 헬퍼 메소드
    private BigDecimal getCurrentPriceFromRedis(String coinCode) {
        // Coin-Gatherer가 "coin:price:KRW-BTC" 같은 키로 가격을 저장하고 있다고 가정
        String priceStr = redisTemplate.opsForValue().get("coin:price:" + coinCode);
        if (priceStr == null) {
            return BigDecimal.ZERO; // 혹은 에러 처리
        }
        return new BigDecimal(priceStr);
    }

    private void executeLimitOrder(InvestmentOrder order, BigDecimal currentPrice) {
        // 주문 상태 변경
        order.setStatus(OrderStatus.FILLED);
        order.setFilledAt(LocalDateTime.now());

        // 자산 지급 로직 (위의 buyMarket과 유사하게 자산 추가)
        InvestmentWallet wallet = walletRepository.findByUserId(order.getUserId()).orElseThrow();
        CoinAsset asset = assetRepository.findByWalletAndCoinCode(wallet, order.getCoinCode())
                .orElseGet(() -> {
                    CoinAsset newAsset = new CoinAsset();
                    newAsset.setWallet(wallet);
                    newAsset.setCoinCode(order.getCoinCode());
                    return newAsset;
                });

        // 주의: 지정가 매수는 '주문 가격'이 아닌 '실제 체결 가격'으로 평단가를 계산할 수도 있으나,
        // 여기서는 간단하게 주문했던 TargetPrice로 계산하거나 CurrentPrice로 계산.
        // 보통은 Limit Order는 그 가격 이하에서 체결되므로 이득입니다.
        asset.addAsset(order.getAmount(), currentPrice);

        // 차액 환불 (내가 100원에 건 주문이 98원에 체결되면 2원 * 수량만큼 돌려줘야 함)
        BigDecimal orderedTotal = order.getTargetPrice().multiply(order.getAmount());
        BigDecimal actualTotal = currentPrice.multiply(order.getAmount());
        if (orderedTotal.compareTo(actualTotal) > 0) {
            wallet.deposit(orderedTotal.subtract(actualTotal));
        }
    }

}
