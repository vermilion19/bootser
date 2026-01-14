# Booster 프로젝트 버그 분석 리포트

> 분석 날짜: 2026-01-14

## 요약

| # | 심각도 | 컴포넌트 | 이슈 | 상태 |
|---|--------|----------|------|------|
| 1 | ~~MEDIUM~~ | ~~RestaurantRepository~~ | ~~decreaseOccupancy 불필요한 WHERE 조건~~ | ✅ 수정완료 |
| 2 | ~~HIGH~~ | ~~RestaurantEventListener~~ | ~~예외 무시 처리~~ | ✅ 수정완료 |
| 3 | ~~HIGH~~ | ~~RestaurantCacheService~~ | ~~폴백 메서드 오버로딩 모호성~~ | ✅ 수정완료 |
| 4 | MEDIUM | WaitingEventProducer | topic 프로퍼티 null 체크 누락 | 🔴 미수정 |
| 5 | MEDIUM | Waiting 엔티티 | cancel() 상태 검증 불완전 | 🔴 미수정 |

---

## ✅ 수정완료

### ~~BUG #1: RestaurantRepository.decreaseOccupancy의 불필요한 WHERE 조건~~
- 위치: `apps/restaurant-service/.../RestaurantRepository.java`
- 수정: WHERE 조건 제거

### ~~BUG #2: RestaurantEventListener Kafka 리스너의 예외 처리 누락~~
- 위치: `apps/restaurant-service/.../RestaurantEventListener.java`
- 수정: catch 블록에서 `throw e;` 추가

### ~~BUG #3: RestaurantCacheService의 불완전한 Bulkhead 폴백~~
- 위치: `apps/waiting-service/.../RestaurantCacheService.java`
- 수정: 두 폴백 메서드를 하나로 통합, `instanceof`로 분기 처리

---

## 🔴 미수정

### BUG #4: MEDIUM - WaitingEventProducer의 잠재적 NullPointerException

#### 위치
- `apps/waiting-service/src/main/java/com/booster/waitingservice/waiting/application/WaitingEventProducer.java` (Line 20-23)

#### 현재 코드
```java
@Value("${app.kafka.topics.waiting-events}")
private String topic;

public void send(WaitingEvent event) {
    log.info("🚀 [Kafka] 이벤트 발행: type={}, waitingId={}", event.type(), event.waitingId());
    kafkaTemplate.send(topic, event);  // topic이 null일 수 있음
}
```

#### 문제점
- `app.kafka.topics.waiting-events` 프로퍼티가 application.yml에 설정되지 않은 경우
- `@Value`가 `null`을 주입함 (기본값 미지정 시)
- `kafkaTemplate.send(null, event)` 호출 시 `NullPointerException` 또는 `IllegalArgumentException` 발생

#### 수정 방안
```java
@Value("${app.kafka.topics.waiting-events:booster.waiting.events}")
private String topic;  // 기본값 제공
```

---

### BUG #5: MEDIUM - Waiting.cancel() 메서드의 불완전한 검증

#### 위치
- `apps/waiting-service/src/main/java/com/booster/waitingservice/waiting/domain/Waiting.java` (Line 68-73)

#### 현재 코드
```java
public void cancel() {
    if (this.status == WaitingStatus.ENTERED) {
        throw new IllegalStateException("이미 입장한 손님은 취소할 수 없습니다.");
    }
    this.status = WaitingStatus.CANCELED;
}
```

#### 문제점
- `CALLED` 상태에서의 취소를 허용함 (ENTERED만 차단)
- 논리적으로 호출된(CALLED) 손님은 취소할 수 없어야 함
- 정상 시퀀스: WAITING → (선택적 CALLED) → ENTERED
- CALLED 후 취소는 비즈니스적으로 맞지 않음
- 추가 이슈: `CANCELED` 상태 체크 없음 - cancel 두 번 호출해도 그냥 다시 설정됨

#### 수정 방안
```java
public void cancel() {
    if (this.status == WaitingStatus.ENTERED) {
        throw new IllegalStateException("이미 입장한 손님은 취소할 수 없습니다.");
    }
    if (this.status == WaitingStatus.CANCELED) {
        throw new IllegalStateException("이미 취소된 대기입니다.");
    }
    // 선택적: CALLED 상태 체크
    // if (this.status == WaitingStatus.CALLED) {
    //     throw new IllegalStateException("호출 상태의 대기는 취소할 수 없습니다.");
    // }
    this.status = WaitingStatus.CANCELED;
}
```

---

## 권장 수정 우선순위

1. **BUG #4** - topic 프로퍼티 기본값 추가
2. **BUG #5** - cancel() 상태 검증 보완

---

## 추가 권장 사항

1. **동시성 테스트 추가**: 분산 환경 시나리오에 대한 동시성 테스트 작성
2. **설정 검증**: 필수 설정값에 대한 애플리케이션 시작 시 검증 추가
3. **DLQ 설정**: Kafka Dead Letter Queue 설정으로 실패 메시지 관리
4. **모니터링**: 위 버그들과 관련된 메트릭/알람 설정
