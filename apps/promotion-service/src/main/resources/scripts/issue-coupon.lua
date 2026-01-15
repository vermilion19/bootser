---@diagnostic disable: undefined-global

-- KEYS[1]: coupon:count:{couponId} (남은 수량)
-- KEYS[2]: coupon:issued:{couponId} (발급받은 유저 Set - 중복 방지용)
-- ARGV[1]: userId (유저 ID)

-- 1. 중복 발급 체크
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return "DUPLICATED"
end

-- 2. 수량 체크
local count = redis.call('GET', KEYS[1])
if count == nil or tonumber(count) <= 0 then
    return "SOLD_OUT"
end

-- 3. 수량 차감 및 유저 등록 (여기까지 오면 성공 확정)
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])

return "SUCCESS"