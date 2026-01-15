---@diagnostic disable: undefined-global

-- KEYS[1]: coupon:count:{couponId} (남은 수량)
-- KEYS[2]: coupon:issued:{couponId} (발급받은 유저 Set - 중복 방지용)
-- KEYS[3]: coupon:outbox:{couponId}
-- ARGV[1]: userId (유저 ID)
-- ARGV[2]: timestamp (발급 시간)

-- 1. 중복 발급 체크
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return "DUPLICATED"
end

-- 2. 수량 체크
local count = redis.call('GET', KEYS[1])
if count == nil or tonumber(count) <= 0 then
    return "SOLD_OUT"
end

-- 3. 원자적 실행 (수량 차감 + 유저 등록 + Outbox 저장)
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])

-- Outbox 저장 (Key: userId, Value: status/time)
-- 나중에 꺼내서 Kafka로 보내기 위함
redis.call('HSET', KEYS[3], ARGV[1], ARGV[2])

return "SUCCESS"