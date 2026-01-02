# 🌐 Core-Web Module Design Record

이 문서는 시스템 전반의 공통 응답 규격과 전역 예외 처리 메커니즘을 담당하는 `core-web` 모듈의 설계 내용을 기록합니다.

## 📅 Update History
| 날짜 | 수정자 | 내용 | 상태 |
|:---:|:---:|:---|:---:|
| 2026-01-02 | Juhong Kim | 초기 패키지 구조 설계 및 응답/예외 처리 로직 구현 | `COMPLETED` ✅ |

---

##  Key Technical Decisions

### 1. 전역 응답 규격화 (`response` 패키지)
* **`ApiResponse`**: API 성공/실패 시 공통적으로 반환될 표준 포맷입니다. (Record 활용 권장)
* **`ResultType`**: 응답의 성공 여부나 내부 상태를 정의하는 Enum으로, 단순 HTTP 상태 코드를 넘어선 세밀한 상태 표현을 담당합니다.
* **사유**: 클라이언트(Front-end)와의 일관된 통신 규약을 유지하여 협업 효율을 극대화합니다.

### 2. 예외 처리 아키텍처 (`exception` 패키지)
* **`ErrorCode` (Interface)**: 도메인별 다양한 에러 코드를 추상화하기 위한 인터페이스입니다.
* **`CommonErrorCode` (Enum)**: 시스템 전반에서 발생하는 공통 에러(예: 400, 500 계열)를 정의합니다.
* **`CoreException`**: 비즈니스 로직에서 던질 최상위 사용자 정의 예외입니다.
* **`GlobalExceptionHandler`**: `@RestControllerAdvice`를 통해 발생한 모든 예외를 낚아채 표준화된 `ApiResponse` 형태로 변환합니다.

### 3. 웹 자동 설정 (`config` 패키지)
* **`CoreWebConfig`**: Jackson 직렬화 설정이나 Interceptor 등 웹 관련 공통 빈(Bean) 설정을 관리합니다.

---

## 📂 Module Structure Analysis
```text
core-web
└── src/main/java/com/booster/core/web
    ├── config
    │   └── CoreWebConfig
    ├── exception
    │   ├── CommonErrorCode (Enum)
    │   ├── CoreException
    │   ├── ErrorCode (Interface)
    │   └── GlobalExceptionHandler
    └── response
        ├── ApiResponse
        └── ResultType (Enum)