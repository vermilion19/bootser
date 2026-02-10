# Vueroid Web API - 포트폴리오 분석 리포트

> 분석 대상: 차량 IoT 디바이스 관리 플랫폼 백엔드 API 서버
> 기술 스택: Spring Boot 2.7.6 / Java 17 / PostgreSQL / Redis / AWS
> 프로젝트 규모: Java 파일 489개 / 서비스 69개 / 리포지토리 88개 / 엔티티 50+개

---

## 1. 프로젝트 개요

차량용 블랙박스(Vueroid) 디바이스의 등록, 관리, 영상 스트리밍, 결제, 통계 등을 처리하는 REST API 서버.
웹 대시보드와 모바일 앱(Android/iOS) 양쪽에 API를 제공하며, 다중 환경(local/test/dev/stage/prod) 배포를 지원한다.

### 패키지 구조

```
com.ncn.vc/
├── common/          # 공통 Enum, JWT, 응답 VO
├── config/          # Spring 설정 (AWS, Firebase, Swagger, Async, CORS 등)
├── controller/      # REST 컨트롤러 44개 (api / app / fileview / pay / exception)
├── domain/          # JPA 엔티티 50+개 (BaseEntity 상속, Auditing 적용)
├── dto/             # DTO 100+개 (요청/응답 분리)
├── service/         # 비즈니스 로직 69개
├── repository/      # JPA + QueryDSL + MyBatis Mapper 88개
├── interceptor/     # Rate Limiting, Token 검증 인터셉터
├── handler/         # Redis Pub/Sub 핸들러
├── util/            # JWT 디코더, 보안, SSE, 날짜, GPS 유틸
└── exception/       # 커스텀 예외 + ErrorCode 인터페이스
```

---

## 2. 포트폴리오 강점 항목

### 2-1. JWT 인증 시스템 + 소셜 로그인 + 2FA

| 항목 | 구현 내용 |
|------|-----------|
| 토큰 전략 | Access Token(HS512) + Refresh Token 이중 발급 |
| 토큰 저장 | DB 저장 + 버전(UUID) 기반 토큰 무효화 |
| 소셜 로그인 | Google OAuth2, Apple Sign-In(JWT 디코딩), Facebook |
| 2차 인증 | 이메일 기반 2FA 코드 검증 |
| 비밀번호 보안 | RSA 비대칭키로 전송 구간 암호화 → 서버에서 복호화 후 BCrypt 비교 |
| 로그인 제한 | 5회 실패 시 30초 잠금, 시도 횟수 DB 추적 |
| 중복 로그인 감지 | Redis Pub/Sub `newLogin` 채널로 실시간 브로드캐스트 |
| Refresh Token | HttpOnly + Secure + SameSite=Lax 쿠키로 전달 (CSRF 방어) |

**인증 플로우:**

```
Client → POST /auth/token (RSA 암호화된 비밀번호)
  → AuthService.authenticate() (RSA 복호화 → BCrypt 검증)
    → 로그인 시도 횟수 체크 → 계정 상태 확인
    → TokenProvider.generateToken() (HS512 서명)
    → UaTokenService.save() (DB 저장)
    → Redis Pub/Sub "newLogin" 이벤트 발행
  → ResponseCookie(refreshToken) + Body(accessToken)
```

### 2-2. Redis 활용 (캐싱 / Pub·Sub / Rate Limiting)

| 용도 | 구현 |
|------|------|
| Rate Limiting | `PasswordSearchInterceptor` - IP 기반 5회/3분 제한, Redis 카운터 사용 |
| Pub/Sub | 로그인 이벤트를 `newLogin` 채널로 발행 → `RedisSubscriber`에서 수신 |
| SSE 연동 | `SSEEmitters`로 클라이언트에 실시간 이벤트 푸시 (count, eventAlert) |
| 세션 관리 | Spring Session Redis 통합 |

### 2-3. AWS 클라우드 인프라 연동

| 서비스 | 용도 |
|--------|------|
| S3 | 파일 업로드/다운로드, Presigned URL 생성 (30분 만료), 폴더 기반 정리 |
| SES | HTML 템플릿 이메일 발송 (회원가입 인증, 비밀번호 찾기 등) |
| EC2 | 2대 서버 HA 구성 |
| ALB | Application Load Balancer + Target Group 헬스체크 |
| KMS | 키 관리 (암호화) |

### 2-4. CI/CD 파이프라인 (무중단 배포)

Jenkins 기반 4개 환경 파이프라인 구성:

```
JenkinsfileDev / JenkinsfileTest / JenkinsfileStage / JenkinsfileProd
```

**프로덕션 배포 플로우 (Blue-Green 방식):**

```
1. ALB Target Group에서 서버 A 제거 (Deregister)
2. Gradle Build → JAR 생성
3. SCP → Bastion Host → Target EC2로 배포
4. 서비스 시작 + Health Check 대기
5. ALB Target Group에 서버 A 재등록 (Register)
6. 서버 B에 대해 1~5 반복
```

### 2-5. 멀티 데이터 접근 전략

| 기술 | 사용 목적 |
|------|-----------|
| Spring Data JPA | 기본 CRUD, 엔티티 매핑, Auditing |
| QueryDSL | 동적 쿼리, 복잡한 조건 검색 |
| MyBatis | 통계/리포트 등 복잡한 네이티브 SQL |
| PostgreSQL AES256 | DB 레벨 GPS 좌표 암호화 (`decrypt_number_aes256()`) |

### 2-6. 기타 기술 요소

| 항목 | 내용 |
|------|------|
| Push 알림 | Firebase(FCM) + APNS 멀티플랫폼 지원, 디바이스 토큰 관리 |
| 배치 처리 | Spring Batch - 휴면계정 처리, 개인정보 보호 배치 |
| SSE | Server-Sent Events로 브라우저 실시간 알림 |
| API 문서화 | Swagger/OpenAPI 4개 그룹 (API / APP / FileView / Pay) |
| 모니터링 | Spring Actuator + Prometheus 메트릭 연동 |
| JPA Auditing | `BaseEntity`에 `@CreatedDate`, `@LastModifiedDate` 자동 기록 |
| 글로벌 예외 처리 | `@ControllerAdvice` 기반 통합 에러 핸들링 (커스텀 코드 401~412) |
| 비동기 처리 | `@EnableAsync` 설정, 비차단 작업 실행 |
| 암호화 이중화 | RSA(전송 구간) + AES(저장 데이터) + DB 레벨 암호화 3중 구조 |

---

## 3. 약점 분석 (면접 대비 필수)

### 3-1. 코드 품질

| 문제 | 위치 | 설명 |
|------|------|------|
| 컨트롤러 비대화 | `AuthController.java` 350줄 | 비즈니스 로직이 컨트롤러에 과도하게 포함 |
| 문자열 비교 오류 | `AuthController:93` | `ipAdd != ""` → `!ipAdd.isEmpty()` 사용해야 함 |
| 참조 비교 오류 | `AuthController:180` | `== "Y"` → `.equals("Y")` 사용해야 함 |
| 레거시 HTTP 클라이언트 | `AuthController:231` | `HttpURLConnection` 직접 사용 → RestTemplate/WebClient 사용 권장 |
| `throws Exception` 남용 | 다수 메서드 | 구체적 예외 타입 명시 필요 |

### 3-2. 테스트 커버리지

- 테스트 파일 26개 / 소스 파일 489개 = **약 5%**
- 단위 테스트 거의 없음, 통합 테스트 위주
- 인증 플로우 등 핵심 로직에 대한 테스트 부재

### 3-3. 보안 취약점

| 항목 | 문제 | 개선 방향 |
|------|------|-----------|
| CORS | `allowedOrigins("*")` 전체 허용 | 프로덕션 도메인만 화이트리스트 |
| Apple 토큰 | 서명 검증 없이 Base64 디코딩만 수행 | Apple 공개키로 JWT 서명 검증 필수 |
| AES 모드 | ECB 모드 사용 (패턴 노출 위험) | CBC 또는 GCM 모드로 전환 |
| 시크릿 키 관리 | 설정 파일에 하드코딩 | AWS Secrets Manager 또는 환경변수 분리 |

### 3-4. 아키텍처 한계

- DDD, 헥사고날 등 고급 아키텍처 패턴 미적용
- 서비스 간 높은 결합도 (AuthController에 10개 이상 의존성 주입)
- 이벤트 기반 아키텍처가 Redis Pub/Sub 수준에서 머무름
- 도메인 로직과 인프라 로직이 분리되지 않음

---

## 4. 종합 평가

| 항목 | 점수 | 비고 |
|------|------|------|
| 프로젝트 규모 | ★★★★☆ | 489개 파일, 실무 운영 수준 |
| 기술 스택 다양성 | ★★★★☆ | Spring, Redis, AWS, JWT, QueryDSL, Firebase |
| 아키텍처 깊이 | ★★☆☆☆ | 기본 레이어드 아키텍처, 고급 패턴 부재 |
| 코드 품질 | ★★☆☆☆ | 버그, 컨벤션 미준수, 리팩토링 필요 |
| 테스트 | ★☆☆☆☆ | 커버리지 5% 미만 |
| 인프라/DevOps | ★★★☆☆ | Jenkins + AWS 무중단 배포 구성 |
| 보안 | ★★★☆☆ | RSA+AES+DB 3중 암호화는 좋으나 취약점 존재 |

---

## 5. 포트폴리오 강화 전략 (대기업 타겟)

> 핵심 전략: **"레거시를 분석하고 개선한 경험"**은 대기업에서 높게 평가하는 역량이다.
> 코드를 처음부터 잘 짜는 것보다, **문제를 인식하고 개선하는 능력**을 보여줄 것.

### 우선순위별 개선 항목

**P0 - 즉시 효과 (면접 스토리 직결)**

| 순서 | 항목 | 기대 효과 |
|------|------|-----------|
| 1 | AuthController 리팩토링 → Strategy 패턴으로 소셜 로그인 추상화 | "설계 패턴 적용 경험" 어필 |
| 2 | 인증 플로우 단위 테스트 + 통합 테스트 추가 | "테스트 문화 인식" 어필 (이것만 해도 차별화) |
| 3 | Apple 토큰 서명 검증 구현 | "보안 취약점을 발견하고 개선했다" 스토리 |

**P1 - 기술 깊이 강화**

| 순서 | 항목 | 기대 효과 |
|------|------|-----------|
| 4 | AES-ECB → AES-GCM 전환 + IV 적용 | 암호화 이해도 증명 |
| 5 | Docker + GitHub Actions CI/CD 전환 | 모던 DevOps 역량 |
| 6 | 커스텀 예외 체계 정비 + ErrorCode Enum 통합 | 에러 핸들링 설계 능력 |

**P2 - 아키텍처 레벨업**

| 순서 | 항목 | 기대 효과 |
|------|------|-----------|
| 7 | 도메인 이벤트 도입 (Spring ApplicationEvent) | 이벤트 기반 설계 이해 |
| 8 | 헥사고날 아키텍처 일부 적용 (Port & Adapter) | 아키텍처 역량 어필 |
| 9 | Spring REST Docs 기반 API 문서 자동화 | 문서화 자동화 경험 |
