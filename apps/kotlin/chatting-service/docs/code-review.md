# Code Review - chatting-service

분석 일시: 2026-02-26

---

## 요약

| 심각도 | 건수 |
|--------|------|
| 🔴 Critical | 1 |
| 🟠 High | 3 |
| 🟡 Medium | 2 |
| 🔵 Low | 2 |

---

## 🔴 Critical

### [C-1] ~~TALK 메시지 방 멤버십 검증 없음~~ ✅ 수정 완료 (2026-02-26)

**위치**: `application/ChatService.kt`

**수정 내용**: `when` 블록에 `Type.TALK` 분기 추가, 미입장 방으로의 발송 차단.

```kotlin
// 변경 전: else -> {} 로 TALK가 검증 없이 통과
else -> {}

// 변경 후: TALK 전용 멤버십 검증
Type.TALK -> {
    if (rooms[message.roomId]?.contains(message.userId) != true) {
        log.warn("[REJECT] 미입장 방 TALK 차단: userId={}, roomId={}", message.userId, message.roomId)
        return
    }
}
```

`ENTER` 없이 임의의 `roomId`로 TALK를 보내면 즉시 드롭됨.
히스토리 오염, seq 카운터 조작, Redis 불필요한 부하 모두 차단.

---

## 🟠 High

### [H-1] ~~Redis 히스토리/시퀀스 키에 TTL 없음~~ ✅ 수정 완료 (2026-02-26)

**위치**: `application/ChatService.kt`

**수정 내용**: `unsubscribeFromRoom` (방의 마지막 유저 퇴장 시점)에 Redis 키 삭제 추가.

```kotlin
private fun unsubscribeFromRoom(roomId: String) {
    roomSubscriptions.remove(roomId)?.dispose()
    redisTemplate.delete(historyKey(roomId), seqKey(roomId))  // 추가
        .doOnError { e -> log.error("[REDIS] 방 키 삭제 실패: roomId={}, {}", roomId, e.message) }
        .subscribe()
    log.debug("[UNSUB] 방 구독 해제 및 키 삭제: roomId={}", roomId)
}
```

마지막 유저가 퇴장할 때 `chat.history.{roomId}`, `chat.seq.{roomId}` 두 키를 한 번에 삭제.
기존 fire-and-forget(`.subscribe()`) 패턴 유지.

---

### [H-2] ~~Redis 실패 시 seq=0 폴백으로 재연결 복구 오동작~~ ✅ 수정 완료 (2026-02-26)

**위치**: `application/ChatService.kt:202`

**수정 내용**: `?: 0L` 폴백 제거, `IllegalStateException` throw로 변경.
예외는 `publishToRedis`의 기존 catch 블록에서 처리되어 해당 메시지 발행을 중단하고 에러 로그 기록.

```kotlin
// 변경 전
.awaitSingleOrNull() ?: 0L

// 변경 후
.awaitSingleOrNull()
    ?: throw IllegalStateException("Redis seq increment returned null: roomId=${message.roomId}")
```

---

### [H-3] ~~SessionRegistry에 TTL 없음~~ ✅ 수정 완료 (2026-02-26)

**위치**: `infrastructure/SessionRegistryService.kt`

**수정 내용**: Redis Hash(`session:registry`) → Redis String + TTL 방식으로 전환.

```kotlin
// 변경 전: Hash (필드 단위 TTL 불가)
redisTemplate.opsForHash<String, String>().put("session:registry", userId, instanceId)

// 변경 후: String + TTL 1시간
redisTemplate.opsForValue().set("session:user:$userId", instanceId, Duration.ofHours(1))
```

서버 크래시 시 `unregister` 미호출이 발생해도 `SESSION_TTL(1시간)` 이후 키가 자동 만료됨.
정상 종료 시에는 `unregister`에서 `delete`로 즉시 제거.

---

## 🟡 Medium

### [M-1] `LoadTester.kt`이 `src/main`에 위치

**위치**: `src/main/kotlin/.../test/LoadTester.kt`

**문제**: 부하 테스트 도구가 프로덕션 클래스패스에 포함됨.
`loadTesterJar` Gradle 태스크로 별도 JAR를 뽑지만,
기본 빌드 산출물(`bootJar`)에도 클래스 파일이 포함되어 패키지 크기 증가.

**개선 방향**: `src/test`로 이동하거나 별도 `:tools:load-tester` 모듈로 분리.

---

### [M-2] ~~`TestConfig.kt`의 `runBlocking` in Reactor 파이프라인~~ ✅ 수정 완료 (2026-02-26)

**위치**: `test/kotlin/.../TestConfig.kt`

**수정 내용**: `runBlocking` → `mono(Dispatchers.Default) { }` 교체.
`thenAnswer`에서 `Mono<Long>`을 직접 반환하도록 변경하여, `.awaitSingleOrNull()` 구독 시점에
`Dispatchers.Default`에서 비동기 실행됨. 이벤트 루프 스레드 블로킹 제거.

```kotlin
// 변경 전
kotlinx.coroutines.runBlocking {
    chatService.broadcastToLocalUsers(message)
}
Mono.just(1L)

// 변경 후
mono(Dispatchers.Default) {
    try {
        val message = mapper.readValue<ChatMessage>(json)
        chatService.broadcastToLocalUsers(message)
    } catch (_: Exception) {}
    1L
}
```

---

## 🔵 Low

### [L-1] ~~`subscribeToRoomIfNeeded` 로그 항상 출력~~ ✅ 수정 완료 (2026-02-26)

**위치**: `application/ChatService.kt`

**수정 내용**: `computeIfAbsent` 외부의 로그를 람다 내부 첫 줄로 이동.
실제로 새 구독이 생성될 때만 "[SUB] 방 구독 시작" 로그가 출력됨.

```kotlin
// 변경 전: 구독 여부와 무관하게 항상 출력
roomSubscriptions.computeIfAbsent(roomId) { /* 구독 */ }
log.debug("[SUB] 방 구독: roomId={}", roomId)

// 변경 후: 신규 구독 시에만 출력
roomSubscriptions.computeIfAbsent(roomId) {
    log.debug("[SUB] 방 구독 시작: roomId={}", roomId)
    /* 구독 */
}
```

---

### [L-2] JWT 인증 미구현 (TODO)

**위치**: `web/ChatWebSocketHandler.kt:89-96`

```kotlin
// TODO: [Phase 1 미구현] JWT 인증으로 교체 필요
return UriComponentsBuilder.fromUri(session.handshakeInfo.uri)
    .queryParams.getFirst("userId")
```

**문제**: `userId`를 Query Param으로 신뢰 → 누구든 타 유저 ID로 접속 가능.
이슈 [C-1]의 영향이 더 커지는 근본 원인이기도 함.

**개선 방향**: `?token=<JWT>` → `JwtProvider.extractUserId(token)` → `sub` 클레임에서 userId 추출.