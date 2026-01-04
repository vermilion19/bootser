package com.booster.storage.redis.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    // 락의 키 값 (SpEL 지원, 예: "#request.restaurantId")
    String key();

    // 락의 시간 단위
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    // 락 획득을 위해 대기하는 시간 (기본 5초)
    long waitTime() default 5L;

    // 락을 임대하는 시간 (기본 3초)
    long leaseTime() default 3L;
}
