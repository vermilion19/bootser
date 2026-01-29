# Java computeIfAbsent 완벽 가이드

## 개요

`Map.computeIfAbsent()`는 **키가 없을 때만 값을 생성**하는 메서드로, 코딩 테스트에서 그룹핑 패턴에 자주 사용된다.

---

## 1. 메서드 시그니처

```java
V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction)
```

| 파라미터 | 설명 |
|----------|------|
| `key` | 찾을 키 |
| `mappingFunction` | 키가 없을 때 실행할 함수 (key → value) |
| **반환값** | 기존 값 또는 새로 생성된 값 |

**핵심**: 두 번째 인자는 `Function<K, V>` - **key를 받아서 value를 반환하는 함수**

---

## 2. 예제 코드

```java
String[] words = {"abc", "banana", "aaa", "ca", "aa", "b"};

Map<Integer, List<String>> groups = new HashMap<>();

for (String word : words) {
    int len = word.length();
    groups.computeIfAbsent(len, k -> new ArrayList<>()).add(word);
}
```

---

## 3. 동작 원리

### 3.1 한 줄 분해

```java
groups.computeIfAbsent(len, k -> new ArrayList<>()).add(word);
```

이 한 줄을 풀어쓰면:

```java
// 1단계: computeIfAbsent 실행
List<String> list;
if (groups.containsKey(len)) {
    // 키가 이미 있으면 → 기존 값 반환
    list = groups.get(len);
} else {
    // 키가 없으면 → 람다 실행해서 새 값 생성 후 저장
    list = new ArrayList<>();  // k -> new ArrayList<>() 실행됨
    groups.put(len, list);
}

// 2단계: 반환된 리스트에 단어 추가
list.add(word);
```

### 3.2 흐름도

```
computeIfAbsent(key, mappingFunction)
          │
          ▼
    ┌─────────────┐
    │ key 존재?   │
    └─────┬───────┘
          │
    ┌─────┴─────┐
    │           │
   YES         NO
    │           │
    ▼           ▼
 기존 값     mappingFunction(key) 실행
  반환          │
    │           ▼
    │       새 값 생성
    │           │
    │           ▼
    │       map.put(key, 새값)
    │           │
    └─────┬─────┘
          │
          ▼
       값 반환
```

---

## 4. `k` 파라미터가 필요한 이유

### 4.1 Function 인터페이스 규약

```java
// Function 인터페이스 정의
@FunctionalInterface
public interface Function<T, R> {
    R apply(T t);  // 입력 T를 받아서 R을 반환
}
```

```java
// 컴파일러 입장에서:
groups.computeIfAbsent(len, k -> new ArrayList<>());
//                          ↑
//                     Function<Integer, List<String>> 타입이어야 함
//                     Integer를 받아서 List<String>을 반환하는 함수
```

**`k`는 사용 안 해도 문법적으로 받아야 함** (인터페이스 규약)

### 4.2 k를 실제로 사용하는 경우

```java
// 예1: key 값에 따라 다른 초기 용량의 리스트 생성
groups.computeIfAbsent(len, k -> new ArrayList<>(k * 10));
//                          ↑
//                          len 값이 k로 전달됨
//                          len=3이면 capacity 30인 리스트 생성
```

```java
// 예2: key를 포함한 기본값 생성
Map<String, String> cache = new HashMap<>();
cache.computeIfAbsent("user:123", k -> "default_" + k);
// 결과: "user:123" → "default_user:123"
```

```java
// 예3: key로 외부 조회
Map<Long, User> userCache = new HashMap<>();
userCache.computeIfAbsent(userId, k -> userRepository.findById(k));
//                                 ↑
//                                 userId가 k로 전달되어 DB 조회
```

### 4.3 사용하지 않는 파라미터 표현

```java
// Java 22+ : 언더스코어 사용 가능 (권장)
groups.computeIfAbsent(len, _ -> new ArrayList<>());

// Java 21 이하: 의미 있는 이름 또는 아무 이름
groups.computeIfAbsent(len, unused -> new ArrayList<>());
groups.computeIfAbsent(len, length -> new ArrayList<>());
groups.computeIfAbsent(len, k -> new ArrayList<>());  // 관례적으로 k 사용
```

---

## 5. 실행 흐름 시각화

```
words = ["abc", "banana", "aaa", "ca", "aa", "b"]

1. "abc" (len=3)
   groups = {}  → 3 없음 → new ArrayList<>() 생성
   groups = {3: ["abc"]}

2. "banana" (len=6)
   groups = {3: ["abc"]}  → 6 없음 → new ArrayList<>() 생성
   groups = {3: ["abc"], 6: ["banana"]}

3. "aaa" (len=3)
   groups = {3: ["abc"], 6: ["banana"]}  → 3 있음 → 기존 리스트 반환
   groups = {3: ["abc", "aaa"], 6: ["banana"]}

4. "ca" (len=2)
   → 2 없음 → new ArrayList<>() 생성
   groups = {3: ["abc", "aaa"], 6: ["banana"], 2: ["ca"]}

5. "aa" (len=2)
   → 2 있음 → 기존 리스트 반환
   groups = {3: ["abc", "aaa"], 6: ["banana"], 2: ["ca", "aa"]}

6. "b" (len=1)
   → 1 없음 → new ArrayList<>() 생성
   groups = {3: ["abc", "aaa"], 6: ["banana"], 2: ["ca", "aa"], 1: ["b"]}
```

**최종 결과:**
```java
{
    1: ["b"],
    2: ["ca", "aa"],
    3: ["abc", "aaa"],
    6: ["banana"]
}
```

---

## 6. computeIfAbsent vs 기존 방식 비교

### 6.1 전통적인 방식

```java
Map<Integer, List<String>> groups = new HashMap<>();

for (String word : words) {
    int len = word.length();

    // 키 존재 확인 → 없으면 생성 → 값 추가
    if (!groups.containsKey(len)) {
        groups.put(len, new ArrayList<>());
    }
    groups.get(len).add(word);
}
```

### 6.2 computeIfAbsent 방식

```java
Map<Integer, List<String>> groups = new HashMap<>();

for (String word : words) {
    int len = word.length();
    groups.computeIfAbsent(len, k -> new ArrayList<>()).add(word);
}
```

### 6.3 비교

| 항목 | 전통적 방식 | computeIfAbsent |
|------|-------------|-----------------|
| 코드 줄 수 | 4줄 | 1줄 |
| get() 호출 | 2번 (containsKey + get) | 1번 |
| 가독성 | 의도 파악에 시간 필요 | 한눈에 파악 |
| 원자성 | 없음 (멀티스레드 위험) | 있음 |

---

## 7. 관련 메서드들

### 7.1 computeIfAbsent vs computeIfPresent vs compute

```java
Map<String, Integer> map = new HashMap<>();
map.put("a", 1);

// computeIfAbsent: 키가 없을 때만 실행
map.computeIfAbsent("a", k -> 100);  // 실행 안됨, 기존 값 1 유지
map.computeIfAbsent("b", k -> 100);  // 실행됨, b=100 추가

// computeIfPresent: 키가 있을 때만 실행
map.computeIfPresent("a", (k, v) -> v + 10);  // 실행됨, a=11
map.computeIfPresent("c", (k, v) -> v + 10);  // 실행 안됨

// compute: 항상 실행
map.compute("a", (k, v) -> v == null ? 1 : v + 1);  // 항상 실행
```

### 7.2 getOrDefault

```java
// 값만 가져오고 저장은 안 함
int count = map.getOrDefault("key", 0);  // 없으면 0 반환, 저장은 X

// vs computeIfAbsent
int count = map.computeIfAbsent("key", k -> 0);  // 없으면 0 저장하고 반환
```

### 7.3 putIfAbsent

```java
// 키가 없을 때만 저장 (람다 없이 값 직접 전달)
map.putIfAbsent("key", new ArrayList<>());  // 값이 미리 생성됨!

// vs computeIfAbsent
map.computeIfAbsent("key", k -> new ArrayList<>());  // 필요할 때만 생성 (Lazy)
```

**주의**: `putIfAbsent`는 값이 필요 없어도 항상 생성됨 (성능 낭비 가능)

---

## 8. 코딩 테스트 활용 패턴

### 8.1 단어 길이별 그룹핑

```java
String[] words = {"eat", "tea", "tan", "ate", "nat", "bat"};
Map<Integer, List<String>> byLength = new HashMap<>();

for (String word : words) {
    byLength.computeIfAbsent(word.length(), k -> new ArrayList<>()).add(word);
}
```

### 8.2 아나그램 그룹핑

```java
String[] words = {"eat", "tea", "tan", "ate", "nat", "bat"};
Map<String, List<String>> anagrams = new HashMap<>();

for (String word : words) {
    char[] chars = word.toCharArray();
    Arrays.sort(chars);
    String key = new String(chars);  // "eat" → "aet"
    anagrams.computeIfAbsent(key, k -> new ArrayList<>()).add(word);
}
// 결과: {"aet": ["eat", "tea", "ate"], "ant": ["tan", "nat"], "abt": ["bat"]}
```

### 8.3 그래프 인접 리스트

```java
int[][] edges = {{0, 1}, {0, 2}, {1, 2}, {2, 3}};
Map<Integer, List<Integer>> graph = new HashMap<>();

for (int[] edge : edges) {
    graph.computeIfAbsent(edge[0], k -> new ArrayList<>()).add(edge[1]);
    graph.computeIfAbsent(edge[1], k -> new ArrayList<>()).add(edge[0]);  // 무방향
}
```

### 8.4 빈도수 카운팅 (Map<K, Integer>)

```java
// 빈도수 카운팅은 merge가 더 적합
String[] items = {"a", "b", "a", "c", "a", "b"};
Map<String, Integer> count = new HashMap<>();

for (String item : items) {
    count.merge(item, 1, Integer::sum);
}
// 결과: {a=3, b=2, c=1}
```

---

## 9. 요약

| 질문 | 답변 |
|------|------|
| `k`는 뭐냐? | `computeIfAbsent`의 첫 번째 인자(key)가 람다로 전달됨 |
| 왜 사용 안 해도 필요? | `Function<K,V>` 인터페이스가 파라미터를 요구함 |
| 언제 실행됨? | **key가 없을 때만** 람다가 실행됨 |
| 장점은? | 코드 간결, 원자적 연산, Lazy 생성 |

```java
// 핵심 패턴
map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
//                   │    │         │              │
//                   │    │         │              └─ 3. 반환된 리스트에 추가
//                   │    │         └─ 2. 없으면 새 리스트 생성
//                   │    └─ key가 람다의 파라미터로 전달됨 (안 써도 됨)
//                   └─ 1. 이 키가 있는지 확인
```
