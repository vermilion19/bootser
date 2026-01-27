# Java 제네릭, 와일드카드, 함수형 인터페이스 완벽 가이드

## 목차
1. [제네릭 기초](#1-제네릭-기초)
2. [와일드카드](#2-와일드카드)
3. [함수형 인터페이스](#3-함수형-인터페이스)
4. [실전 분석: computeIfAbsent](#4-실전-분석-computeifabsent)

---

## 1. 제네릭 기초

### 1.1 제네릭이란?

**타입을 파라미터화**하여 컴파일 시점에 타입 안전성을 보장하는 기능

```java
// 제네릭 없이 (Java 1.4 이전)
List list = new ArrayList();
list.add("hello");
list.add(123);  // 컴파일 OK, 런타임에 문제 발생 가능
String s = (String) list.get(1);  // ClassCastException!

// 제네릭 사용 (Java 5+)
List<String> list = new ArrayList<>();
list.add("hello");
list.add(123);  // 컴파일 에러! 타입 안전
String s = list.get(0);  // 캐스팅 불필요
```

### 1.2 타입 파라미터 명명 관례

| 문자 | 의미 | 예시 |
|------|------|------|
| `T` | Type | `List<T>`, `Optional<T>` |
| `E` | Element | `Collection<E>` |
| `K` | Key | `Map<K, V>` |
| `V` | Value | `Map<K, V>` |
| `N` | Number | `Calculator<N extends Number>` |
| `R` | Return | `Function<T, R>` |

### 1.3 제네릭 클래스 정의

```java
// 제네릭 클래스
public class Box<T> {
    private T item;

    public void set(T item) {
        this.item = item;
    }

    public T get() {
        return item;
    }
}

// 사용
Box<String> stringBox = new Box<>();
stringBox.set("hello");
String value = stringBox.get();  // 캐스팅 불필요

Box<Integer> intBox = new Box<>();
intBox.set(123);
Integer num = intBox.get();
```

### 1.4 제네릭 메서드 정의

```java
// 클래스의 제네릭과 별개로 메서드에 제네릭 선언 가능
public class Util {

    // <T>가 메서드의 타입 파라미터 선언
    public static <T> T getFirst(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    // 여러 타입 파라미터
    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        return map.containsKey(key) ? map.get(key) : defaultValue;
    }
}

// 사용 - 타입 추론
String first = Util.getFirst(List.of("a", "b", "c"));
Integer num = Util.getFirst(List.of(1, 2, 3));
```

### 1.5 타입 파라미터 vs 구체적 타입

```java
// 정의 시: 타입 파라미터 사용 (T, K, V 등)
public class HashMap<K, V> {
    public V get(K key) { ... }
    public V put(K key, V value) { ... }
}

// 사용 시: 구체적 타입 지정 (String, Integer 등)
HashMap<String, Integer> map = new HashMap<>();
//       ↑       ↑
//       K=String, V=Integer로 대체됨
```

---

## 2. 와일드카드

### 2.1 와일드카드란?

`?`는 **"알 수 없는 타입"**을 의미. 제네릭 타입을 **사용할 때** 유연성을 제공.

```java
// 제네릭 타입 파라미터: 정의할 때 사용
public class Box<T> { }  // T는 "어떤 타입이든 될 수 있다"

// 와일드카드: 사용할 때 사용
public void process(Box<?> box) { }  // ?는 "어떤 Box든 받겠다"
```

### 2.2 세 가지 와일드카드

```
┌─────────────────────────────────────────────────────────────┐
│                        Object                                │
│                           │                                  │
│                        Number                                │
│                       /       \                              │
│                  Integer    Double                           │
│                                                              │
│  <? extends Number>  ← Number와 그 하위 타입                  │
│  <? super Integer>   ← Integer와 그 상위 타입                 │
│  <?>                 ← 모든 타입                              │
└─────────────────────────────────────────────────────────────┘
```

| 와일드카드 | 이름 | 의미 | 가능한 타입 |
|-----------|------|------|-------------|
| `<?>` | Unbounded | 모든 타입 | Object, String, Integer, ... |
| `<? extends T>` | Upper Bounded | T와 T의 하위 타입 | T, T의 자식들 |
| `<? super T>` | Lower Bounded | T와 T의 상위 타입 | T, T의 부모들 |

### 2.3 `<? extends T>` - 상한 와일드카드

**"T 또는 T의 자식 타입"** - **읽기 전용** (Producer)

```java
// Number 또는 Number의 하위 타입 (Integer, Double, ...)
public double sum(List<? extends Number> numbers) {
    double total = 0;
    for (Number n : numbers) {  // 읽기 OK - Number로 받을 수 있음
        total += n.doubleValue();
    }
    // numbers.add(1);  // 컴파일 에러! - 쓰기 불가
    return total;
}

// 사용
sum(List.of(1, 2, 3));           // List<Integer> OK
sum(List.of(1.1, 2.2, 3.3));     // List<Double> OK
sum(List.of(1, 2.2, 3L));        // List<Number> OK
```

**왜 쓰기가 안 될까?**

```java
List<? extends Number> list = new ArrayList<Integer>();
// list가 실제로 List<Integer>인지 List<Double>인지 모름
list.add(1.0);  // 만약 List<Integer>면? Double을 넣으면 안됨!
list.add(1);    // 만약 List<Double>면? Integer를 넣으면 안됨!
// 컴파일러: "뭔지 모르니까 아무것도 못 넣게 할게"
```

### 2.4 `<? super T>` - 하한 와일드카드

**"T 또는 T의 부모 타입"** - **쓰기 전용** (Consumer)

```java
// Integer 또는 Integer의 상위 타입 (Number, Object)
public void addNumbers(List<? super Integer> list) {
    list.add(1);      // 쓰기 OK - Integer는 항상 넣을 수 있음
    list.add(2);
    list.add(3);
    // Integer n = list.get(0);  // 컴파일 에러! - 읽기 시 타입 불확실
    Object obj = list.get(0);    // Object로만 읽기 가능
}

// 사용
List<Integer> intList = new ArrayList<>();
List<Number> numList = new ArrayList<>();
List<Object> objList = new ArrayList<>();

addNumbers(intList);  // OK
addNumbers(numList);  // OK
addNumbers(objList);  // OK
```

**왜 Integer로 못 읽을까?**

```java
List<? super Integer> list = new ArrayList<Object>();
// list가 실제로 List<Object>일 수 있음
list.add(1);  // Integer 추가 OK
// 하지만 list에는 이미 String이 들어있을 수도 있음 (Object이므로)
Integer n = list.get(0);  // String이 나오면? ClassCastException!
// 컴파일러: "뭐가 나올지 모르니까 Object로만 읽어"
```

### 2.5 PECS 원칙

**P**roducer **E**xtends, **C**onsumer **S**uper

```java
// Producer (데이터를 제공) → extends 사용
public void copy(List<? extends T> source,    // source에서 읽기만 함
                 List<? super T> destination) // destination에 쓰기만 함
{
    for (T item : source) {
        destination.add(item);
    }
}
```

```
┌────────────────────────────────────────────────────────┐
│  상황              │  와일드카드         │  기억법      │
├────────────────────────────────────────────────────────┤
│  데이터를 꺼낼 때   │  <? extends T>     │  Producer   │
│  데이터를 넣을 때   │  <? super T>       │  Consumer   │
│  둘 다 해야 할 때   │  <T> (와일드카드 X) │  -          │
└────────────────────────────────────────────────────────┘
```

### 2.6 제네릭 타입 파라미터 vs 와일드카드

| 구분 | 제네릭 타입 파라미터 | 와일드카드 |
|------|---------------------|-----------|
| 문법 | `<T>`, `<K, V>` | `<?>`, `<? extends T>` |
| 사용 위치 | 클래스/메서드 **정의** | 타입 **사용** (매개변수, 변수) |
| 타입 참조 | 메서드 내에서 T로 참조 가능 | 참조 불가 (이름이 없음) |
| 목적 | 타입 간 관계 표현 | 유연한 타입 수용 |

```java
// 제네릭 타입 파라미터 - T를 여러 곳에서 참조
public <T> void copy(List<T> source, List<T> dest) {
    for (T item : source) {  // T로 참조
        dest.add(item);       // 같은 T 타입
    }
}

// 와일드카드 - 타입 참조 불필요할 때
public void printAll(List<?> list) {
    for (Object item : list) {  // 타입 참조 불필요
        System.out.println(item);
    }
}
```

---

## 3. 함수형 인터페이스

### 3.1 함수형 인터페이스란?

**추상 메서드가 딱 하나**인 인터페이스. 람다 표현식의 타겟 타입.

```java
@FunctionalInterface  // 선택적이지만 권장
public interface Calculator {
    int calculate(int a, int b);  // 추상 메서드 1개

    // default, static 메서드는 있어도 됨
    default void printResult(int result) {
        System.out.println(result);
    }
}

// 람다로 구현
Calculator add = (a, b) -> a + b;
Calculator multiply = (a, b) -> a * b;

add.calculate(3, 5);       // 8
multiply.calculate(3, 5);  // 15
```

### 3.2 주요 함수형 인터페이스

```java
// java.util.function 패키지
```

| 인터페이스 | 메서드 | 설명 | 예시 |
|-----------|--------|------|------|
| `Function<T, R>` | `R apply(T t)` | T → R 변환 | `s -> s.length()` |
| `BiFunction<T, U, R>` | `R apply(T t, U u)` | (T, U) → R 변환 | `(a, b) -> a + b` |
| `Consumer<T>` | `void accept(T t)` | T 소비 (반환 없음) | `s -> System.out.println(s)` |
| `BiConsumer<T, U>` | `void accept(T t, U u)` | (T, U) 소비 | `(k, v) -> map.put(k, v)` |
| `Supplier<T>` | `T get()` | T 생성 (입력 없음) | `() -> new ArrayList<>()` |
| `Predicate<T>` | `boolean test(T t)` | T → boolean | `s -> s.isEmpty()` |
| `UnaryOperator<T>` | `T apply(T t)` | T → T (같은 타입) | `n -> n * 2` |
| `BinaryOperator<T>` | `T apply(T t1, T t2)` | (T, T) → T | `(a, b) -> a + b` |

### 3.3 Function<T, R> 상세

```java
@FunctionalInterface
public interface Function<T, R> {
    R apply(T t);  // 핵심 메서드: T를 받아 R을 반환

    // 합성 메서드들
    default <V> Function<V, R> compose(Function<? super V, ? extends T> before) { ... }
    default <V> Function<T, V> andThen(Function<? super R, ? extends V> after) { ... }
}
```

```java
// 사용 예
Function<String, Integer> strToLength = s -> s.length();
Function<Integer, Integer> doubleIt = n -> n * 2;

// 합성: "hello" → 5 → 10
Function<String, Integer> composed = strToLength.andThen(doubleIt);
int result = composed.apply("hello");  // 10
```

### 3.4 람다 표현식 문법

```java
// 풀 문법
(Integer a, Integer b) -> { return a + b; }

// 타입 추론
(a, b) -> { return a + b; }

// 단일 표현식 (return, 중괄호 생략)
(a, b) -> a + b

// 파라미터 1개 (괄호 생략)
a -> a * 2

// 파라미터 없음
() -> System.out.println("hello")

// 메서드 참조
String::length        // s -> s.length()
System.out::println   // s -> System.out.println(s)
ArrayList::new        // () -> new ArrayList<>()
```

### 3.5 람다와 함수형 인터페이스 매칭

```java
// 컴파일러가 타겟 타입을 보고 람다의 타입을 추론

// Function<String, Integer>의 apply(String) → Integer
Function<String, Integer> f1 = s -> s.length();

// Consumer<String>의 accept(String) → void
Consumer<String> c1 = s -> System.out.println(s);

// Supplier<List<String>>의 get() → List<String>
Supplier<List<String>> s1 = () -> new ArrayList<>();

// Predicate<String>의 test(String) → boolean
Predicate<String> p1 = s -> s.isEmpty();
```

---

## 4. 실전 분석: computeIfAbsent

### 4.1 메서드 시그니처 분해

```java
// Map 인터페이스의 computeIfAbsent
V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction)
```

하나씩 분석해보자:

### 4.2 Map<K, V>의 K, V

```java
public interface Map<K, V> {
    V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);
}
```

- `K`: Map을 **정의할 때** 선언된 Key 타입
- `V`: Map을 **정의할 때** 선언된 Value 타입
- **사용 시 구체적 타입으로 대체됨**

```java
Map<String, Integer> map = new HashMap<>();
//    ↓        ↓
//    K        V
// 이 map에서 computeIfAbsent는:
// Integer computeIfAbsent(String key, Function<? super String, ? extends Integer> fn)
```

### 4.3 Function<? super K, ? extends V>의 의미

```java
Function<? super K, ? extends V> mappingFunction
```

**분해:**
- `Function<입력타입, 출력타입>`: 입력을 받아 출력을 반환하는 함수
- `? super K`: K 또는 K의 상위 타입을 **입력**으로 받는 함수
- `? extends V`: V 또는 V의 하위 타입을 **출력**하는 함수

### 4.4 왜 와일드카드를 썼을까?

**유연성을 위해!**

```java
Map<Integer, Number> map = new HashMap<>();

// 만약 Function<K, V> 였다면 (와일드카드 없이):
// Function<Integer, Number>만 가능

// Function<? super K, ? extends V> 덕분에:
// 아래 모두 가능!

// 1. Function<Integer, Number> - 정확히 일치
map.computeIfAbsent(1, (Integer k) -> k * 1.5);

// 2. Function<Number, Number> - 입력이 더 넓음 (? super K)
map.computeIfAbsent(1, (Number k) -> k.doubleValue());

// 3. Function<Object, Number> - 입력이 더 넓음 (? super K)
map.computeIfAbsent(1, (Object k) -> 0.0);

// 4. Function<Integer, Integer> - 출력이 더 좁음 (? extends V)
map.computeIfAbsent(1, (Integer k) -> k * 2);  // Integer는 Number의 하위 타입

// 5. Function<Integer, Double> - 출력이 더 좁음 (? extends V)
map.computeIfAbsent(1, (Integer k) -> k * 1.5);  // Double은 Number의 하위 타입
```

### 4.5 시각화

```
Map<Integer, Number> 에서 computeIfAbsent의 Function 파라미터:

Function<? super Integer, ? extends Number>

입력 (? super Integer):          출력 (? extends Number):
        Object                           Number ← 허용
           ↑                            /      \
        Number                     Integer    Double ← 허용
           ↑                          ↑         ↑
        Integer ← 기준              허용       허용

Integer, Number, Object 가능      Number, Integer, Double 가능
(더 넓게 받을 수 있음)            (더 구체적으로 반환 가능)
```

### 4.6 PECS 관점에서

```java
Function<? super K, ? extends V>
         ↑            ↑
      Consumer      Producer
      (K를 소비)    (V를 생성)
```

- `? super K`: 함수가 K를 **소비**(입력으로 받음) → super
- `? extends V`: 함수가 V를 **생성**(출력으로 반환) → extends

### 4.7 실제 사용에서는 신경 쓸 필요 없음

```java
Map<Integer, List<String>> groups = new HashMap<>();

// 우리가 쓰는 람다
groups.computeIfAbsent(3, k -> new ArrayList<>());
//                        ↑
//                        컴파일러가 알아서 타입 추론
//                        Function<Integer, ArrayList<String>>으로 추론됨
//                        ArrayList<String>은 List<String>의 하위 타입 → OK!
```

---

## 5. 정리: 언제 뭘 쓰나?

### 5.1 제네릭 타입 파라미터 (`T`, `K`, `V`)

**클래스나 메서드를 정의할 때** 사용

```java
// 클래스 정의
public class Box<T> { }
public interface Map<K, V> { }

// 메서드 정의
public <T> T getFirst(List<T> list) { }
public <K, V> void put(K key, V value) { }
```

### 5.2 와일드카드 (`?`, `? extends`, `? super`)

**제네릭 타입을 사용할 때** 유연성을 위해 사용

```java
// 매개변수 타입
public void process(List<?> list) { }
public double sum(List<? extends Number> numbers) { }
public void fill(List<? super Integer> list) { }

// API 정의 시 유연한 타입 수용
V computeIfAbsent(K key, Function<? super K, ? extends V> fn);
```

### 5.3 판단 기준

```
질문: 메서드/클래스 내에서 그 타입을 참조해야 하나?

예 → 제네릭 타입 파라미터 (T)
    public <T> T getFirst(List<T> list) {
        return list.get(0);  // T로 참조
    }

아니오 → 와일드카드 (?)
    public void printAll(List<?> list) {
        for (Object o : list) {  // 타입 참조 불필요
            System.out.println(o);
        }
    }
```

### 5.4 빠른 참조표

| 상황 | 사용 | 예시 |
|------|------|------|
| 타입 정의 (클래스/인터페이스) | `<T>` | `class Box<T>` |
| 메서드 정의 (타입 참조 필요) | `<T>` | `<T> T first(List<T> list)` |
| 메서드 정의 (타입 참조 불필요) | `<?>` | `void print(List<?> list)` |
| 읽기만 할 때 | `<? extends T>` | `sum(List<? extends Number>)` |
| 쓰기만 할 때 | `<? super T>` | `fill(List<? super Integer>)` |
| API 파라미터 (유연하게) | 와일드카드 조합 | `Function<? super K, ? extends V>` |

---

## 6. 연습 문제

### Q1. 다음 코드가 컴파일되는 이유는?

```java
Map<String, List<Integer>> map = new HashMap<>();
map.computeIfAbsent("key", k -> new ArrayList<>());
```

<details>
<summary>정답</summary>

```
computeIfAbsent의 시그니처:
V computeIfAbsent(K key, Function<? super K, ? extends V> fn)

Map<String, List<Integer>>에서:
- K = String
- V = List<Integer>

따라서 Function은:
Function<? super String, ? extends List<Integer>>

람다 k -> new ArrayList<>()는:
- 입력: String (? super String 만족)
- 출력: ArrayList<Integer> (? extends List<Integer> 만족)
  - ArrayList<Integer>는 List<Integer>의 하위 타입이므로 OK
```
</details>

### Q2. 왜 안 될까?

```java
List<? extends Number> list = new ArrayList<Integer>();
list.add(1);  // 컴파일 에러!
```

<details>
<summary>정답</summary>

```
list의 실제 타입이 뭔지 컴파일러는 모름.
- List<Integer>일 수도 있고
- List<Double>일 수도 있고
- List<Number>일 수도 있음

만약 List<Double>인데 Integer를 넣으면?
타입 안전성 위반!

그래서 컴파일러는 "? extends"에 아무것도 못 넣게 막음.
(null만 가능)
```
</details>

### Q3. 이건 왜 될까?

```java
List<? super Integer> list = new ArrayList<Number>();
list.add(1);  // OK!
```

<details>
<summary>정답</summary>

```
list의 실제 타입은:
- List<Integer>이거나
- List<Number>이거나
- List<Object>임

어떤 경우든 Integer를 넣는 건 안전함!
- List<Integer>에 Integer 넣기 → OK
- List<Number>에 Integer(Number의 하위) 넣기 → OK
- List<Object>에 Integer(Object의 하위) 넣기 → OK

그래서 "? super Integer"에는 Integer를 넣을 수 있음.
```
</details>
