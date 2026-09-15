package com.booster.dday.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARCHITECTURE §10-4 를 닫는 테스트 — <b>{@code @Scheduled} 가 가상 스레드를 타는가.</b>
 *
 * <p>설계가 이것을 「확인 필요」로 열어 둔 까닭이 §5.7 에 있다. 스케줄 작업이
 * 여덟인데 Spring 의 기본 {@code TaskScheduler} 풀 크기는 <b>1</b> 이다.
 *
 * <blockquote>
 * 공휴일 동기화(수십 분)가 그 하나를 붙들면 <b>Outbox 릴레이가 수십 분 멈춘다.</b>
 * D-4 알림이 수십 분 늦고, 로그에는 아무 에러도 안 찍힌다.
 * </blockquote>
 *
 * <p><b>확인 결과: 탄다.</b> Spring Boot 4.0.1 의
 * {@code TaskSchedulingConfigurations.TaskSchedulerConfiguration} 은
 * {@code @ConditionalOnThreading(VIRTUAL)} 인 {@code taskScheduler} 빈을 갖고 있고,
 * {@code spring.threads.virtual.enabled: true} 면 그쪽이 뜬다. 그것은 스케줄마다
 * 가상 스레드를 새로 뽑으므로 <b>풀 크기라는 개념 자체가 없다.</b>
 *
 * <p>그래도 {@code spring.task.scheduling.pool.size} 를 설정에 남겨 둔다. 아래
 * 두 번째 테스트가 그 까닭이다 — <b>가상 스레드를 끄면 함정이 그대로 돌아온다.</b>
 * 그때 기본값 1 로 돌아가지 않게 하는 것이 그 줄이다.
 *
 * <p>이 테스트는 스프링 부트를 올리지 않는다. 자동 설정 하나만 떼어 돌리므로
 * DB 도 Redis 도 필요 없고, <b>부트 버전을 올릴 때 이 결론이 그대로인지</b>가
 * 바로 여기서 깨진다.
 */
class SchedulerThreadingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(SchedulingEnabled.class);

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingEnabled {
    }

    @Test
    @DisplayName("가상 스레드가 켜져 있으면 스케줄러에 풀이 없다 — 동기화가 릴레이를 못 막는다")
    void virtualThreadsRemoveThePool() {
        runner.withPropertyValues("spring.threads.virtual.enabled=true")
                .run(context -> assertThat(context.getBean(TaskScheduler.class))
                        .as("SimpleAsyncTaskScheduler 는 스케줄마다 스레드를 새로 뽑는다")
                        .isInstanceOf(SimpleAsyncTaskScheduler.class));
    }

    @Test
    @DisplayName("가상 스레드를 끄면 풀로 돌아간다 — 그래서 풀 크기 설정을 지우지 않는다")
    void withoutVirtualThreadsThePoolIsBackAndMustBeSized() {
        runner.withPropertyValues(
                        "spring.threads.virtual.enabled=false",
                        "spring.task.scheduling.pool.size=10")
                .run(context -> {
                    TaskScheduler scheduler = context.getBean(TaskScheduler.class);
                    assertThat(scheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
                    assertThat(corePoolSizeOf(scheduler))
                            .as("스케줄 작업이 여덟이다 (§5.6 + SCHEMA §10)")
                            .isGreaterThanOrEqualTo(8);
                });
    }

    /**
     * 이 테스트가 §5.7 이 걱정한 고장을 <b>수로 보여 준다.</b> 아무것도 안 하면
     * 풀이 1 이고, 그 하나를 긴 작업이 붙들면 나머지 일곱이 통째로 멈춘다.
     */
    @Test
    @DisplayName("아무것도 안 하면 풀이 1 이다 — 그것이 §5.7 이 말한 조용한 함정이다")
    void defaultPoolIsOne() {
        runner.withPropertyValues("spring.threads.virtual.enabled=false")
                .run(context -> assertThat(corePoolSizeOf(context.getBean(TaskScheduler.class)))
                        .isEqualTo(1));
    }

    /**
     * {@code getPoolSize()} 가 아니라 코어 크기를 본다.
     *
     * <p>{@code ThreadPoolTaskScheduler.getPoolSize()} 는 <b>지금 살아 있는 스레드
     * 수</b>라서, 아직 아무 일도 안 시킨 스케줄러에서는 설정과 무관하게 0 이다.
     * 우리가 묻는 것은 「몇 개까지 동시에 돌 수 있나」이고 그것은 코어 크기다.
     */
    private static int corePoolSizeOf(TaskScheduler scheduler) {
        return ((ThreadPoolTaskScheduler) scheduler).getScheduledThreadPoolExecutor().getCorePoolSize();
    }
}
