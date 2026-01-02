# 📦 Common Module Design Record

이 문서는 프로젝트 전반에서 공유되는 순수 자바 기반의 유틸리티와 공통 도메인 모델을 관리하는 `common` 모듈의 설계 내용을 기록합니다.

## 📅 Update History
| 날짜 | 수정자 | 내용 |      상태       |
|:---:|:---:|:---|:-------------:|
| 2026-01-02 | Juhong Kim | 초기 유틸리티(DateUtils) 구현 및 확장 계획 수립 | `IN-PROGRESS` |

---

## Key Technical Decisions

### 1. 도메인 독립적 설계
* **원칙**: 특정 프레임워크(Spring 등)에 의존하지 않는 순수 자바 로직 위주로 구성합니다.
* **현황**: `DateUtils`를 통해 프로젝트 내 시간 계산 로직을 표준화하였습니다.

### 2. 향후 확장 계획 (Next Development)
대규모 트래픽 및 고가용성 환경을 위해 아래 기능을 순차적으로 추가할 예정입니다.
* **`utils` 확장**: `JsonUtils` (Jackson 기반 직렬화 유틸), `StringValidator` 등 추가.
* **`exception`**: 서비스 전반에서 공통으로 정의할 수 있는 비즈니스 예외 계층 정의.
* **`domain`**: 여러 모듈에서 공통으로 사용되는 Value Object(VO)나 열거형(Enum) 관리.
* **`security`**: 암호화/복호화 유틸리티 (유저 개인정보 보호용).

---

## 📂 Module Structure Analysis
```text
libs/common
└── src/main/java/com/booster/common
    └── DateUtils (날짜/시간 관련 공통 유틸리티)