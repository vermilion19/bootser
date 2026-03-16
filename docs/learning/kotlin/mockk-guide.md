# MockK 핵심 키워드 사용법

Kotlin 테스트에서 사용하는 MockK 라이브러리의 주요 키워드 정리.

---

## `every` — stub 설정

호출 시 무엇을 반환할지 정의한다.

```kotlin
// 기본: 특정 값 반환
every { inventoryRepository.findByVariantId(1L) } returns inventory

// any() : 어떤 인자든 매칭
every { inventoryRepository.save(any()) } returns inventory

// null 반환
every { cartRepository.findByUserId(1L) } returns null

// 예외 던지기
every { productRepository.findById(99L) } throws RuntimeException("DB 오류")

// answers : 람다로 동적 반환 (인자를 그대로 돌려줄 때 자주 씀)
every { cartRepository.save(any()) } answers { firstArg() }
//                                             ↑ save(cart) 호출 시 cart 그대로 반환
```

---

## `verify` — 호출 검증

실제로 해당 메서드가 호출됐는지 확인한다.

```kotlin
// 정확히 1번 호출됐는지
verify(exactly = 1) { inventoryHistoryRepository.save(any()) }

// 한 번도 호출 안 됐는지
verify(exactly = 0) { cartRepository.save(any()) }

// 최소 1번 이상
verify(atLeast = 1) { cartRepository.findByUserId(any()) }

// 순서까지 검증
verifyOrder {
    inventoryRepository.findById(1L)
    inventoryHistoryRepository.save(any())
}
```

> `verify`는 **행동 검증** — "이 메서드가 호출됐어야 한다"
> `shouldBe`는 **상태 검증** — "이 값이 맞아야 한다"
> 둘을 함께 쓰는 게 좋은 테스트

---

## `slot` — 저장된 인자 캡처 후 내부 검증

`verify(exactly = 1) { save(any()) }` 는 "호출됐다"만 알 수 있다.
**저장된 객체의 내부 값**까지 검증하고 싶을 때 `slot`을 사용한다.

```kotlin
val historySlot = slot<InventoryHistory>()

every { inventoryHistoryRepository.save(capture(historySlot)) } answers { firstArg() }
//                                       ↑ save 호출 시 인자를 slot에 캡처

service.adjust(command)

// slot에서 꺼내서 내부 값 검증
historySlot.captured.changeAmount shouldBe 5
historySlot.captured.reason shouldBe InventoryHistory.ChangeReason.RESTOCK
```

> 여러 번 호출될 때는 `slot` 대신 `mutableListOf<T>()` + `captureAll` 사용.

---

## `mockk` — mock 객체 생성

```kotlin
// 기본: 모든 호출에 명시적 stub 필요 (없으면 에러)
val repo = mockk<CartRepository>()

// relaxed: stub 없으면 기본값 반환 (0, null, emptyList 등)
val repo = mockk<CartRepository>(relaxed = true)

// relaxUnitFun: Unit 반환 메서드만 자동 처리
val item = mockk<CartItem>(relaxUnitFun = true)
// → item.softDelete() 를 stub 없이 호출 가능
```

---

## `spyk` — 실제 객체의 일부만 stub

```kotlin
// 실제 객체를 감싸서 일부 메서드만 override
val realCart = Cart.create(userId = 1L)
val spyCart = spyk(realCart)

every { spyCart.findActiveItems() } returns emptyList()
// 나머지 메서드는 실제 Cart 동작 그대로 유지
```

---

## 키워드 한눈에 보기

| 키워드 | 역할 | 언제 쓰는가 |
|--------|------|-------------|
| `every { } returns` | 반환값 지정 | 항상 필요 |
| `every { } answers { }` | 동적 반환 (firstArg 등) | 저장 후 그대로 반환할 때 |
| `every { } throws` | 예외 던지기 | 예외 경로 테스트 |
| `verify` | 호출 여부/횟수 검증 | 중요한 부수 효과 확인 |
| `slot` + `capture` | 인자 캡처 후 내부 검증 | save된 객체 내용 확인 |
| `mockk(relaxed)` | 자동 기본값 반환 | stub 덜 써도 될 때 |
| `spyk` | 실제 객체 일부만 stub | 대부분의 실제 동작은 유지할 때 |