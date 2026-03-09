# Kotlin Scope Functions 완벽 가이드

## 개요

Scope function은 객체의 컨텍스트 내에서 코드 블록을 실행하기 위한 함수다.
`let`, `run`, `with`, `apply`, `also` 5가지가 있으며, 두 가지 기준으로 구분된다.

- **컨텍스트 참조 방식**: `this` (람다 수신자) vs `it` (람다 인자)
- **반환값**: 객체 자신 vs 람다의 마지막 값

---

## 한눈에 비교

| 함수 | 컨텍스트 참조 | 반환값 | 주요 용도 |
|------|-------------|--------|----------|
| `let` | `it` | 람다 결과 | null 체크, 변환 |
| `run` | `this` | 람다 결과 | 연산 후 결과 반환 |
| `with` | `this` | 람다 결과 | 이미 있는 객체에 연산 |
| `apply` | `this` | 객체 자신 | 객체 초기화/설정 |
| `also` | `it` | 객체 자신 | 부수 작업 (로깅, 검증) |

---

## 1. `let`

> **컨텍스트 객체를 `it`으로 참조, 람다 결과를 반환**

### 언제 쓰나?
- `?.let { }` 패턴으로 null safe 처리
- 값을 변환해서 다른 타입으로 반환할 때

```kotlin
// null 체크
val name: String? = "홍길동"
val length = name?.let {
    println("이름: $it")
    it.length   // 반환값
} // length = 4

// null이면 블록 자체가 실행되지 않음
val nullName: String? = null
val result = nullName?.let { it.length } // result = null
```

```kotlin
// 변환
val numbers = listOf(1, 2, 3)
val doubled = numbers.let { list ->
    list.map { it * 2 }
} // doubled = [2, 4, 6]
```

---

## 2. `run`

> **컨텍스트 객체를 `this`로 참조, 람다 결과를 반환**

### 언제 쓰나?
- 객체를 초기화하면서 동시에 결과값이 필요할 때
- `?.run { }` 패턴으로 null safe 처리 + 결과 반환

```kotlin
// 객체 설정 + 결과 반환
val result = StringBuilder().run {
    append("Hello")
    append(", World")
    toString()  // 반환값
} // result = "Hello, World"
```

```kotlin
// null safe 처리
val comment: Comment? = findComment(1L)
val content = comment?.run {
    "[${author}] $content"  // this = comment
} // content = "[홍길동] 댓글 내용" 또는 null
```

> `run` vs `let`: 객체 멤버를 자주 참조하면 `this`를 쓰는 `run`,
> 외부 변수와 혼동될 수 있으면 `it`을 쓰는 `let` 선택

---

## 3. `with`

> **컨텍스트 객체를 `this`로 참조, 람다 결과를 반환 (확장 함수 아님)**

### 언제 쓰나?
- 이미 생성된 객체에 여러 작업을 묶어서 처리할 때
- non-null 객체에서 `run`과 동일한 역할

```kotlin
val sb = StringBuilder()

// with 없이
sb.append("Hello")
sb.append(", ")
sb.append("World")

// with 사용
val result = with(sb) {
    append("Hello")
    append(", ")
    append("World")
    toString()
}
```

> `with`는 확장 함수가 아니라 일반 함수다.
> 따라서 `?.with { }` null safe 체이닝이 불가능하다. null이 올 수 있으면 `run`을 써라.

---

## 4. `apply`

> **컨텍스트 객체를 `this`로 참조, 객체 자신을 반환**

### 언제 쓰나?
- 객체 생성 직후 프로퍼티/설정을 초기화할 때
- Builder 패턴 대용

```kotlin
// 객체 초기화
val objectMapper = ObjectMapper().apply {
    registerModule(kotlinModule())          // this.registerModule(...)
    activateDefaultTyping(validator, mode) // this.activateDefaultTyping(...)
}
// apply 이후에도 objectMapper는 ObjectMapper 인스턴스 그 자체

// 풀어쓰면 동일
val objectMapper = ObjectMapper()
objectMapper.registerModule(kotlinModule())
objectMapper.activateDefaultTyping(validator, mode)
```

```kotlin
// data class 복사 후 변경 (copy + apply)
val updated = comment.copy().apply {
    content = "수정된 내용"
}
```

---

## 5. `also`

> **컨텍스트 객체를 `it`으로 참조, 객체 자신을 반환**

### 언제 쓰나?
- 객체를 변경하지 않고 **부수 작업** (로깅, 검증, 디버깅)을 끼워 넣을 때
- 체이닝 중간에 로그를 찍고 싶을 때

```kotlin
// 로깅
val comment = commentRepository.save(newComment)
    .also { log.info("댓글 저장 완료: id=${it.id}") }

// 검증
val comments = commentRepository.findAll()
    .also { check(it.isNotEmpty()) { "댓글이 없습니다" } }
```

> `also` vs `apply`: 객체 자신의 멤버를 쓸 필요 없는 부수 작업이면 `also`,
> 객체 내부를 설정하는 작업이면 `apply`

---

## 실무 선택 기준

```
결과값이 필요한가?
├── YES → 람다 결과 반환
│   ├── 객체 멤버를 자주 참조 → run / with
│   └── 외부 변수와 구분 필요 → let
└── NO → 객체 자신 반환
    ├── 객체 초기화/설정 → apply
    └── 부수 작업 (로깅 등) → also
```

---

## 체이닝 예시

scope function은 체이닝해서 쓸 수 있다.

```kotlin
val result = commentRepository.findById(id)
    .orElse(null)
    ?.also { log.info("조회된 댓글: ${it.id}") }  // 로깅 (부수 작업)
    ?.let { comment ->                              // 변환 (결과 반환)
        CommentResponse.from(comment)
    }
```

---

## 정리

| 기억법 | |
|--------|---|
| `apply`, `also` | **A**로 시작 → 객체 자신(**A**pply itself) 반환 |
| `let`, `run`, `with` | 람다 결과 반환 |
| `this` 계열 (`run`, `with`, `apply`) | 객체 멤버 직접 접근 (수신자처럼) |
| `it` 계열 (`let`, `also`) | 외부에서 인자처럼 접근 |
