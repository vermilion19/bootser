# JPA & Hibernate 어노테이션 완벽 가이드

> 작성일: 2026-02-11
> 대상: Spring Boot 4.0.1, Hibernate 6.x, JPA 3.1

이 문서는 JPA 표준 어노테이션과 Hibernate 전용 어노테이션을 실무 관점에서 정리한 종합 가이드입니다.

---

## 목차

1. [기본 JPA 어노테이션](#1-기본-jpa-어노테이션)
2. [연관관계 어노테이션](#2-연관관계-어노테이션)
3. [Hibernate 전용 어노테이션](#3-hibernate-전용-어노테이션)
4. [Spring Data JPA 어노테이션](#4-spring-data-jpa-어노테이션)
5. [성능 최적화 어노테이션](#5-성능-최적화-어노테이션)
6. [캐싱 관련 어노테이션](#6-캐싱-관련-어노테이션)
7. [실무 사용 패턴과 주의사항](#7-실무-사용-패턴과-주의사항)

---

## 1. 기본 JPA 어노테이션

### 1.1 @Entity

**설명**: 클래스가 JPA 엔티티임을 나타내는 필수 어노테이션

**사용 시점**: 데이터베이스 테이블과 매핑되는 모든 도메인 객체

**예제**:
```java
@Entity
@Table(name = "users") // 테이블명 지정 (선택)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
}
```

**주의사항**:
- 기본 생성자(no-args constructor) 필수 → `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 권장
- `final` 클래스, `final` 필드 사용 불가
- 엔티티명 기본값은 클래스명이며, `@Entity(name = "CustomName")`으로 변경 가능

---

### 1.2 @Table

**설명**: 엔티티가 매핑될 데이터베이스 테이블 정보를 지정

**사용 시점**: 테이블명이 클래스명과 다를 때, 스키마/인덱스/유니크 제약 지정 시

**예제**:
```java
@Entity
@Table(
    name = "tb_user",
    schema = "public",
    uniqueConstraints = @UniqueConstraint(columnNames = {"email"}),
    indexes = {
        @Index(name = "idx_username", columnList = "username"),
        @Index(name = "idx_created_at", columnList = "created_at")
    }
)
public class User { }
```

**주의사항**:
- DDL 자동 생성 시에만 인덱스/제약 조건이 반영됨
- 운영 환경에서는 Flyway/Liquibase 등 마이그레이션 도구 사용 권장

---

### 1.3 @Id

**설명**: 엔티티의 기본 키(Primary Key) 필드를 지정

**사용 시점**: 모든 엔티티에 필수

**예제**:
```java
@Entity
public class Product {
    @Id
    private String productCode; // 직접 할당

    private String name;
}
```

**주의사항**:
- 엔티티는 반드시 하나의 `@Id`를 가져야 함
- 복합 키는 `@EmbeddedId` 또는 `@IdClass` 사용

---

### 1.4 @GeneratedValue

**설명**: 기본 키 생성 전략을 지정

**사용 시점**: 데이터베이스가 자동으로 ID를 생성하도록 할 때

**전략 비교**:

| 전략 | 설명 | 성능 | 지원 DB | 권장도 |
|------|------|------|---------|--------|
| `IDENTITY` | DB Auto Increment 사용 | 보통 | MySQL, PostgreSQL, SQL Server | ⭐⭐⭐ |
| `SEQUENCE` | DB 시퀀스 사용 | 좋음 | PostgreSQL, Oracle | ⭐⭐⭐⭐⭐ |
| `TABLE` | 별도 테이블로 시퀀스 관리 | 나쁨 | 모든 DB | ⭐ (권장 X) |
| `AUTO` | JPA가 DB에 맞게 자동 선택 | 가변 | 모든 DB | ⭐⭐⭐ |

**예제**:
```java
// IDENTITY (MySQL)
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

// SEQUENCE (PostgreSQL)
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
@SequenceGenerator(
    name = "user_seq",
    sequenceName = "user_id_seq",
    allocationSize = 50 // 성능 최적화: 한 번에 50개씩 미리 할당
)
private Long id;

// TABLE (권장하지 않음)
@Id
@GeneratedValue(strategy = GenerationType.TABLE, generator = "table_gen")
@TableGenerator(
    name = "table_gen",
    table = "id_generator",
    pkColumnName = "seq_name",
    valueColumnName = "seq_value"
)
private Long id;
```

**주의사항**:
- `IDENTITY`는 `INSERT` 쿼리를 즉시 실행해야 ID를 알 수 있음 → 배치 삽입 불가
- `SEQUENCE`가 성능상 가장 유리 (PostgreSQL 권장)
- `allocationSize`를 적절히 설정하면 DB 왕복 횟수 감소

---

### 1.5 @Column

**설명**: 필드를 테이블 컬럼에 매핑하고 제약 조건 지정

**사용 시점**: 컬럼명 변경, `nullable`, `unique`, 길이 제한 등 설정

**예제**:
```java
@Entity
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        name = "article_title",     // 컬럼명
        nullable = false,            // NOT NULL
        length = 200,                // VARCHAR(200)
        unique = true,               // UNIQUE 제약
        updatable = false,           // UPDATE 불가
        columnDefinition = "TEXT"    // 직접 DDL 지정
    )
    private String title;

    @Column(precision = 10, scale = 2) // DECIMAL(10,2)
    private BigDecimal price;
}
```

**주의사항**:
- `nullable = false`는 DDL 생성 시에만 적용됨 → 런타임 검증은 `@NotNull` 별도 사용
- `updatable = false`는 Hibernate가 UPDATE 쿼리에서 해당 컬럼을 제외
- `columnDefinition`은 DB 종속성 증가 → 가능하면 피하기

---

### 1.6 @Enumerated

**설명**: Enum 타입을 DB에 저장하는 방식 지정

**사용 시점**: Enum 필드를 엔티티에 사용할 때

**예제**:
```java
public enum UserRole {
    ADMIN, USER, GUEST
}

@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ❌ 나쁜 예: ORDINAL (기본값)
    @Enumerated(EnumType.ORDINAL) // 0, 1, 2로 저장
    private UserRole role;

    // ✅ 좋은 예: STRING
    @Enumerated(EnumType.STRING) // "ADMIN", "USER", "GUEST"로 저장
    private UserRole role;
}
```

**주의사항**:
- **절대 `ORDINAL` 사용 금지!** → Enum 순서 변경 시 기존 데이터 깨짐
- 항상 `EnumType.STRING` 사용 권장
- Enum 값이 변경되면 데이터 마이그레이션 필요

---

### 1.7 @Temporal

**설명**: 날짜/시간 타입의 매핑 방식 지정 (JPA 2.x 이전)

**사용 시점**: `java.util.Date`, `java.util.Calendar` 사용 시 (레거시)

**예제**:
```java
@Entity
public class Order {
    @Temporal(TemporalType.DATE)       // DATE (날짜만)
    private Date orderDate;

    @Temporal(TemporalType.TIME)       // TIME (시간만)
    private Date orderTime;

    @Temporal(TemporalType.TIMESTAMP)  // TIMESTAMP (날짜+시간)
    private Date createdAt;
}
```

**주의사항**:
- **Java 8+ 환경에서는 사용하지 않음**
- 대신 `LocalDate`, `LocalDateTime`, `Instant` 사용 (자동 매핑)
- 신규 프로젝트에서는 `@Temporal` 불필요

**현대적 방식**:
```java
@Entity
public class Order {
    private LocalDate orderDate;      // DATE
    private LocalTime orderTime;      // TIME
    private LocalDateTime createdAt;  // TIMESTAMP
    private Instant timestamp;        // TIMESTAMP WITH TIME ZONE
}
```

---

### 1.8 @Lob

**설명**: 대용량 데이터(CLOB, BLOB)를 저장

**사용 시점**: 큰 텍스트 또는 바이너리 데이터 저장

**예제**:
```java
@Entity
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(columnDefinition = "TEXT") // PostgreSQL
    private String content; // CLOB

    @Lob
    private byte[] fileData; // BLOB
}
```

**주의사항**:
- CLOB/BLOB은 성능 이슈 발생 가능 → 큰 파일은 별도 스토리지(S3 등) 사용 권장
- Lazy Loading 고려: `@Basic(fetch = FetchType.LAZY)`

---

### 1.9 @Transient

**설명**: JPA가 해당 필드를 무시하도록 지정

**사용 시점**: DB 컬럼과 매핑하지 않는 임시 필드

**예제**:
```java
@Entity
public class User {
    @Id
    private Long id;

    private String password;

    @Transient // DB에 저장하지 않음
    private String passwordConfirm;

    @Transient
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
```

**주의사항**:
- `static`, `transient` 키워드 필드는 자동으로 무시됨
- Jackson 직렬화 시에는 `@JsonIgnore`와 별도로 관리

---

## 2. 연관관계 어노테이션

### 2.1 @ManyToOne

**설명**: N:1 관계 (자식 → 부모)

**사용 시점**: 자식 엔티티가 하나의 부모를 참조할 때 (가장 흔한 관계)

**예제**:
```java
@Entity
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // ⚠️ LAZY 필수!
    @JoinColumn(name = "user_id") // FK 컬럼명
    private User user;
}

@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
}
```

**주의사항**:
- **기본 FetchType이 EAGER → 반드시 LAZY로 변경**
- FK는 Many 쪽에 존재
- `optional = false` 설정 시 NOT NULL 제약 추가

---

### 2.2 @OneToMany

**설명**: 1:N 관계 (부모 → 자식들)

**사용 시점**: 부모 엔티티가 여러 자식을 가질 때

**예제**:
```java
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ 양방향 관계 (권장)
    @OneToMany(
        mappedBy = "user",              // 연관 관계의 주인(Order.user)
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY          // 기본값이지만 명시 권장
    )
    private List<Order> orders = new ArrayList<>();

    // 편의 메서드
    public void addOrder(Order order) {
        orders.add(order);
        order.setUser(this);
    }
}

@Entity
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // 연관 관계의 주인 (FK 보유)
}
```

**단방향 OneToMany (비추천)**:
```java
@Entity
public class User {
    @OneToMany
    @JoinColumn(name = "user_id") // 중간 테이블 없이 FK 직접 지정
    private List<Order> orders = new ArrayList<>();
}
// ❌ 문제: Order 테이블에 FK가 있지만 User가 관리 → UPDATE 쿼리 추가 발생
```

**주의사항**:
- **양방향 관계 권장**, 단방향은 성능 이슈 발생
- `mappedBy`는 반대편 필드명 지정
- `orphanRemoval = true`: 부모에서 제거된 자식 자동 DELETE
- 편의 메서드로 양방향 동기화 필수

---

### 2.3 @OneToOne

**설명**: 1:1 관계

**사용 시점**: 엔티티와 부가 정보를 분리할 때 (예: User ↔ UserProfile)

**예제**:
```java
// 주 테이블(User)에 FK 보유 (권장)
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @JoinColumn(name = "profile_id", unique = true)
    private UserProfile profile;
}

@Entity
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bio;

    @OneToOne(mappedBy = "profile", fetch = FetchType.LAZY)
    private User user; // 양방향 (선택)
}
```

**Shared Primary Key (비추천)**:
```java
@Entity
public class UserProfile {
    @Id
    private Long id; // User의 ID와 동일

    @OneToOne
    @MapsId // User의 ID를 공유
    @JoinColumn(name = "id")
    private User user;
}
```

**주의사항**:
- **양방향 `@OneToOne`은 LAZY 로딩이 제대로 작동하지 않을 수 있음** (Hibernate 제약)
- 주 테이블에 FK 두는 것이 성능상 유리
- `unique = true` 제약 조건 필수

---

### 2.4 @ManyToMany

**설명**: N:M 관계 (중간 테이블 자동 생성)

**사용 시점**: 학생-강좌, 태그-포스트 등

**예제**:
```java
@Entity
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany
    @JoinTable(
        name = "student_course",
        joinColumns = @JoinColumn(name = "student_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    private Set<Course> courses = new HashSet<>();
}

@Entity
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany(mappedBy = "courses")
    private Set<Student> students = new HashSet<>();
}
```

**실무 권장: 중간 엔티티 생성**:
```java
// ✅ 확장 가능한 방식
@Entity
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    private LocalDateTime enrolledAt; // 추가 정보
    private String status;
}
```

**주의사항**:
- **실무에서는 중간 엔티티를 명시적으로 만들기 권장**
- 중간 테이블에 추가 컬럼이 필요하면 `@ManyToMany` 불가
- `List` 대신 `Set` 사용 권장 (중복 방지)

---

### 2.5 @JoinColumn

**설명**: 외래 키 컬럼을 명시적으로 지정

**사용 시점**: FK 컬럼명, 제약 조건 커스터마이징

**예제**:
```java
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",           // FK 컬럼명
        nullable = false,           // NOT NULL
        foreignKey = @ForeignKey(name = "fk_order_user") // FK 제약 조건명
    )
    private User user;
}
```

**주의사항**:
- 생략 시 기본값: `{필드명}_{참조 테이블 PK 컬럼명}`
- DDL 생성 시에만 제약 조건 적용됨

---

### 2.6 @JoinTable

**설명**: `@ManyToMany` 중간 테이블 설정

**사용 시점**: 중간 테이블 이름/컬럼 커스터마이징

**예제**:
```java
@ManyToMany
@JoinTable(
    name = "user_role",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id"),
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"})
)
private Set<Role> roles = new HashSet<>();
```

---

### 2.7 @MappedSuperclass

**설명**: 여러 엔티티에서 공통으로 사용하는 매핑 정보를 상속

**사용 시점**: `createdAt`, `updatedAt` 같은 공통 필드 관리

**예제**:
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class) // Spring Data JPA Auditing
public abstract class BaseEntity {
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;
}

@Entity
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    // createdAt, updatedAt 자동 상속
}
```

**주의사항**:
- `@MappedSuperclass` 자체는 엔티티가 아님 → 조회 불가
- `@Entity`와 혼용 불가
- Spring Data JPA의 `@CreatedDate`, `@LastModifiedDate`와 함께 사용 권장

---

### 2.8 @Embeddable / @Embedded

**설명**: 값 타입을 정의하고 엔티티에 포함

**사용 시점**: 주소, 금액 등 응집도 높은 값 타입

**예제**:
```java
@Embeddable
public class Address {
    private String city;
    private String street;
    private String zipcode;

    // 기본 생성자 필수
    protected Address() {}

    public Address(String city, String street, String zipcode) {
        this.city = city;
        this.street = street;
        this.zipcode = zipcode;
    }
}

@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private Address address;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "city", column = @Column(name = "work_city")),
        @AttributeOverride(name = "street", column = @Column(name = "work_street")),
        @AttributeOverride(name = "zipcode", column = @Column(name = "work_zipcode"))
    })
    private Address workAddress; // 같은 타입 재사용
}
```

**주의사항**:
- `@Embeddable` 클래스는 불변 객체로 설계 권장
- 같은 타입을 여러 번 사용하려면 `@AttributeOverrides` 필수
- 컬렉션으로 사용 시 `@ElementCollection` 활용

---

## 3. Hibernate 전용 어노테이션

### 3.1 @NaturalId

**설명**: 엔티티의 자연 키(비즈니스 키)를 정의

**사용 시점**: 기본 키 외에 유일하게 식별 가능한 속성이 있을 때 (예: ISBN, Email)

**예제**:
```java
@Entity
@Table(name = "books")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@NaturalIdCache // Natural ID 캐싱 활성화
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NaturalId
    @Column(nullable = false, unique = true)
    private String isbn; // 비즈니스 키

    private String title;
}

// 조회 예제
Book book = session.bySimpleNaturalId(Book.class)
                   .load("978-0134685991");
```

**성능 이점**:
- 2차 캐시 사용 시 Natural ID → PK 매핑을 캐싱
- DB 왕복 없이 엔티티 조회 가능

**주의사항**:
- `@NaturalIdCache`는 2차 캐시 활성화 시에만 의미 있음
- Natural ID는 불변 값 권장 (`mutable = false` 기본값)

---

### 3.2 @Formula

**설명**: SQL 표현식으로 가상 컬럼 정의

**사용 시점**: 계산된 값을 필드로 표현하고 싶을 때

**예제**:
```java
@Entity
public class Product {
    @Id
    private Long id;

    @Column(name = "base_price")
    private BigDecimal basePrice;

    @Column(name = "tax_rate")
    private BigDecimal taxRate;

    @Formula("base_price * (1 + tax_rate)") // 가상 컬럼
    private BigDecimal totalPrice; // DB에 저장되지 않음
}

// 서브쿼리 예제
@Entity
public class Customer {
    @Id
    private Long id;

    @Formula("(SELECT COUNT(*) FROM orders o WHERE o.customer_id = id)")
    private int orderCount;

    @Formula("(SELECT MIN(o.created_at) FROM orders o WHERE o.customer_id = id)")
    private LocalDateTime firstOrderDate;
}
```

**주의사항**:
- 읽기 전용 (INSERT/UPDATE 불가)
- 네이티브 SQL 사용 → DB 의존성 증가
- 복잡한 계산은 성능 저하 가능 → 인덱싱 불가

---

### 3.3 @Where / @SQLRestriction (Hibernate 6.3+)

**설명**: 엔티티 조회 시 자동으로 WHERE 조건 추가

**사용 시점**: Soft Delete, 다중 테넌트, 상태 필터링

**예제 (@Where - Hibernate 6.2 이하)**:
```java
@Entity
@Where(clause = "deleted = false")
public class User {
    @Id
    private Long id;

    private String username;

    @Column(nullable = false)
    private boolean deleted = false;
}

// SELECT * FROM users WHERE deleted = false (자동 추가)
```

**예제 (@SQLRestriction - Hibernate 6.3+)**:
```java
@Entity
@SQLRestriction("deleted_at IS NULL") // @Where 대체
public class Article {
    @Id
    private Long id;

    private String title;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

// 컬렉션에도 적용 가능
@Entity
public class Blog {
    @OneToMany(mappedBy = "blog")
    @SQLRestriction("status = 'PUBLISHED'")
    private List<Post> publishedPosts = new ArrayList<>();
}
```

**주의사항**:
- 조건을 비활성화할 수 없음 → 삭제된 데이터도 조회하려면 네이티브 쿼리 사용
- `@Filter`와 달리 항상 활성화됨

---

### 3.4 @Filter / @FilterDef

**설명**: 동적으로 활성화/비활성화 가능한 필터

**사용 시점**: 조건부로 데이터를 필터링하고 싶을 때 (다중 테넌트, 권한 기반 조회)

**예제**:
```java
@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = Long.class)
)
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Order {
    @Id
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    private String productName;
}

// 사용 예제
Session session = entityManager.unwrap(Session.class);
session.enableFilter("tenantFilter")
       .setParameter("tenantId", 123L);

List<Order> orders = orderRepository.findAll(); // tenant_id = 123 자동 적용

session.disableFilter("tenantFilter"); // 필터 해제
```

**주의사항**:
- `@SQLRestriction`과 달리 동적으로 제어 가능
- Session 레벨에서 관리 → 요청마다 활성화 필요
- Spring에서는 `@WebFilter` 또는 AOP로 자동화 가능

---

### 3.5 @BatchSize

**설명**: 컬렉션 조회 시 배치 페칭 크기 지정 (N+1 문제 완화)

**사용 시점**: `@OneToMany`, `@ManyToMany` Lazy Loading 시 성능 개선

**예제**:
```java
@Entity
public class User {
    @Id
    private Long id;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @BatchSize(size = 10) // 한 번에 10개의 User에 대한 Order를 IN 쿼리로 조회
    private List<Order> orders = new ArrayList<>();
}

// 100명의 User를 조회할 때
// ❌ @BatchSize 없으면: 1 + 100 (N+1 문제)
// ✅ @BatchSize(10) 사용: 1 + 10 (User 쿼리 1번 + Order 배치 쿼리 10번)
```

**글로벌 설정**:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100 # 전역 설정
```

**주의사항**:
- 근본적 해결은 `@EntityGraph` 또는 `Fetch Join` 사용
- `size`는 10~1000 사이 권장 (DB 성능에 따라 튜닝)

---

### 3.6 @DynamicInsert / @DynamicUpdate

**설명**: INSERT/UPDATE 시 변경된 컬럼만 쿼리에 포함

**사용 시점**: 컬럼이 많고 일부만 자주 변경되는 엔티티

**예제**:
```java
@Entity
@DynamicInsert // INSERT 시 null 아닌 필드만 포함
@DynamicUpdate // UPDATE 시 변경된 필드만 포함
public class User {
    @Id
    private Long id;

    private String username;
    private String email;
    private String phone;
    // ... 50개 컬럼
}

// @DynamicUpdate 없으면
// UPDATE users SET username=?, email=?, phone=?, ... (50개 컬럼 모두)

// @DynamicUpdate 사용 시
// UPDATE users SET email=? WHERE id=? (변경된 컬럼만)
```

**주의사항**:
- 쿼리 캐싱 불가 → 매번 새로운 쿼리 생성
- 컬럼이 적으면 오히려 성능 저하
- 기본적으로 비활성화된 이유: 대부분 경우 정적 쿼리가 더 빠름

---

### 3.7 @Immutable

**설명**: 엔티티를 읽기 전용으로 설정

**사용 시점**: 절대 변경되지 않는 마스터 데이터 (국가 코드, 카테고리 등)

**예제**:
```java
@Entity
@Immutable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class Country {
    @Id
    private String code;

    private String name;
}
```

**성능 이점**:
- Dirty Checking 스킵 → 성능 향상
- 2차 캐시와 함께 사용 시 효과적

**주의사항**:
- 수정 시도 시 무시됨 (예외 발생 X)
- DELETE는 가능

---

### 3.8 @CreationTimestamp / @UpdateTimestamp

**설명**: 엔티티 생성/수정 시각 자동 설정

**사용 시점**: Auditing 필드 관리

**예제**:
```java
@Entity
public class Article {
    @Id
    private Long id;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt; // INSERT 시 자동 설정

    @UpdateTimestamp
    private LocalDateTime updatedAt; // INSERT/UPDATE 시 자동 설정
}

// Hibernate 6.0+: DB에서 생성
@CreationTimestamp(source = SourceType.DB) // DB의 CURRENT_TIMESTAMP 사용
private Instant createdAt;
```

**Spring Data JPA 방식 (권장)**:
```java
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
public abstract class BaseEntity {
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

// Application.java
@EnableJpaAuditing
public class Application { }
```

**주의사항**:
- Hibernate 방식은 애플리케이션 시각 사용
- Spring Data JPA 방식이 더 유연 (생성자, 수정자 정보도 저장 가능)

---

### 3.9 @ColumnDefault

**설명**: 컬럼의 기본값 설정 (DDL에만 영향)

**사용 시점**: DB 레벨 기본값 지정

**예제**:
```java
@Entity
public class User {
    @Id
    private Long id;

    @ColumnDefault("true") // DDL: DEFAULT true
    private Boolean active;

    @ColumnDefault("0")
    private Integer loginCount;
}
```

**주의사항**:
- DDL 생성 시에만 적용됨
- Java 레벨 기본값은 별도 지정 필요
  ```java
  private Boolean active = true; // Java 기본값
  ```

---

### 3.10 @Comment

**설명**: 테이블/컬럼에 주석 추가 (DDL 생성 시)

**사용 시점**: DB 스키마 문서화

**예제**:
```java
@Entity
@Comment("사용자 정보 테이블")
public class User {
    @Id
    @Comment("사용자 고유 ID")
    private Long id;

    @Comment("사용자명 (로그인 ID)")
    private String username;
}
```

**주의사항**:
- DDL 생성 시에만 적용
- 일부 DB는 주석 미지원

---

### 3.11 @SoftDelete (Hibernate 6.4+)

**설명**: Soft Delete 네이티브 지원

**사용 시점**: 논리적 삭제 구현 (기존 `@Where` 대체)

**예제**:
```java
@Entity
@SoftDelete(columnName = "deleted", strategy = SoftDeleteType.ACTIVE) // deleted = true면 삭제로 간주
public class User {
    @Id
    private Long id;

    private String username;

    // deleted 컬럼 자동 관리
}

// 삭제 시
userRepository.delete(user); // UPDATE users SET deleted = true

// 조회 시
userRepository.findAll(); // WHERE deleted = false 자동 추가
```

**전략**:
- `SoftDeleteType.ACTIVE`: `true`면 활성, `false`면 삭제
- `SoftDeleteType.DELETED`: `true`면 삭제, `false`면 활성

**주의사항**:
- 삭제된 데이터 조회 시 네이티브 쿼리 필요
- `@SoftDelete` 사용 시 `@Where`/`@SQLRestriction` 불필요

---

### 3.12 @Cache (Second Level Cache)

**설명**: 2차 캐시 설정

**사용 시점**: 자주 읽고 거의 변경되지 않는 엔티티

**예제**:
```java
@Entity
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Category {
    @Id
    private Long id;

    private String name;

    @OneToMany(mappedBy = "category")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Product> products = new ArrayList<>();
}
```

**전략**:
- `READ_ONLY`: 읽기 전용 (가장 빠름)
- `NONSTRICT_READ_WRITE`: 약한 일관성 (성능 우선)
- `READ_WRITE`: 강한 일관성 (권장)
- `TRANSACTIONAL`: 트랜잭션 캐시 (JTA 필요)

**설정**:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          region.factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
      javax:
        cache:
          provider: org.ehcache.jsr107.EhcacheCachingProvider
```

**주의사항**:
- 컬렉션도 별도로 캐싱 설정 필요
- Redis 캐시와 중복 고려 (보통 2차 캐시 대신 Redis 사용 권장)

---

### 3.13 @OptimisticLocking / @Version

**설명**: 낙관적 락 전략

**사용 시점**: 동시성 제어 (충돌 감지)

**예제**:
```java
@Entity
@OptimisticLocking(type = OptimisticLockType.VERSION) // 기본값
public class Product {
    @Id
    private Long id;

    @Version // 버전 필드 (필수)
    private Long version;

    private String name;
    private Integer stock;
}

// 사용 예제
Product product = productRepository.findById(1L).orElseThrow();
product.setStock(product.getStock() - 1);
productRepository.save(product);
// UPDATE products SET stock=?, version=version+1 WHERE id=? AND version=?
// 버전 불일치 시 OptimisticLockException 발생
```

**전략**:
- `VERSION`: `@Version` 필드 사용 (권장)
- `ALL`: 모든 필드로 WHERE 조건 생성
- `DIRTY`: 변경된 필드만 WHERE 조건에 포함
- `NONE`: 낙관적 락 사용 안 함

**주의사항**:
- `@Version` 필드는 직접 수정 금지
- Dirty Read 문제는 해결 못함 (필요 시 Pessimistic Lock 사용)

---

### 3.14 @Fetch / @FetchProfile

**설명**: Fetch 전략 세밀 제어

**사용 시점**: `@EntityGraph`로 해결 안 되는 복잡한 Fetch 전략

**@Fetch 예제**:
```java
@Entity
public class User {
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Fetch(FetchMode.SELECT) // N+1 (기본값)
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT) // 서브쿼리로 한 번에 조회
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    @Fetch(FetchMode.JOIN) // Join Fetch (권장 X, @EntityGraph 사용)
    private List<Address> addresses = new ArrayList<>();
}
```

**@FetchProfile 예제**:
```java
@Entity
@FetchProfile(
    name = "user-with-orders",
    fetchOverrides = {
        @FetchOverride(
            entity = User.class,
            association = "orders",
            mode = FetchMode.JOIN
        )
    }
)
public class User {
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();
}

// 사용
Session session = entityManager.unwrap(Session.class);
session.enableFetchProfile("user-with-orders");
User user = session.get(User.class, 1L);
```

**주의사항**:
- **실무에서는 `@EntityGraph` 권장** (Spring Data JPA 지원)
- `FetchMode.JOIN`은 중복 결과 발생 가능 → `DISTINCT` 필요

---

## 4. Spring Data JPA 어노테이션

### 4.1 @Query

**설명**: 커스텀 JPQL/네이티브 쿼리 작성

**사용 시점**: 메서드명 쿼리로 표현 불가능한 복잡한 쿼리

**예제**:
```java
public interface UserRepository extends JpaRepository<User, Long> {

    // JPQL
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);

    // 네이티브 쿼리
    @Query(value = "SELECT * FROM users WHERE created_at > :date", nativeQuery = true)
    List<User> findRecentUsers(@Param("date") LocalDateTime date);

    // DTO 프로젝션
    @Query("SELECT new com.example.dto.UserDto(u.id, u.username) FROM User u")
    List<UserDto> findAllUserDtos();

    // 페이징
    @Query("SELECT u FROM User u WHERE u.active = true")
    Page<User> findActiveUsers(Pageable pageable);
}
```

**주의사항**:
- JPQL은 엔티티명 사용 (`User`), 네이티브는 테이블명 (`users`)
- 네이티브 쿼리는 DB 의존성 증가 → 최소화 권장
- DTO 프로젝션 시 생성자 필요

---

### 4.2 @Modifying

**설명**: `@Query`로 UPDATE/DELETE 쿼리 실행

**사용 시점**: 벌크 연산 (배치 업데이트)

**예제**:
```java
public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("UPDATE User u SET u.active = false WHERE u.lastLoginAt < :date")
    int deactivateInactiveUsers(@Param("date") LocalDateTime date);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId")
    void deleteCommentsByPostId(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true) // 영속성 컨텍스트 자동 초기화
    @Query("UPDATE Product p SET p.price = p.price * 1.1")
    int increasePrices();
}
```

**주의사항**:
- **반드시 `@Transactional` 필수**
- 벌크 연산은 영속성 컨텍스트를 거치지 않음 → 불일치 발생 가능
- `clearAutomatically = true` 또는 수동으로 `entityManager.clear()` 호출

---

### 4.3 @EntityGraph

**설명**: Fetch Join을 선언적으로 지정 (N+1 문제 해결)

**사용 시점**: 연관 엔티티를 한 번에 조회하고 싶을 때

**예제**:
```java
@Entity
@NamedEntityGraph(
    name = "User.withOrders",
    attributeNodes = @NamedAttributeNode("orders")
)
public class User {
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();
}

public interface UserRepository extends JpaRepository<User, Long> {

    // NamedEntityGraph 사용
    @EntityGraph("User.withOrders")
    List<User> findAll();

    // 인라인 정의
    @EntityGraph(attributePaths = {"orders", "profile"})
    Optional<User> findById(Long id);

    // 중첩 관계
    @EntityGraph(attributePaths = {"orders.items"})
    List<User> findByActive(boolean active);
}
```

**주의사항**:
- `attributePaths`는 점(`.`)으로 중첩 관계 표현
- 여러 컬렉션 동시 Fetch Join 시 `MultipleBagFetchException` 발생 가능
  - 해결: 하나는 `Set` 사용 또는 별도 쿼리로 분리

---

### 4.4 @Lock

**설명**: 비관적 락 설정

**사용 시점**: 동시성 제어가 중요한 경우 (재고 차감 등)

**예제**:
```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE) // SELECT ... FOR UPDATE
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ) // SELECT ... FOR SHARE (PostgreSQL)
    List<Product> findByCategory(String category);
}

// 사용 예제
@Transactional
public void decreaseStock(Long productId, int quantity) {
    Product product = productRepository.findByIdWithLock(productId)
        .orElseThrow();
    product.decreaseStock(quantity);
    // 트랜잭션 종료 시 락 해제
}
```

**락 모드**:
- `PESSIMISTIC_WRITE`: 배타적 락 (`FOR UPDATE`)
- `PESSIMISTIC_READ`: 공유 락 (`FOR SHARE`)
- `OPTIMISTIC`: 낙관적 락 (`@Version` 사용)
- `OPTIMISTIC_FORCE_INCREMENT`: 버전 강제 증가

**주의사항**:
- 데드락 주의 → 락 획득 순서 일관성 유지
- 타임아웃 설정 권장
  ```java
  @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "3000"))
  ```

---

### 4.5 @QueryHints

**설명**: JPA 쿼리 힌트 지정

**사용 시점**: 읽기 전용 쿼리, 캐시 제어, 타임아웃 설정

**예제**:
```java
public interface UserRepository extends JpaRepository<User, Long> {

    // 읽기 전용 (Dirty Checking 스킵)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<User> findAllReadOnly();

    // 캐시 제어
    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.cacheMode", value = "NORMAL")
    })
    List<User> findByActive(boolean active);

    // Fetch Size 설정
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "50"))
    @Query("SELECT u FROM User u")
    Stream<User> streamAll();

    // 쿼리 타임아웃 (밀리초)
    @QueryHints(@QueryHint(name = "javax.persistence.query.timeout", value = "3000"))
    List<User> findByUsername(String username);
}
```

**주요 힌트**:
- `org.hibernate.readOnly`: 읽기 전용 모드
- `org.hibernate.cacheable`: 쿼리 결과 캐싱
- `org.hibernate.fetchSize`: 한 번에 가져올 row 수
- `javax.persistence.query.timeout`: 타임아웃 (ms)
- `org.hibernate.comment`: 쿼리에 주석 추가 (디버깅용)

---

## 5. 성능 최적화 어노테이션

### 5.1 성능 최적화 체크리스트

| 문제 | 해결 방법 | 어노테이션 |
|------|-----------|-----------|
| N+1 문제 | Fetch Join | `@EntityGraph`, `@Fetch(SUBSELECT)` |
| 지연 로딩 최적화 | 배치 페칭 | `@BatchSize` |
| 불필요한 Dirty Checking | 읽기 전용 | `@QueryHints(readOnly)` |
| 과도한 컬럼 업데이트 | 동적 쿼리 | `@DynamicUpdate` |
| 중복 쿼리 | 2차 캐시 | `@Cache` |
| 동시성 이슈 | 락 | `@Lock`, `@Version` |

---

### 5.2 N+1 문제 해결 종합

**문제 상황**:
```java
// 100명의 User 조회
List<User> users = userRepository.findAll(); // 1번 쿼리

// 각 User의 Order 조회 (Lazy Loading)
for (User user : users) {
    System.out.println(user.getOrders().size()); // 100번 쿼리 (N+1)
}
```

**해결 방법**:

#### 1) @EntityGraph (권장)
```java
@EntityGraph(attributePaths = {"orders"})
List<User> findAll();
```

#### 2) JPQL Fetch Join
```java
@Query("SELECT u FROM User u JOIN FETCH u.orders")
List<User> findAllWithOrders();
```

#### 3) @BatchSize
```java
@OneToMany(mappedBy = "user")
@BatchSize(size = 10)
private List<Order> orders;
```

#### 4) @Fetch(SUBSELECT)
```java
@OneToMany(mappedBy = "user")
@Fetch(FetchMode.SUBSELECT)
private List<Order> orders;
```

---

## 6. 캐싱 관련 어노테이션

### 6.1 Spring Cache vs Hibernate 2nd Cache

| 구분 | Spring Cache (`@Cacheable`) | Hibernate 2nd Cache (`@Cache`) |
|------|----------------------------|-------------------------------|
| 레벨 | 서비스 레이어 | 영속성 레이어 |
| 대상 | 메서드 리턴 값 | 엔티티, 컬렉션 |
| 저장소 | Redis, EhCache 등 | EhCache, Infinispan 등 |
| 세밀함 | 거칠음 | 세밀함 (엔티티 단위) |
| 권장 | ✅ 일반적으로 권장 | 특수한 경우만 |

**권장 전략**:
- **Spring Cache로 충분** (Redis 사용)
- Hibernate 2nd Cache는 복잡도 증가 → 피하기 권장

---

### 6.2 Spring Cache 예제

```java
@Service
@CacheConfig(cacheNames = "users")
public class UserService {

    @Cacheable(key = "#id") // 캐시 조회
    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    @CachePut(key = "#user.id") // 캐시 갱신
    public User update(User user) {
        return userRepository.save(user);
    }

    @CacheEvict(key = "#id") // 캐시 삭제
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    @CacheEvict(allEntries = true) // 전체 캐시 삭제
    public void deleteAll() {
        userRepository.deleteAll();
    }
}
```

---

## 7. 실무 사용 패턴과 주의사항

### 7.1 엔티티 설계 Best Practices

```java
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_created_at", columnList = "created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class User extends BaseEntity { // BaseEntity에 생성/수정 시각

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Order> orders = new ArrayList<>();

    // 정적 팩토리 메서드
    public static User create(String email, String username) {
        User user = new User();
        user.email = email;
        user.username = username;
        user.role = UserRole.USER;
        return user;
    }

    // 비즈니스 메서드 (Setter 금지)
    public void changeUsername(String newUsername) {
        if (newUsername == null || newUsername.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        this.username = newUsername;
    }

    // 양방향 편의 메서드
    public void addOrder(Order order) {
        orders.add(order);
        order.setUser(this);
    }
}
```

---

### 7.2 Repository 패턴

```java
public interface UserRepository extends JpaRepository<User, Long> {

    // 메서드명 쿼리 (간단한 경우)
    Optional<User> findByEmail(String email);
    List<User> findByRoleAndActiveTrue(UserRole role);

    // @Query (복잡한 경우)
    @Query("SELECT u FROM User u WHERE u.createdAt >= :startDate")
    List<User> findRecentUsers(@Param("startDate") LocalDateTime startDate);

    // N+1 해결: @EntityGraph
    @EntityGraph(attributePaths = {"orders", "profile"})
    Optional<User> findWithOrdersById(Long id);

    // 벌크 연산
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.active = false WHERE u.lastLoginAt < :date")
    int deactivateInactiveUsers(@Param("date") LocalDateTime date);

    // 비관적 락
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);
}
```

---

### 7.3 공통 발생 이슈와 해결책

#### 문제 1: LazyInitializationException

**원인**: 트랜잭션 밖에서 Lazy Loading 시도

**해결**:
```java
// ❌ 나쁜 예
@Service
public class UserService {
    public User findUser(Long id) { // @Transactional 없음
        return userRepository.findById(id).orElseThrow();
    }
}

// Controller에서
user.getOrders().size(); // LazyInitializationException!

// ✅ 좋은 예
@Transactional(readOnly = true)
public User findUser(Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

---

#### 문제 2: MultipleBagFetchException

**원인**: 2개 이상의 컬렉션을 동시에 Fetch Join

**해결**:
```java
// ❌ 에러 발생
@EntityGraph(attributePaths = {"orders", "comments"})
List<User> findAll();

// ✅ 해결책 1: 하나는 Set 사용
@OneToMany(mappedBy = "user")
private Set<Order> orders = new HashSet<>(); // List → Set

// ✅ 해결책 2: 별도 쿼리로 분리
@EntityGraph(attributePaths = {"orders"})
List<User> findAll();

@EntityGraph(attributePaths = {"comments"})
List<User> findAllWithComments();
```

---

#### 문제 3: N+1 문제 재발

**진단**:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        show_sql: true
        format_sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

**해결**:
- `@EntityGraph` 또는 `Fetch Join` 사용
- `@BatchSize` 설정
- DTO 프로젝션으로 필요한 데이터만 조회

---

### 7.4 DDD 엔티티 구조 예제

```java
// 1. BaseEntity (공통 필드)
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

// 2. 도메인 엔티티
@Entity
@Table(name = "waitings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waiting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitingStatus status;

    @Column(nullable = false)
    private Integer partySize;

    // 정적 팩토리 메서드
    public static Waiting create(Restaurant restaurant, Integer partySize) {
        Waiting waiting = new Waiting();
        waiting.restaurant = restaurant;
        waiting.partySize = partySize;
        waiting.status = WaitingStatus.WAITING;
        return waiting;
    }

    // 비즈니스 로직
    public void call() {
        if (status != WaitingStatus.WAITING) {
            throw new IllegalStateException("Cannot call non-waiting entry");
        }
        this.status = WaitingStatus.CALLED;
    }

    public void cancel() {
        if (status == WaitingStatus.SEATED) {
            throw new IllegalStateException("Cannot cancel seated entry");
        }
        this.status = WaitingStatus.CANCELLED;
    }
}
```

---

### 7.5 테스트 작성 예제

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByEmail_ShouldReturnUser() {
        // given
        User user = User.create("test@example.com", "testuser");
        entityManager.persist(user);
        entityManager.flush();

        // when
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void findWithOrdersById_ShouldNotCauseNPlus1() {
        // given
        User user = User.create("test@example.com", "testuser");
        entityManager.persist(user);

        Order order1 = Order.create(user);
        Order order2 = Order.create(user);
        entityManager.persist(order1);
        entityManager.persist(order2);
        entityManager.flush();
        entityManager.clear(); // 영속성 컨텍스트 초기화

        // when
        Optional<User> found = userRepository.findWithOrdersById(user.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getOrders()).hasSize(2); // 추가 쿼리 없음
    }
}
```

---

## 참고 자료

**JPA/Hibernate 공식 문서**:
- [JPA 3.1 Specification](https://jakarta.ee/specifications/persistence/3.1/)
- [Hibernate ORM 6.6 Documentation](https://docs.jboss.org/hibernate/orm/6.6/introduction/html_single/Hibernate_Introduction.html)

**주요 참고 블로그**:
- [Baeldung - JPA Tutorials](https://www.baeldung.com/hibernate-one-to-many)
- [Vlad Mihalcea - High-Performance Java Persistence](https://vladmihalcea.com/the-best-way-to-map-a-onetomany-association-with-jpa-and-hibernate/)
- [Thorben Janssen - JPA Best Practices](https://thorben-janssen.com/naturalid-good-way-persist-natural-ids-hibernate/)

**주요 기능별 링크**:
- [@SoftDelete (Hibernate 6.4+)](https://vladmihalcea.com/hibernate-softdelete-annotation/)
- [@NaturalId Performance](https://vladmihalcea.com/the-best-way-to-map-a-naturalid-business-key-with-jpa-and-hibernate/)
- [Spring Data JPA @Query](https://www.baeldung.com/spring-data-jpa-query)
- [Spring Data JPA @Lock](https://vladmihalcea.com/spring-data-jpa-locking/)
- [@BatchSize Optimization](https://medium.com/jpa-java-persistence-api-guide/hibernate-optimization-with-batchsize-and-batch-size-configuration-579bf759fc05)
- [Hibernate @Formula](https://www.concretepage.com/hibernate/formula_hibernate_annotation)
- [@GeneratedValue Strategies](https://thorben-janssen.com/jpa-generate-primary-keys/)

---

**작성자**: Claude Code
**최종 수정일**: 2026-02-11
