package com.booster.storage.redis.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DistributedLockAopTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @InjectMocks
    private DistributedLockAop distributedLockAop;

    @Test
    @DisplayName("락 획득 성공 시: 락 획득 -> 비즈니스 로직 실행 -> 락 해제 순서로 동작한다")
    void lock_success() throws Throwable {
        // given
        String key = "test:1";
        DistributedLock annotation = createAnnotation(key);
        Method method = this.getClass().getMethod("testMethod", Long.class);

        // Mock 설정
        given(joinPoint.getSignature()).willReturn(signature);
        given(signature.getMethod()).willReturn(method);
        given(signature.getParameterNames()).willReturn(new String[]{"id"});
        given(joinPoint.getArgs()).willReturn(new Object[]{1L}); // SpEL 파싱용 인자

        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(true); // 락 획득 성공
        given(joinPoint.proceed()).willReturn("Success"); // 로직 실행 성공
        given(rLock.isHeldByCurrentThread()).willReturn(true); // 내가 락을 잡고 있음

        // when
        Object result = distributedLockAop.lock(joinPoint);

        // then
        assertThat(result).isEqualTo("Success");

        // 검증: 락 획득 시도가 있었는가?
        verify(rLock).tryLock(annotation.waitTime(), annotation.leaseTime(), annotation.timeUnit());
        // 검증: 비즈니스 로직이 실행되었는가?
        verify(joinPoint).proceed();
        // 검증: 락 해제가 되었는가?
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("락 획득 실패 시: 예외가 발생하고 비즈니스 로직은 실행되지 않아야 한다")
    void lock_fail() throws Throwable {
        // given
        String key = "test:1";
        Method method = this.getClass().getMethod("testMethod", Long.class);

        given(joinPoint.getSignature()).willReturn(signature);
        given(signature.getMethod()).willReturn(method);
        given(signature.getParameterNames()).willReturn(new String[]{"id"});
        given(joinPoint.getArgs()).willReturn(new Object[]{1L});

        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(false); // 락 획득 실패

        // when & then
        assertThatThrownBy(() -> distributedLockAop.lock(joinPoint))
                .isInstanceOf(RuntimeException.class) // 커스텀 예외로 변경했다면 그걸로 검증
                .hasMessage("현재 요청량이 많아 처리가 지연되고 있습니다.");

        // 검증: 비즈니스 로직은 실행되면 안됨
        verify(joinPoint, times(0)).proceed();
    }

    @Test
    @DisplayName("비즈니스 로직 예외 발생 시: 예외가 터져도 락은 반드시 해제되어야 한다")
    void lock_exception() throws Throwable {
        // given
        String key = "test:1";
        Method method = this.getClass().getMethod("testMethod", Long.class);

        given(joinPoint.getSignature()).willReturn(signature);
        given(signature.getMethod()).willReturn(method);
        given(signature.getParameterNames()).willReturn(new String[]{"id"});
        given(joinPoint.getArgs()).willReturn(new Object[]{1L});

        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(true); // 락 획득 성공
        given(joinPoint.proceed()).willThrow(new IllegalArgumentException("비즈니스 예외")); // 로직 에러
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        // when & then
        assertThatThrownBy(() -> distributedLockAop.lock(joinPoint))
                .isInstanceOf(IllegalArgumentException.class);

        // 검증: 예외가 터졌어도 락 해제는 호출되었는가?
        verify(rLock).unlock();
    }

    // --- Helper Methods ---

    // 테스트용 가짜 메서드 (Reflection용)
    @DistributedLock(key = "'test:' + #id")
    public void testMethod(Long id) {}

    // 어노테이션 객체를 직접 생성하기 어려우므로 리플렉션으로 가져옴
    private DistributedLock createAnnotation(String key) throws NoSuchMethodException {
        return this.getClass().getMethod("testMethod", Long.class).getAnnotation(DistributedLock.class);
    }
}