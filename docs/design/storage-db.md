# 🗄️ Storage-DB Module Design Record

이 문서는 시스템의 영속성 계층(RDB)을 담당하며, JPA 및 QueryDSL 설정을 관리하는 `storage-db` 모듈의 설계 내용을 기록합니다.

## 📅 Update History
| 날짜 | 수정자 | 내용 | 상태 |
|:---:|:---:|:---|:---:|
| 2026-01-02 | Juhong Kim | JPA/QueryDSL 설정 및 도메인 공통 엔티티 설계 | `COMPLETED` ✅ |

---

##  Key Technical Decisions

### 1. Persistence 설정 (`config` 패키지)
* **`JpaConfig`**: JPA Auditing 활성화 및 엔티티 매니저 설정을 담당합니다.
* **`QueryDslConfig`**: 대규모 트래픽 환경에서 복잡한 동적 쿼리를 타입 안정성 있게 작성하기 위해 `JPAQueryFactory`를 빈(Bean)으로 등록하여 관리합니다.
* **사유**: 설정 클래스를 명확히 분리함으로써 각 프레임워크의 의존성을 독립적으로 관리하고 확장성을 확보합니다.

### 2. 도메인 공통 모델 (`core` 패키지)
* **`BaseEntity`**: 모든 데이터베이스 테이블의 공통 필드(예: `createdAt`, `updatedAt`)를 관리하는 최상위 클래스입니다.
* **사유**: `@MappedSuperclass`와 JPA Auditing을 사용하여 데이터의 생성/수정 시간을 자동화하고 코드 중복을 제거합니다.

---

## 📂 Module Structure Analysis
```text
storage-db
└── src/main/java/com/booster/storage/db
    ├── config
    │   ├── JpaConfig
    │   └── QueryDslConfig
    └── core
        └── BaseEntity