# JPA Annotation Guide

이 문서는 실무와 면접 준비 관점에서 **헷갈리기 쉬운 JPA 어노테이션들**을 정리한 노트다.
특히 `@MapsId`와 `@MappedSuperclass`를 중심으로, 자주 놓치는 표준 JPA 어노테이션들의 목적, 사용 시점, 장단점, 실무 팁을 같이 정리했다.

---

## 1. 먼저 정리: `MapId`가 아니라 `@MapsId`

실무에서 자주 헷갈리는 이름이 있다.

- `@MapsId` : **연관관계가 엔티티 식별자(PK)를 직접 매핑**하도록 하는 어노테이션
- `@MapKey`, `@MapKeyColumn` : `Map<K, V>` 컬렉션을 매핑할 때 쓰는 어노테이션

즉 `@MapsId`는 **식별자 매핑** 쪽이고, `@MapKey`는 **컬렉션 키 매핑** 쪽이다. 이름이 비슷할 뿐 완전히 다른 용도다.

---

## 2. `@MapsId`는 언제 쓰는가

`@MapsId`는 `@ManyToOne` 또는 `@OneToOne` 연관관계가 **자식 엔티티의 PK 전체 또는 PK 일부를 직접 담당**하게 하고 싶을 때 쓴다.

쉽게 말하면:

> “자식 PK를 따로 만들지 말고, 부모 PK에서 파생시키겠다”

이럴 때 사용한다.

### 2-1. 가장 대표적인 케이스: 공유 PK 1:1

예를 들어 `Member`와 `MemberProfile`이 있다고 하자.

- `Member`는 독립적으로 존재 가능
- `MemberProfile`은 `Member` 없이는 의미가 약함
- `MemberProfile.id`는 `Member.id`와 같으면 충분함

```java
@Entity
public class Member {
    @Id
    @GeneratedValue
    private Long id;
}

@Entity
public class MemberProfile {
    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id")
    private Member member;

    private String nickname;
}
```

이 구조의 의미는 다음과 같다.

- `member_profile.id`는 PK이면서 동시에 FK다.
- `MemberProfile`의 식별자는 `Member`가 결정한다.
- 개발자가 `profile.id = member.getId()`를 수동으로 맞출 필요가 없다.

### 2-2. 복합키 일부를 부모 식별자와 맞출 때

```java
@Embeddable
public class OrderLineId {
    private Long orderId;
    private Integer lineNo;
}

@Entity
public class OrderLine {
    @EmbeddedId
    private OrderLineId id;

    @MapsId("orderId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    private int quantity;
}
```

이 경우:

- `OrderLineId.orderId`는 `order` 연관관계가 채운다.
- `lineNo`만 따로 세팅하면 된다.
- 복합키 일부를 부모 FK와 자연스럽게 맞출 수 있다.

### 2-3. 언제 쓰면 좋은가

다음 조건이 맞으면 `@MapsId`를 적극 검토할 만하다.

- 자식 엔티티가 부모에 **강하게 종속**됨
- 자식이 **독립 PK를 가질 이유가 적음**
- 공유 PK 1:1이 도메인적으로 자연스러움
- 복합키 일부가 부모 PK/FK로 설명되는 모델임

### 2-4. 언제 피하는 게 좋은가

다음 조건이면 일반 `@JoinColumn` + 별도 `@Id`가 더 낫다.

- 자식이 나중에 **독립적으로 식별**되어야 함
- 부모 없이도 자식이 비즈니스적으로 의미가 있음
- 단순 FK 관계인데 굳이 PK까지 부모에 종속시킬 이유가 없음

### 2-5. 실무 포인트

- 공유 PK 1:1은 모델 의도가 매우 강하다.
- 잘 맞는 도메인에서는 매우 깔끔하다.
- 잘못 쓰면 자식 엔티티를 너무 강하게 묶어서 유연성을 잃는다.
- 양방향 `@OneToOne`보다 단방향 `@OneToOne + @MapsId`가 더 효율적인 경우가 있다.

---

## 3. `@MappedSuperclass`는 언제 쓰는가

`@MappedSuperclass`는 **공통 매핑 필드를 하위 엔티티에 상속**시키고 싶을 때 쓰는 어노테이션이다.

핵심은 이거다.

- 이 클래스 자체는 **엔티티가 아니다**
- 이 클래스 자체로는 **테이블이 생기지 않는다**
- 하지만 안에 선언한 매핑은 **하위 엔티티가 상속받는다**

즉, “엔티티 공통 필드 베이스 클래스”를 만들 때 가장 자주 쓴다.

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue
    protected Long id;

    @Version
    protected Long version;

    protected LocalDateTime createdAt;
    protected LocalDateTime updatedAt;
}

@Entity
public class Order extends BaseEntity {
    private String orderNumber;
}

@Entity
public class Product extends BaseEntity {
    private String name;
}
```

이 경우:

- `BaseEntity` 자체 테이블은 없다.
- `Order`, `Product` 테이블에 `id`, `version`, `createdAt`, `updatedAt` 컬럼이 들어간다.

### 3-1. 언제 쓰면 좋은가

아래처럼 거의 모든 엔티티가 공유하는 필드가 있을 때 좋다.

- `id`
- `version`
- `createdAt`, `updatedAt`
- `createdBy`, `updatedBy`
- 소프트 삭제 플래그

### 3-2. 장점

- 중복 코드 감소
- 매핑 일관성 확보
- 감사 컬럼, 버전 필드 관리가 쉬움

### 3-3. 주의점

`@MappedSuperclass`는 **상속 전략(`@Inheritance`)과 다르다.**

#### `@MappedSuperclass`
- 독립 엔티티 아님
- 자체 테이블 없음
- 조회 대상 아님
- 공통 필드 상속용

#### `@Inheritance`
- 상속 구조 전체가 엔티티 모델임
- 단일 테이블 / 조인 전략 / 테이블별 전략 등 선택 가능
- 부모 타입으로 polymorphic query 가능

즉 `@MappedSuperclass`는 **공통 컬럼 재사용용**이고,
`@Inheritance`는 **엔티티 상속 모델링용**이다.

### 3-4. 실무 팁

- 공통 식별자/감사 필드에는 매우 유용하다.
- 너무 많은 비즈니스 로직까지 베이스 클래스로 올리면 엔티티 계층이 뻣뻣해진다.
- 공통 필드만 올리고, 도메인 로직은 신중하게 올리는 편이 좋다.

---

## 4. `@ElementCollection`

엔티티가 아닌 **값 타입 컬렉션**을 저장할 때 쓴다.

대표 예시:

- `Set<String> tags`
- `List<String> phoneNumbers`
- `List<AddressValue>` 같은 embeddable 컬렉션

```java
@Entity
public class Article {
    @Id
    private Long id;

    @ElementCollection
    @CollectionTable(name = "article_tags", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();
}
```

### 언제 쓰나

- 값 그 자체가 중요하고 독립 식별자가 필요 없을 때
- 태그, 별칭, 주소 값 객체 모음 같은 구조

### 주의점

- 엔티티 컬렉션에는 쓰면 안 된다.
- 값 타입 컬렉션은 변경 추적이 단순하지 않을 수 있다.
- 요소 수가 많고 수정이 잦으면 별도 엔티티로 분리하는 게 더 나을 수도 있다.

---

## 5. `@CollectionTable`

`@ElementCollection`과 거의 같이 다닌다.

값 타입 컬렉션을 어떤 테이블에 저장할지 명시한다.

```java
@ElementCollection
@CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "user_id"))
@Column(name = "skill_name")
private Set<String> skills;
```

### 언제 쓰나

- 컬렉션 테이블명, 조인 컬럼명을 명시하고 싶을 때
- 기본 naming strategy에 의존하고 싶지 않을 때

---

## 6. `@MapKey` / `@MapKeyColumn`

둘 다 `Map<K, V>` 컬렉션 매핑용이지만 용도가 다르다.

### `@MapKey`
엔티티의 특정 필드를 Map의 key로 삼고 싶을 때

```java
@OneToMany(mappedBy = "department")
@MapKey(name = "employeeNumber")
private Map<String, Employee> employeesByNumber;
```

### `@MapKeyColumn`
Map의 key를 별도 컬럼으로 저장하고 싶을 때

```java
@ElementCollection
@CollectionTable(name = "item_images")
@MapKeyColumn(name = "image_name")
@Column(name = "file_name")
private Map<String, String> images;
```

### 정리

- `@MapKey` : value 엔티티의 필드를 key로 사용
- `@MapKeyColumn` : key 자체를 컬럼으로 저장

---

## 7. `@OrderColumn`

`List`의 순서를 **DB에 영속화**하고 싶을 때 쓴다.

```java
@OneToMany
@OrderColumn(name = "display_order")
private List<Banner> banners = new ArrayList<>();
```

### 언제 쓰나

- 사용자가 직접 순서를 바꾸는 리스트
- 배너 순서
- 챕터 순서
- 메뉴 노출 순서

### 주의점

- 삭제/삽입/순서 변경 시 `order` 컬럼 업데이트가 추가로 발생할 수 있다.
- 단순 조회 정렬이면 `@OrderBy`나 쿼리 정렬이 더 적합한 경우가 많다.

---

## 8. `@Convert`

도메인 값을 DB에 저장할 표현으로 바꾸는 데 쓰는 어노테이션이다.

예를 들어 `Boolean`을 `Y/N`으로 저장하고 싶을 수 있다.

```java
@Converter(autoApply = true)
public class BooleanYnConverter implements AttributeConverter<Boolean, String> {
    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        return Boolean.TRUE.equals(attribute) ? "Y" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        return "Y".equals(dbData);
    }
}
```

```java
@Entity
public class User {
    @Id
    private Long id;

    private Boolean marketingAgreed;
}
```

### 언제 쓰나

- `Y/N`, `T/F`, 코드값 매핑
- 암호화/복호화 문자열
- 값 객체를 단일 컬럼으로 저장할 때

### 주의점

- ID, version, relationship 매핑에는 기대와 다르게 동작할 수 있으니 보수적으로 접근하는 게 좋다.
- enum은 단순하면 `@Enumerated`가 더 읽기 쉬울 수 있다.

---

## 9. `@AttributeOverride` / `@AssociationOverride`

재사용되는 embeddable이나 mapped superclass의 매핑을 **현재 위치에서 덮어쓰기** 위해 사용한다.

### `@AttributeOverride`
기본 속성 컬럼을 바꾸고 싶을 때

```java
@Embeddable
public class Address {
    private String city;
    private String street;
}

@Entity
public class Company {
    @Id
    private Long id;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "city", column = @Column(name = "office_city")),
        @AttributeOverride(name = "street", column = @Column(name = "office_street"))
    })
    private Address officeAddress;
}
```

### `@AssociationOverride`
embeddable 안의 연관관계 컬럼을 바꾸고 싶을 때 사용한다.

### 언제 쓰나

- 같은 embeddable을 여러 위치에 재사용할 때
- 컬럼명 충돌을 피하고 싶을 때
- 공통 구조는 유지하면서 컬럼명만 바꾸고 싶을 때

---

## 10. `@SecondaryTable`

엔티티 하나를 **여러 테이블에 나눠 저장**할 때 쓴다.

```java
@Entity
@Table(name = "customer")
@SecondaryTable(
    name = "customer_detail",
    pkJoinColumns = @PrimaryKeyJoinColumn(name = "customer_id")
)
public class Customer {
    @Id
    private Long id;

    private String name;

    @Column(table = "customer_detail")
    private String memo;
}
```

### 언제 쓰나

- 레거시 스키마에서 상세 테이블이 분리되어 있을 때
- 논리적으로는 하나의 엔티티인데 물리적으로 테이블이 나뉘어 있을 때

### 주의점

- 도메인적으로 완전히 다른 개념이면 엔티티 분리가 더 나을 수 있다.
- 테이블 분리가 단순히 스키마 사정인지, 도메인 경계인지 먼저 구분해야 한다.

---

## 11. `@NamedEntityGraph`

조회 시점마다 **fetch plan**을 조절하고 싶을 때 쓴다.

```java
@NamedEntityGraph(
    name = "Order.withMemberAndItems",
    attributeNodes = {
        @NamedAttributeNode("member"),
        @NamedAttributeNode("items")
    }
)
@Entity
public class Order {
    @Id
    private Long id;
}
```

### 언제 쓰나

- 목록 화면: member만 필요
- 상세 화면: member + items 필요
- 같은 엔티티를 여러 유스케이스에서 서로 다르게 fetch 하고 싶을 때

### 장점

- 무조건 EAGER로 두는 것보다 유연하다.
- fetch join 남발보다 재사용성이 좋다.

---

## 12. `@Version`

낙관적 락(optimistic locking)을 위한 필드다.

```java
@Entity
public class ProductStock {
    @Id
    private Long id;

    @Version
    private Long version;

    private int quantity;
}
```

### 언제 쓰나

- 동시 수정 가능성이 있는 엔티티
- 재고, 결제, 상태 전이, 관리자 수정 화면

### 왜 중요한가

- 마지막 저장이 이전 변경을 덮어쓰는 문제를 감지할 수 있다.
- 경력직 면접에서 거의 반드시 나오는 어노테이션이다.

---

## 13. `@PrimaryKeyJoinColumn`과 `@MapsId` 차이

둘 다 PK 기반 조인 상황에서 등장할 수 있지만, 성격이 다르다.

### `@MapsId`
- 연관관계가 식별자를 직접 매핑함
- 자식 PK와 부모 FK를 강하게 연결함
- 의도가 더 분명함

### `@PrimaryKeyJoinColumn`
- PK 컬럼으로 조인된다는 점을 표현
- `@OneToOne`, 상속, secondary table 등 다양한 곳에서 사용됨
- 식별자 동기화는 개발자가 더 신경 써야 하는 경우가 있다.

새 코드에서는 공유 PK 1:1 의도를 더 분명하게 드러내기 위해 `@MapsId`가 자주 선택된다.

---

## 14. 실무에서 특히 기억할 어노테이션

많은 어노테이션을 다 외우는 것보다 아래를 우선 익히는 게 좋다.

### 가장 체감 큰 것
- `@MapsId`
- `@MappedSuperclass`
- `@ElementCollection`
- `@Convert`
- `@AttributeOverride`
- `@SecondaryTable`
- `@NamedEntityGraph`
- `@Version`

### 실무에서 자주 틀리는 포인트
- `@MapsId`와 `@MapKey`를 혼동
- `@MappedSuperclass`와 `@Inheritance`를 혼동
- `@OrderColumn`과 `@OrderBy`를 혼동
- `@ElementCollection`을 엔티티 컬렉션처럼 사용
- `@Convert`로 모든 타입을 처리하려고 시도

---

## 15. 면접용 한 줄 정리

### `@MapsId`
> 부모와 자식의 식별자를 강하게 묶는 derived identifier 매핑이며, 공유 PK 1:1이나 복합키 일부가 부모 FK인 관계에서 사용한다.

### `@MappedSuperclass`
> 공통 매핑 필드를 하위 엔티티에 상속하기 위한 베이스 클래스이며, 자체 테이블이나 독립 엔티티 정체성은 없다.

### `@ElementCollection`
> 식별자가 없는 값 타입 컬렉션을 별도 테이블에 저장할 때 쓴다.

### `@Version`
> 낙관적 락을 위해 엔티티 변경 충돌을 감지하는 버전 필드다.

---

## 16. 간단한 선택 가이드

### `@MapsId`를 쓸지 고민될 때
- 자식이 부모 식별자에 종속된다 → 사용 검토
- 자식이 독립 PK가 필요하다 → 일반 FK 관계

### `@MappedSuperclass`를 쓸지 고민될 때
- 공통 컬럼 재사용이 목적이다 → 적합
- 부모 타입 자체를 엔티티로 조회하고 싶다 → `@Inheritance` 검토

### `@ElementCollection`을 쓸지 고민될 때
- 값 타입, 독립 식별자 불필요 → 적합
- 변경 많고 독립 생명주기 필요 → 엔티티 분리 검토

### `@OrderColumn`을 쓸지 고민될 때
- 순서 자체가 비즈니스 의미를 가진다 → 적합
- 단순 조회 정렬이다 → `@OrderBy`/쿼리 정렬 우선

---

## 17. Reference

아래 공식 문서를 바탕으로 정리했다.

- Jakarta Persistence API 3.2 `@MapsId`
  - https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/mapsid
- Jakarta Persistence API 3.2 `@MappedSuperclass`
  - https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/mappedsuperclass
- Jakarta Persistence API 3.2 `@ElementCollection`
  - https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/elementcollection
- Jakarta Persistence API 3.2 `@CollectionTable`
  - https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/collectiontable
- Jakarta Persistence API 3.2 `@MapKey`
  - https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/mapkey
- Jakarta Persistence API 3.2 `@AttributeOverride`
  - https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/attributeoverride
- Jakarta Persistence API 3.2 `@SecondaryTable`
  - https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/secondarytable
- Jakarta Persistence API 3.2 `@NamedEntityGraph`
  - https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/namedentitygraph
- Jakarta Persistence API 3.2 `@Version`
  - https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/version
- Jakarta Persistence Specification 3.2
  - https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2
- Hibernate ORM User Guide 7.1
  - https://docs.hibernate.org/orm/7.1/userguide/html_single/
- A Short Guide to Hibernate 7
  - https://docs.hibernate.org/orm/7.3/introduction/html_single/

