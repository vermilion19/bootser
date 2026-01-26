# core-webflux

Spring WebFlux 기반 애플리케이션을 위한 공통 모듈입니다.

## 의존성 추가

```groovy
dependencies {
    implementation project(':libs:core-webflux')
}
```

## 제공 기능

### 1. 통합 API 응답 (`ApiResponse`)

모든 API 응답을 일관된 형식으로 반환합니다.

```java
// 응답 형식
{
    "result": "SUCCESS" | "ERROR",
    "data": { ... },
    "message": "에러 메시지 (에러 시)",
    "errorCode": "C001 (에러 시)"
}
```

#### @RestController 방식

```java
@RestController
public class ChatController {

    @GetMapping("/rooms/{id}")
    public Mono<ApiResponse<ChatRoom>> getRoom(@PathVariable Long id) {
        return chatService.findById(id)
                .map(ApiResponse::success);
    }

    @PostMapping("/rooms")
    public Mono<ApiResponse<ChatRoom>> createRoom(@RequestBody CreateRoomRequest request) {
        return chatService.create(request)
                .map(ApiResponse::success);
    }
}
```

#### RouterFunction 방식

```java
@Configuration
public class ChatRouter {

    @Bean
    public RouterFunction<ServerResponse> chatRoutes(ChatHandler handler) {
        return RouterFunctions.route()
                .GET("/rooms/{id}", handler::getRoom)
                .POST("/rooms", handler::createRoom)
                .build();
    }
}

@Component
public class ChatHandler {

    public Mono<ServerResponse> getRoom(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return chatService.findById(id)
                .flatMap(ApiResponse::ok);  // 200 OK
    }

    public Mono<ServerResponse> createRoom(ServerRequest request) {
        return request.bodyToMono(CreateRoomRequest.class)
                .flatMap(chatService::create)
                .flatMap(ApiResponse::created);  // 201 Created
    }
}
```

#### ApiResponse 헬퍼 메서드

| 메서드 | HTTP Status | 설명 |
|--------|-------------|------|
| `ApiResponse.ok(data)` | 200 | 성공 응답 (데이터 포함) |
| `ApiResponse.ok()` | 200 | 성공 응답 (데이터 없음) |
| `ApiResponse.created(data)` | 201 | 생성 성공 응답 |
| `ApiResponse.errorResponse(errorCode)` | ErrorCode.status | 에러 응답 |
| `ApiResponse.errorResponse(status, message)` | status | 에러 응답 |

### 2. 예외 처리

#### ErrorCode 인터페이스

도메인별 에러코드를 정의할 때 구현합니다.

```java
@Getter
@AllArgsConstructor
public enum ChatErrorCode implements ErrorCode {
    ROOM_NOT_FOUND(404, "CHAT-001", "채팅방을 찾을 수 없습니다."),
    ROOM_FULL(400, "CHAT-002", "채팅방 정원이 초과되었습니다."),
    ALREADY_JOINED(409, "CHAT-003", "이미 참여 중인 채팅방입니다.");

    private final int status;
    private final String code;
    private final String message;
}
```

#### CoreException

비즈니스 예외를 던질 때 사용합니다.

```java
public Mono<ChatRoom> findById(Long id) {
    return chatRoomRepository.findById(id)
            .switchIfEmpty(Mono.error(
                new CoreException(ChatErrorCode.ROOM_NOT_FOUND)
            ));
}

// 커스텀 메시지와 함께
throw new CoreException(ChatErrorCode.ROOM_NOT_FOUND, "ID: " + id + " 채팅방을 찾을 수 없습니다.");
```

#### 공통 에러코드 (CommonErrorCode)

| 코드 | HTTP Status | 설명 |
|------|-------------|------|
| `INVALID_INPUT_VALUE` | 400 | 잘못된 입력값 |
| `RESOURCE_NOT_FOUND` | 404 | 리소스 없음 |
| `UNAUTHORIZED` | 401 | 인증 필요 |
| `FORBIDDEN` | 403 | 접근 권한 없음 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 |

### 3. 전역 예외 핸들러 (ReactiveExceptionHandler)

`@RestControllerAdvice` 기반으로 다음 예외를 자동 처리합니다:

| 예외 | HTTP Status | 설명 |
|------|-------------|------|
| `CoreException` | ErrorCode.status | 비즈니스 예외 |
| `WebExchangeBindException` | 400 | Validation 실패 |
| `ServerWebInputException` | 400 | 요청 파싱 오류 |
| `Exception` | 500 | 기타 예외 |

## 모듈 구조

```
libs/core-webflux/
├── build.gradle
├── README.md
└── src/main/
    ├── java/com/booster/core/webflux/
    │   ├── config/
    │   │   └── CoreWebFluxConfig.java       # 자동 설정
    │   ├── exception/
    │   │   ├── ErrorCode.java               # 에러코드 인터페이스
    │   │   ├── CommonErrorCode.java         # 공통 에러코드
    │   │   ├── CoreException.java           # 커스텀 예외
    │   │   └── ReactiveExceptionHandler.java # 전역 예외 핸들러
    │   └── response/
    │       ├── ResultType.java              # SUCCESS, ERROR
    │       └── ApiResponse.java             # 통합 응답 DTO
    └── resources/META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## core-web vs core-webflux

| 항목 | core-web (MVC) | core-webflux (Reactive) |
|------|----------------|-------------------------|
| 의존성 | spring-boot-starter-web | spring-boot-starter-webflux |
| 스레드 모델 | Thread-per-request | Event Loop (Non-blocking) |
| 반환 타입 | `T`, `ResponseEntity<T>` | `Mono<T>`, `Flux<T>` |
| Validation 예외 | `MethodArgumentNotValidException` | `WebExchangeBindException` |
| 파싱 예외 | `HttpMessageNotReadableException` | `ServerWebInputException` |
| 적합한 용도 | 일반 REST API | 고성능/실시간 (SSE, WebSocket) |

## 주의사항

1. **MVC와 혼용 금지**: `spring-boot-starter-web`과 `spring-boot-starter-webflux`를 동시에 사용하면 MVC가 우선 적용됩니다.

2. **블로킹 코드 주의**: WebFlux에서 블로킹 호출(JDBC, Thread.sleep 등)은 성능 저하를 유발합니다. R2DBC, WebClient 등 논블로킹 API를 사용하세요.

3. **RouterFunction 예외 처리**: RouterFunction 방식에서 전역 예외 처리가 필요하면 `WebExceptionHandler`를 별도로 구현해야 합니다.
