# 인증 흐름 (Authentication Flow)

## 개요

이 문서는 Google OAuth2 기반 로그인부터 서비스 접근까지의 전체 데이터 흐름을 설명합니다.

---

## 1. 전체 아키텍처

```
┌─────────────┐     ┌─────────────┐     ┌─────────────────┐
│   Frontend  │────▶│   Gateway   │────▶│  auth-service   │
│  (Browser)  │     │   (6000)    │     │     (8080)      │
└─────────────┘     └─────────────┘     └─────────────────┘
       │                   │                     │
       │                   │                     ▼
       │                   │            ┌─────────────────┐
       │                   │            │  Google OAuth   │
       │                   │            │    Server       │
       │                   │            └─────────────────┘
       │                   │
       │                   ▼
       │           ┌─────────────────┐
       │           │  d-day-service  │
       │           │     (8080)      │
       └──────────▶└─────────────────┘
```

---

## 2. 회원가입 & 로그인 흐름 (Google OAuth2)

### 2.1 시퀀스 다이어그램

```
┌────────┐          ┌─────────┐          ┌──────────────┐          ┌────────────┐          ┌────────┐
│Browser │          │ Gateway │          │ auth-service │          │Google OAuth│          │   DB   │
└───┬────┘          └────┬────┘          └──────┬───────┘          └─────┬──────┘          └───┬────┘
    │                    │                      │                        │                     │
    │ 1. GET /auth/v1/login/google              │                        │                     │
    │───────────────────▶│                      │                        │                     │
    │                    │──────────────────────▶                        │                     │
    │                    │   (라우팅)            │                        │                     │
    │                    │                      │                        │                     │
    │◀──────────────────────────────────────────│                        │                     │
    │  { loginUrl: "/oauth2/authorization/google" }                      │                     │
    │                    │                      │                        │                     │
    │ 2. GET /oauth2/authorization/google       │                        │                     │
    │───────────────────▶│──────────────────────▶                        │                     │
    │                    │                      │                        │                     │
    │◀──────────────────────────────────────────│                        │                     │
    │  302 Redirect to Google Login Page        │                        │                     │
    │                    │                      │                        │                     │
    │ 3. 사용자가 Google 로그인 수행             │                        │                     │
    │──────────────────────────────────────────────────────────────────▶│                     │
    │                    │                      │                        │                     │
    │ 4. Google이 Callback URL로 리다이렉트 (Authorization Code 포함)    │                     │
    │◀──────────────────────────────────────────────────────────────────│                     │
    │                    │                      │                        │                     │
    │ 5. GET /login/oauth2/code/google?code=xxx │                        │                     │
    │───────────────────▶│──────────────────────▶                        │                     │
    │                    │                      │                        │                     │
    │                    │                      │ 6. Authorization Code로 │                     │
    │                    │                      │    Access Token 요청    │                     │
    │                    │                      │───────────────────────▶│                     │
    │                    │                      │◀───────────────────────│                     │
    │                    │                      │   Access Token 반환     │                     │
    │                    │                      │                        │                     │
    │                    │                      │ 7. Access Token으로     │                     │
    │                    │                      │    사용자 정보 요청     │                     │
    │                    │                      │───────────────────────▶│                     │
    │                    │                      │◀───────────────────────│                     │
    │                    │                      │  { email, name, sub }   │                     │
    │                    │                      │                        │                     │
    │                    │                      │ 8. 사용자 조회/생성                           │
    │                    │                      │─────────────────────────────────────────────▶│
    │                    │                      │◀─────────────────────────────────────────────│
    │                    │                      │                        │                     │
    │                    │                      │ 9. JWT 생성 & Cookie 설정                    │
    │                    │                      │                        │                     │
    │◀──────────────────────────────────────────│                        │                     │
    │  302 Redirect + Set-Cookie: access_token=JWT; HttpOnly; Secure     │                     │
    │                    │                      │                        │                     │
└───┴────┘          └────┴────┘          └──────┴───────┘          └─────┴──────┘          └───┴────┘
```

### 2.2 단계별 설명

#### Step 1-2: 로그인 URL 요청
```
GET /auth/v1/login/google
Response: { "loginUrl": "/oauth2/authorization/google" }
```

Frontend는 이 URL로 사용자를 리다이렉트합니다.

#### Step 3-4: Google 로그인
- 사용자가 Google 계정으로 로그인
- Google이 `Authorization Code`를 포함하여 Callback URL로 리다이렉트

#### Step 5-7: Token 교환 & 사용자 정보 획득
- auth-service가 Authorization Code로 Google Access Token 획득
- Access Token으로 Google 사용자 정보 조회 (email, name, sub)

#### Step 8: 사용자 조회/생성
```java
// AuthService.processOAuthLogin()
Optional<User> existingUser = userRepository.findByOauthProviderAndOauthId(GOOGLE, oauthId);

if (existingUser.isPresent()) {
    // 기존 사용자: 프로필 업데이트
    user.updateProfile(name);
} else {
    // 신규 사용자: 생성
    User newUser = User.createGoogleUser(email, name, oauthId);
    userRepository.save(newUser);
}
```

#### Step 9: JWT 생성 & Cookie 설정
```java
// OAuth2LoginSuccessHandler
String jwt = tokenProvider.createToken(user);

ResponseCookie cookie = ResponseCookie.from("access_token", jwt)
    .httpOnly(true)      // JavaScript 접근 불가 (XSS 방어)
    .secure(true)        // HTTPS에서만 전송
    .sameSite("Lax")     // CSRF 기본 방어
    .path("/")
    .maxAge(3600)        // 1시간
    .build();

response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
response.sendRedirect(redirectUri);  // Frontend로 리다이렉트
```

---

## 3. JWT 토큰 구조

### 3.1 Payload (Claims)

```json
{
  "sub": "123456789012345678",      // User ID (Snowflake)
  "email": "user@gmail.com",
  "name": "홍길동",
  "role": "ROLE_USER",              // ROLE_USER 또는 ROLE_ADMIN
  "access_services": ["d-day"],     // 접근 가능한 서비스 목록
  "oauth_provider": "GOOGLE",
  "iat": 1706000000,                // 발급 시간
  "exp": 1706003600                 // 만료 시간 (1시간 후)
}
```

### 3.2 서비스 접근 권한 (access_services)

| 서비스 코드 | 설명 | 경로 패턴 |
|------------|------|----------|
| `d-day` | D-Day 서비스 | `/api/v1/special-days/**` |
| `diary` | 다이어리 서비스 (예정) | `/api/v1/diary/**` |
| `waiting` | 웨이팅 서비스 | `/waitings/**` |
| `restaurant` | 레스토랑 서비스 | `/restaurants/**` |

신규 가입 시 기본값: `["d-day"]`

---

## 4. 서비스 접근 흐름 (인증된 요청)

### 4.1 시퀀스 다이어그램

```
┌────────┐          ┌─────────┐          ┌──────────────┐
│Browser │          │ Gateway │          │ d-day-service│
└───┬────┘          └────┬────┘          └──────┬───────┘
    │                    │                      │
    │ 1. GET /api/v1/special-days/today         │
    │    Cookie: access_token=eyJhbG...         │
    │───────────────────▶│                      │
    │                    │                      │
    │                    │ 2. JWT 검증           │
    │                    │    - 서명 확인        │
    │                    │    - 만료 확인        │
    │                    │    - access_services │
    │                    │      검증 ("d-day")   │
    │                    │                      │
    │                    │ 3. 요청 전달 (헤더 추가)
    │                    │    X-User-Id: 123...  │
    │                    │    X-User-Role: ROLE_USER
    │                    │    X-User-Email: user@gmail.com
    │                    │─────────────────────▶│
    │                    │                      │
    │                    │                      │ 4. 비즈니스 로직 처리
    │                    │                      │    @CurrentMemberId로
    │                    │                      │    X-User-Id 추출
    │                    │                      │
    │                    │◀─────────────────────│
    │                    │   Response Body       │
    │◀───────────────────│                      │
    │   Response Body    │                      │
└───┴────┘          └────┴────┘          └──────┴───────┘
```

### 4.2 Gateway JWT 검증 로직

```java
// JwtAuthorizationFilter.java
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = request.getPath().value();

    // 1. 제외 경로 확인 (/auth/**, /oauth2/**, /actuator/**)
    if (isExcludedPath(path)) {
        return chain.filter(exchange);
    }

    // 2. Cookie에서 JWT 추출
    String token = extractTokenFromCookie(request);  // "access_token" 쿠키
    if (token == null) {
        return onError(exchange, "No access_token cookie", UNAUTHORIZED);
    }

    // 3. JWT 파싱 및 검증
    Claims claims = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();

    // 4. access_services 검증
    String requiredService = getRequiredService(path);  // 예: "d-day"
    if (!hasServiceAccess(claims, requiredService)) {
        return onError(exchange, "Access denied", FORBIDDEN);
    }

    // 5. 다운스트림 서비스로 헤더 전달
    ServerHttpRequest modifiedRequest = request.mutate()
        .header("X-User-Id", claims.getSubject())
        .header("X-User-Role", claims.get("role", String.class))
        .header("X-User-Email", claims.get("email", String.class))
        .build();

    return chain.filter(exchange.mutate().request(modifiedRequest).build());
}
```

### 4.3 다운스트림 서비스에서 사용자 정보 추출

```java
// d-day-service: CurrentMemberIdResolver.java
@Override
public Object resolveArgument(...) {
    String userIdHeader = webRequest.getHeader("X-User-Id");

    if (userIdHeader == null || userIdHeader.isBlank()) {
        if (annotation.required()) {
            throw new SpecialDayException(UNAUTHORIZED);
        }
        return null;
    }

    return Long.parseLong(userIdHeader);
}

// Controller에서 사용
@GetMapping("/my")
public List<SpecialDay> getMySpecialDays(@CurrentMemberId Long memberId) {
    return specialDayService.findByMemberId(memberId);
}
```

---

## 5. 로그아웃 흐름

### 5.1 시퀀스 다이어그램

```
┌────────┐          ┌─────────┐          ┌──────────────┐
│Browser │          │ Gateway │          │ auth-service │
└───┬────┘          └────┬────┘          └──────┬───────┘
    │                    │                      │
    │ 1. POST /auth/v1/logout                   │
    │───────────────────▶│──────────────────────▶
    │                    │                      │
    │◀──────────────────────────────────────────│
    │  Set-Cookie: access_token=; Max-Age=0     │
    │                    │                      │
    │ 2. 쿠키 삭제됨      │                      │
    │                    │                      │
└───┴────┘          └────┴────┘          └──────┴───────┘
```

### 5.2 로그아웃 API

```java
// AuthController.java
@PostMapping("/logout")
public ResponseEntity<Void> logout() {
    ResponseCookie cookie = ResponseCookie.from("access_token", "")
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite("Lax")
        .path("/")
        .maxAge(0)  // 즉시 만료
        .build();

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .build();
}
```

---

## 6. 인증 실패 케이스

### 6.1 HTTP 상태 코드

| 상황 | 상태 코드 | 설명 |
|------|----------|------|
| 쿠키 없음 | `401 Unauthorized` | access_token 쿠키가 없음 |
| 토큰 만료 | `401 Unauthorized` | JWT 만료됨 |
| 토큰 위조 | `401 Unauthorized` | 서명 검증 실패 |
| 서비스 접근 권한 없음 | `403 Forbidden` | access_services에 해당 서비스 없음 |

### 6.2 에러 응답 예시

```json
// 401 Unauthorized
{
  "timestamp": "2026-02-04T10:00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "No access_token cookie"
}

// 403 Forbidden
{
  "timestamp": "2026-02-04T10:00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied to service: diary"
}
```

---

## 7. 보안 고려사항

### 7.1 Cookie 보안 설정

| 속성 | 값 | 목적 |
|------|-----|------|
| `HttpOnly` | true | XSS 공격 방어 (JavaScript 접근 차단) |
| `Secure` | true (운영) | HTTPS에서만 전송 |
| `SameSite` | Lax | CSRF 기본 방어 |
| `Path` | / | 모든 경로에서 쿠키 전송 |

### 7.2 추가 보안 권장사항

1. **HTTPS 필수**: 운영 환경에서는 반드시 HTTPS 사용
2. **Refresh Token**: 현재 구현에는 없으나, 장기 세션 유지가 필요하면 추가 고려
3. **Token Rotation**: 보안 강화를 위해 주기적 토큰 갱신 고려
4. **Rate Limiting**: 로그인 시도 제한 추가 권장

---

## 8. 환경 변수 설정

### auth-service

```yaml
GOOGLE_CLIENT_ID: your-google-client-id
GOOGLE_CLIENT_SECRET: your-google-client-secret
JWT_SECRET: base64-encoded-secret-key
OAUTH_REDIRECT_URI: http://localhost:3000  # Frontend URL
COOKIE_SECURE: false  # 운영에서는 true
```

### gateway-service

```yaml
JWT_SECRET: base64-encoded-secret-key  # auth-service와 동일
```

---

## 9. 테스트 방법

### 9.1 로그인 테스트

```bash
# 1. Google 로그인 URL 확인
curl http://localhost:6000/auth/v1/login/google
# Response: {"loginUrl":"/oauth2/authorization/google"}

# 2. 브라우저에서 로그인 수행
# http://localhost:6000/oauth2/authorization/google 접속

# 3. 로그인 후 쿠키 확인 (브라우저 DevTools > Application > Cookies)
```

### 9.2 인증된 API 호출 테스트

```bash
# Cookie로 API 호출
curl --cookie "access_token=eyJhbG..." \
     http://localhost:6000/api/v1/special-days/today

# 또는 브라우저에서 직접 호출 (쿠키 자동 전송)
```

### 9.3 직접 서비스 호출 테스트 (개발용)

```bash
# Gateway 없이 d-day-service 직접 호출
curl -H "X-User-Id: 123456789" \
     -H "X-User-Role: ROLE_USER" \
     http://localhost:8080/api/v1/special-days/today
```
