-- KEYS[1]: 상품 재고 Key (예: item:stock:1)
-- ARGV[1]: 차감할 수량 (quantity)

local stock = tonumber(redis.call('GET', KEYS[1]))

-- 재고가 없거나(nil), 0보다 작거나, 요청 수량보다 적으면 실패(-1 리턴)
if stock == nil or stock < tonumber(ARGV[1]) then
    return -1
end

-- 재고 차감 실행
redis.call('DECRBY', KEYS[1], ARGV[1])

-- 남은 재고 리턴
return stock - tonumber(ARGV[1])