# Java 코딩테스트 필수 클래스 치트시트

> 코딩테스트에서 자주 사용하는 Java 내장 클래스 정리

---

## 목차

### 입출력
- [BufferedReader / BufferedWriter](#bufferedreader--bufferedwriter)
- [StringTokenizer](#stringtokenizer)
- [StringBuilder](#stringbuilder)

### 자료구조
- [배열 (Array)](#배열-array)
- [ArrayList](#arraylist)
- [LinkedList](#linkedlist)
- [HashMap](#hashmap)
- [HashSet](#hashset)
- [TreeMap](#treemap)
- [TreeSet](#treeset)
- [PriorityQueue (힙)](#priorityqueue-힙)
- [Deque (덱)](#deque-덱)
- [Stack](#stack)

### 유틸리티
- [Arrays](#arrays)
- [Collections](#collections)
- [Math](#math)
- [String 메서드](#string-메서드)
- [Character](#character)
- [Integer / Long](#integer--long)
- [BigInteger](#biginteger)

### 부록
- [자료구조 선택 가이드](#자료구조-선택-가이드)
- [시간복잡도 정리](#시간복잡도-정리)
- [자주 쓰는 코드 템플릿](#자주-쓰는-코드-템플릿)

---

# 입출력

## BufferedReader / BufferedWriter

Scanner보다 **훨씬 빠름**. 대용량 입력 필수.

```java
import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out));

        // 한 줄 읽기
        String line = br.readLine();

        // 정수 하나 읽기
        int n = Integer.parseInt(br.readLine());

        // 공백으로 구분된 여러 정수 읽기
        StringTokenizer st = new StringTokenizer(br.readLine());
        int a = Integer.parseInt(st.nextToken());
        int b = Integer.parseInt(st.nextToken());

        // 출력 (println보다 빠름)
        bw.write(a + b + "\n");

        // 반드시 flush/close!
        bw.flush();
        bw.close();
        br.close();
    }
}
```

### 입력 패턴 모음

```java
// N개의 정수를 배열로
int n = Integer.parseInt(br.readLine());
int[] arr = new int[n];
StringTokenizer st = new StringTokenizer(br.readLine());
for (int i = 0; i < n; i++) {
    arr[i] = Integer.parseInt(st.nextToken());
}

// N x M 2차원 배열
int n = Integer.parseInt(br.readLine());
int m = Integer.parseInt(br.readLine());
int[][] grid = new int[n][m];
for (int i = 0; i < n; i++) {
    st = new StringTokenizer(br.readLine());
    for (int j = 0; j < m; j++) {
        grid[i][j] = Integer.parseInt(st.nextToken());
    }
}

// 문자 그리드 (공백 없는 경우)
char[][] map = new char[n][m];
for (int i = 0; i < n; i++) {
    map[i] = br.readLine().toCharArray();
}

// Long 타입 (큰 수)
long big = Long.parseLong(br.readLine());
```

---

## StringTokenizer

문자열을 구분자로 분리. `split()`보다 빠름.

```java
import java.util.StringTokenizer;

// 기본: 공백 구분
StringTokenizer st = new StringTokenizer("1 2 3 4 5");
while (st.hasMoreTokens()) {
    System.out.println(st.nextToken());  // 1, 2, 3, 4, 5
}

// 커스텀 구분자
st = new StringTokenizer("1,2,3,4,5", ",");

// 여러 구분자
st = new StringTokenizer("1,2;3 4", ",; ");

// 토큰 개수
st = new StringTokenizer("a b c");
int count = st.countTokens();  // 3
```

---

## StringBuilder

문자열 연결 시 **필수**. String + 연산은 O(n^2).

```java
StringBuilder sb = new StringBuilder();

// 추가
sb.append("Hello");
sb.append(" ");
sb.append("World");
sb.append(123);       // 숫자도 가능
sb.append("\n");      // 줄바꿈

// 체이닝
sb.append("a").append("b").append("c");

// 특정 위치에 삽입
sb.insert(0, "Start: ");

// 삭제
sb.delete(0, 5);      // 인덱스 0~4 삭제
sb.deleteCharAt(0);   // 인덱스 0 삭제

// 뒤집기
sb.reverse();

// 문자열로 변환
String result = sb.toString();

// 길이 / 비우기
int len = sb.length();
sb.setLength(0);      // 비우기 (재사용)
```

### 출력 최적화

```java
// ❌ 느림: 매번 출력
for (int i = 0; i < 100000; i++) {
    System.out.println(i);
}

// ✅ 빠름: StringBuilder로 모아서 한 번에
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 100000; i++) {
    sb.append(i).append("\n");
}
System.out.print(sb);
```

---

# 자료구조

## 배열 (Array)

```java
// 선언 및 초기화
int[] arr = new int[10];           // 크기 10, 0으로 초기화
int[] arr2 = {1, 2, 3, 4, 5};      // 값으로 초기화
int[] arr3 = new int[]{1, 2, 3};   // 값으로 초기화

// 2차원 배열
int[][] grid = new int[3][4];      // 3행 4열
int[][] grid2 = {{1,2}, {3,4}};

// 배열 복사
int[] copy = arr.clone();
int[] copy2 = Arrays.copyOf(arr, arr.length);
int[] partial = Arrays.copyOfRange(arr, 1, 4);  // 인덱스 1~3

// 배열 채우기
Arrays.fill(arr, -1);              // 전체를 -1로
Arrays.fill(arr, 0, 5, 99);        // 인덱스 0~4를 99로

// 2차원 배열 채우기
for (int[] row : grid) {
    Arrays.fill(row, -1);
}
```

---

## ArrayList

**가변 크기 배열**. 인덱스 접근 O(1), 삽입/삭제 O(n).

```java
import java.util.ArrayList;
import java.util.List;

// 생성
List<Integer> list = new ArrayList<>();
List<Integer> list2 = new ArrayList<>(100);  // 초기 용량 지정

// 추가
list.add(10);           // 끝에 추가
list.add(0, 5);         // 인덱스 0에 삽입

// 조회
int val = list.get(0);  // 인덱스로 접근
int size = list.size();
boolean empty = list.isEmpty();

// 수정
list.set(0, 100);       // 인덱스 0을 100으로 변경

// 삭제
list.remove(0);                    // 인덱스로 삭제
list.remove(Integer.valueOf(10));  // 값으로 삭제 (주의!)

// 검색
boolean has = list.contains(10);
int idx = list.indexOf(10);        // 없으면 -1

// 순회
for (int num : list) {
    System.out.println(num);
}

for (int i = 0; i < list.size(); i++) {
    System.out.println(list.get(i));
}

// 정렬
Collections.sort(list);                    // 오름차순
Collections.sort(list, Collections.reverseOrder());  // 내림차순

// 배열로 변환
Integer[] arr = list.toArray(new Integer[0]);
// int[]로 변환
int[] intArr = list.stream().mapToInt(i -> i).toArray();

// 배열에서 ArrayList로
List<Integer> fromArr = new ArrayList<>(Arrays.asList(1, 2, 3));
```

---

## LinkedList

**양방향 연결 리스트**. 삽입/삭제 O(1), 인덱스 접근 O(n).
**Deque로도 사용 가능** (앞/뒤 삽입 삭제).

```java
import java.util.LinkedList;

LinkedList<Integer> list = new LinkedList<>();

// ArrayList와 동일한 메서드 + 추가 메서드
list.addFirst(1);       // 맨 앞에 추가
list.addLast(2);        // 맨 뒤에 추가
list.removeFirst();     // 맨 앞 삭제
list.removeLast();      // 맨 뒤 삭제
int first = list.getFirst();
int last = list.getLast();
```

---

## HashMap

**키-값 저장**. 조회/삽입/삭제 O(1).

```java
import java.util.HashMap;
import java.util.Map;

Map<String, Integer> map = new HashMap<>();

// 추가/수정
map.put("apple", 3);
map.put("banana", 5);
map.put("apple", 10);   // 덮어쓰기

// 조회
int val = map.get("apple");           // 10
int val2 = map.getOrDefault("grape", 0);  // 없으면 기본값

// 존재 확인
boolean hasKey = map.containsKey("apple");
boolean hasVal = map.containsValue(10);

// 삭제
map.remove("apple");

// 크기
int size = map.size();
boolean empty = map.isEmpty();

// 순회
for (String key : map.keySet()) {
    System.out.println(key + ": " + map.get(key));
}

for (Map.Entry<String, Integer> entry : map.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}

// values만
for (int value : map.values()) {
    System.out.println(value);
}
```

### 자주 쓰는 패턴

```java
// 빈도수 세기
String[] words = {"a", "b", "a", "c", "a", "b"};
Map<String, Integer> freq = new HashMap<>();
for (String word : words) {
    freq.put(word, freq.getOrDefault(word, 0) + 1);
}
// {a=3, b=2, c=1}

// 그룹핑
Map<Integer, List<String>> groups = new HashMap<>();
for (String s : words) {
    int len = s.length();
    groups.computeIfAbsent(len, k -> new ArrayList<>()).add(s);
}
```

---

## HashSet

**중복 없는 집합**. 추가/삭제/조회 O(1).

```java
import java.util.HashSet;
import java.util.Set;

Set<Integer> set = new HashSet<>();

// 추가 (중복 무시)
set.add(1);
set.add(2);
set.add(1);  // 무시됨
// set = {1, 2}

// 존재 확인
boolean has = set.contains(1);

// 삭제
set.remove(1);

// 크기
int size = set.size();

// 순회 (순서 없음!)
for (int num : set) {
    System.out.println(num);
}

// 집합 연산
Set<Integer> a = new HashSet<>(Arrays.asList(1, 2, 3));
Set<Integer> b = new HashSet<>(Arrays.asList(2, 3, 4));

// 합집합
Set<Integer> union = new HashSet<>(a);
union.addAll(b);  // {1, 2, 3, 4}

// 교집합
Set<Integer> inter = new HashSet<>(a);
inter.retainAll(b);  // {2, 3}

// 차집합
Set<Integer> diff = new HashSet<>(a);
diff.removeAll(b);  // {1}

// 리스트에서 중복 제거
List<Integer> list = Arrays.asList(1, 2, 2, 3, 3, 3);
List<Integer> unique = new ArrayList<>(new HashSet<>(list));
```

---

## TreeMap

**정렬된 Map**. 키 기준 자동 정렬. 모든 연산 O(log n).

```java
import java.util.TreeMap;

TreeMap<Integer, String> map = new TreeMap<>();

map.put(3, "three");
map.put(1, "one");
map.put(2, "two");
// 순회 시: 1, 2, 3 순서

// 정렬 관련 메서드
int first = map.firstKey();         // 최소 키
int last = map.lastKey();           // 최대 키
Map.Entry<Integer, String> firstEntry = map.firstEntry();
Map.Entry<Integer, String> lastEntry = map.lastEntry();

// 범위 검색
Integer lower = map.lowerKey(2);    // 2보다 작은 키 중 최대 (1)
Integer higher = map.higherKey(2);  // 2보다 큰 키 중 최소 (3)
Integer floor = map.floorKey(2);    // 2 이하 중 최대 (2)
Integer ceiling = map.ceilingKey(2);// 2 이상 중 최소 (2)

// 부분 맵
SortedMap<Integer, String> sub = map.subMap(1, 3);  // 키 1~2

// 역순
NavigableMap<Integer, String> desc = map.descendingMap();

// 내림차순 TreeMap
TreeMap<Integer, String> descMap = new TreeMap<>(Collections.reverseOrder());
```

---

## TreeSet

**정렬된 Set**. 자동 정렬. 모든 연산 O(log n).

```java
import java.util.TreeSet;

TreeSet<Integer> set = new TreeSet<>();

set.add(5);
set.add(1);
set.add(3);
// 순회 시: 1, 3, 5 순서

// 정렬 관련 메서드
int first = set.first();       // 최솟값
int last = set.last();         // 최댓값

// 범위 검색
Integer lower = set.lower(3);   // 3보다 작은 것 중 최대 (1)
Integer higher = set.higher(3); // 3보다 큰 것 중 최소 (5)
Integer floor = set.floor(3);   // 3 이하 중 최대 (3)
Integer ceiling = set.ceiling(3);// 3 이상 중 최소 (3)

// 삭제하면서 반환
int polledFirst = set.pollFirst();  // 최솟값 삭제 후 반환
int polledLast = set.pollLast();    // 최댓값 삭제 후 반환

// 부분 집합
SortedSet<Integer> sub = set.subSet(1, 5);  // 1 이상 5 미만

// 내림차순
NavigableSet<Integer> desc = set.descendingSet();

// 내림차순 TreeSet
TreeSet<Integer> descSet = new TreeSet<>(Collections.reverseOrder());
```

---

## PriorityQueue (힙)

**우선순위 큐**. 기본은 **최소 힙**. 삽입/삭제 O(log n), 최솟값 조회 O(1).

```java
import java.util.PriorityQueue;

// 최소 힙 (기본)
PriorityQueue<Integer> minHeap = new PriorityQueue<>();
minHeap.offer(5);
minHeap.offer(1);
minHeap.offer(3);
int min = minHeap.peek();   // 1 (제거 안 함)
int polled = minHeap.poll(); // 1 (제거)

// 최대 힙
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
// 또는
PriorityQueue<Integer> maxHeap2 = new PriorityQueue<>((a, b) -> b - a);

// 크기
int size = minHeap.size();
boolean empty = minHeap.isEmpty();

// 전부 꺼내기 (정렬된 순서)
while (!minHeap.isEmpty()) {
    System.out.println(minHeap.poll());
}
```

### 커스텀 정렬

```java
// 객체 정렬
class Task {
    int priority;
    String name;
    Task(int p, String n) { priority = p; name = n; }
}

// 우선순위 낮은 것부터 (오름차순)
PriorityQueue<Task> pq = new PriorityQueue<>(
    (a, b) -> a.priority - b.priority
);

// 또는 Comparator.comparingInt
PriorityQueue<Task> pq2 = new PriorityQueue<>(
    Comparator.comparingInt(t -> t.priority)
);

// 배열/리스트 정렬
PriorityQueue<int[]> pq3 = new PriorityQueue<>(
    (a, b) -> a[0] - b[0]  // 첫 번째 원소 기준
);
```

---

## Deque (덱)

**양방향 큐**. 앞/뒤 삽입/삭제 O(1). BFS, 슬라이딩 윈도우에 활용.

```java
import java.util.ArrayDeque;
import java.util.Deque;

Deque<Integer> deque = new ArrayDeque<>();

// 앞에 추가/삭제
deque.addFirst(1);      // 또는 offerFirst
deque.removeFirst();    // 또는 pollFirst
int front = deque.peekFirst();

// 뒤에 추가/삭제
deque.addLast(2);       // 또는 offerLast
deque.removeLast();     // 또는 pollLast
int back = deque.peekLast();

// 큐처럼 사용 (FIFO)
deque.offer(1);         // 뒤에 추가
deque.poll();           // 앞에서 제거

// 스택처럼 사용 (LIFO)
deque.push(1);          // 앞에 추가
deque.pop();            // 앞에서 제거
```

### BFS에서 사용

```java
Deque<int[]> queue = new ArrayDeque<>();
queue.offer(new int[]{0, 0});  // 시작점

while (!queue.isEmpty()) {
    int[] cur = queue.poll();
    // 인접 노드 추가
    queue.offer(new int[]{cur[0] + 1, cur[1]});
}
```

---

## Stack

**후입선출(LIFO)**. `Deque` 사용 권장 (Stack은 레거시).

```java
import java.util.ArrayDeque;
import java.util.Deque;

// 권장: ArrayDeque 사용
Deque<Integer> stack = new ArrayDeque<>();

stack.push(1);          // 삽입
stack.push(2);
int top = stack.peek(); // 맨 위 확인 (제거 안 함)
int popped = stack.pop(); // 맨 위 제거

boolean empty = stack.isEmpty();
int size = stack.size();

// 레거시 Stack 클래스 (비권장)
// Stack<Integer> stack = new Stack<>();
```

### 스택 활용 예제

```java
// 괄호 검사
public boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        if (c == '(' || c == '{' || c == '[') {
            stack.push(c);
        } else {
            if (stack.isEmpty()) return false;
            char top = stack.pop();
            if (c == ')' && top != '(') return false;
            if (c == '}' && top != '{') return false;
            if (c == ']' && top != '[') return false;
        }
    }
    return stack.isEmpty();
}
```

---

# 유틸리티

## Arrays

배열 조작 유틸리티.

```java
import java.util.Arrays;

int[] arr = {5, 2, 8, 1, 9};

// 정렬
Arrays.sort(arr);  // [1, 2, 5, 8, 9]

// 부분 정렬
Arrays.sort(arr, 1, 4);  // 인덱스 1~3만 정렬

// 내림차순 (Integer[] 필요)
Integer[] arr2 = {5, 2, 8, 1, 9};
Arrays.sort(arr2, Collections.reverseOrder());

// 2차원 배열 정렬
int[][] intervals = {{3,4}, {1,2}, {2,3}};
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);  // 첫 번째 원소 기준

// 이진 탐색 (정렬된 배열에서)
int idx = Arrays.binarySearch(arr, 5);  // 있으면 인덱스, 없으면 -(삽입위치+1)

// 채우기
Arrays.fill(arr, 0);
Arrays.fill(arr, 0, 3, -1);  // 인덱스 0~2를 -1로

// 복사
int[] copy = Arrays.copyOf(arr, arr.length);
int[] copy2 = Arrays.copyOfRange(arr, 1, 4);

// 비교
boolean eq = Arrays.equals(arr, copy);
boolean eq2 = Arrays.deepEquals(grid1, grid2);  // 2차원 배열

// 문자열 변환
String str = Arrays.toString(arr);       // [1, 2, 5, 8, 9]
String str2 = Arrays.deepToString(grid); // 2차원

// 스트림 변환
int sum = Arrays.stream(arr).sum();
int max = Arrays.stream(arr).max().getAsInt();
```

---

## Collections

컬렉션 조작 유틸리티.

```java
import java.util.Collections;

List<Integer> list = new ArrayList<>(Arrays.asList(5, 2, 8, 1, 9));

// 정렬
Collections.sort(list);                           // 오름차순
Collections.sort(list, Collections.reverseOrder()); // 내림차순
Collections.sort(list, (a, b) -> b - a);          // 커스텀

// 역순
Collections.reverse(list);

// 섞기
Collections.shuffle(list);

// 최대/최소
int max = Collections.max(list);
int min = Collections.min(list);

// 이진 탐색 (정렬된 리스트에서)
int idx = Collections.binarySearch(list, 5);

// 빈도 수
int count = Collections.frequency(list, 5);

// 치환
Collections.replaceAll(list, 1, 100);  // 1을 100으로

// 스왑
Collections.swap(list, 0, 1);

// N개로 채운 리스트
List<Integer> zeros = new ArrayList<>(Collections.nCopies(10, 0));

// 불변 컬렉션
List<Integer> immutable = Collections.unmodifiableList(list);
```

---

## Math

수학 연산.

```java
// 최대/최소
int max = Math.max(10, 20);
int min = Math.min(10, 20);

// 절댓값
int abs = Math.abs(-10);

// 제곱/제곱근
double pow = Math.pow(2, 10);    // 2^10 = 1024.0
double sqrt = Math.sqrt(16);     // 4.0

// 올림/내림/반올림
long ceil = Math.ceil(3.2);      // 4
long floor = Math.floor(3.8);    // 3
long round = Math.round(3.5);    // 4

// 로그
double log = Math.log(10);       // 자연로그
double log10 = Math.log10(100);  // 상용로그 (2.0)

// 랜덤 (0.0 ~ 1.0)
double rand = Math.random();
int randInt = (int)(Math.random() * 100);  // 0 ~ 99

// 상수
double pi = Math.PI;
double e = Math.E;
```

### GCD / LCM

```java
// 최대공약수 (유클리드 알고리즘)
public static int gcd(int a, int b) {
    while (b != 0) {
        int temp = b;
        b = a % b;
        a = temp;
    }
    return a;
}

// 재귀 버전
public static int gcdRec(int a, int b) {
    return b == 0 ? a : gcd(b, a % b);
}

// 최소공배수
public static int lcm(int a, int b) {
    return a / gcd(a, b) * b;  // 오버플로우 방지
}
```

---

## String 메서드

```java
String s = "Hello World";

// 길이
int len = s.length();

// 문자 접근
char c = s.charAt(0);  // 'H'

// 부분 문자열
String sub = s.substring(0, 5);   // "Hello"
String sub2 = s.substring(6);     // "World"

// 검색
int idx = s.indexOf("o");         // 4 (첫 번째)
int idx2 = s.lastIndexOf("o");    // 7 (마지막)
boolean has = s.contains("World");

// 시작/끝 확인
boolean start = s.startsWith("Hello");
boolean end = s.endsWith("World");

// 변환
String upper = s.toUpperCase();
String lower = s.toLowerCase();
String trimmed = s.trim();        // 앞뒤 공백 제거
String replaced = s.replace("o", "0");  // 모든 'o'를 '0'으로

// 분할
String[] parts = s.split(" ");    // ["Hello", "World"]
String[] parts2 = s.split("");    // 각 문자로 분할

// 비교
boolean eq = s.equals("Hello World");
boolean eqIgnore = s.equalsIgnoreCase("hello world");
int cmp = s.compareTo("Hello");   // 사전순 비교

// 문자 배열
char[] chars = s.toCharArray();
String fromChars = new String(chars);

// 포맷
String formatted = String.format("%d + %d = %d", 1, 2, 3);

// 반복 (Java 11+)
String repeated = "ab".repeat(3);  // "ababab"

// 빈 문자열 확인
boolean empty = s.isEmpty();        // 길이가 0인지
boolean blank = s.isBlank();        // 공백만 있는지 (Java 11+)

// join
String joined = String.join(", ", "a", "b", "c");  // "a, b, c"
String joined2 = String.join("-", list);           // 리스트 연결
```

---

## Character

문자 판별/변환.

```java
char c = 'A';

// 판별
boolean isDigit = Character.isDigit('5');       // true
boolean isLetter = Character.isLetter('A');     // true
boolean isLetterOrDigit = Character.isLetterOrDigit('A');
boolean isUpper = Character.isUpperCase('A');   // true
boolean isLower = Character.isLowerCase('a');   // true
boolean isWhitespace = Character.isWhitespace(' ');

// 변환
char upper = Character.toUpperCase('a');  // 'A'
char lower = Character.toLowerCase('A');  // 'a'

// 숫자 변환
int digit = Character.getNumericValue('7');  // 7
int digit2 = '7' - '0';                      // 7 (더 빠름)

// 문자 → 정수 → 문자
char c2 = (char)('a' + 2);  // 'c'
```

---

## Integer / Long

숫자 파싱/변환.

```java
// 문자열 → 숫자
int num = Integer.parseInt("123");
long big = Long.parseLong("123456789012");

// 숫자 → 문자열
String s = Integer.toString(123);
String s2 = String.valueOf(123);

// 진법 변환
int fromBinary = Integer.parseInt("1010", 2);    // 10
int fromHex = Integer.parseInt("FF", 16);        // 255
String toBinary = Integer.toBinaryString(10);    // "1010"
String toHex = Integer.toHexString(255);         // "ff"
String toOctal = Integer.toOctalString(8);       // "10"

// 비트 연산
int bitCount = Integer.bitCount(15);             // 1의 개수: 4
int highest = Integer.highestOneBit(10);         // 8 (1000)
int lowest = Integer.lowestOneBit(10);           // 2 (0010)

// 최대/최소값
int maxInt = Integer.MAX_VALUE;   // 2,147,483,647
int minInt = Integer.MIN_VALUE;   // -2,147,483,648
long maxLong = Long.MAX_VALUE;    // 9,223,372,036,854,775,807

// 비교 (오버플로우 안전)
int cmp = Integer.compare(a, b);  // a < b: -1, a == b: 0, a > b: 1
```

---

## BigInteger

**큰 정수 연산**. 피보나치, 팩토리얼 등.

```java
import java.math.BigInteger;

BigInteger a = new BigInteger("123456789012345678901234567890");
BigInteger b = BigInteger.valueOf(100);

// 사칙연산
BigInteger sum = a.add(b);
BigInteger diff = a.subtract(b);
BigInteger prod = a.multiply(b);
BigInteger quot = a.divide(b);
BigInteger rem = a.mod(b);
BigInteger[] divAndRem = a.divideAndRemainder(b);

// 거듭제곱
BigInteger pow = a.pow(10);

// 비교
int cmp = a.compareTo(b);  // -1, 0, 1
boolean eq = a.equals(b);

// GCD
BigInteger gcd = a.gcd(b);

// 상수
BigInteger zero = BigInteger.ZERO;
BigInteger one = BigInteger.ONE;
BigInteger ten = BigInteger.TEN;

// 변환
long longVal = a.longValue();      // 주의: 오버플로우 가능
String str = a.toString();
String binary = a.toString(2);     // 2진수 문자열
```

### 팩토리얼 예제

```java
public static BigInteger factorial(int n) {
    BigInteger result = BigInteger.ONE;
    for (int i = 2; i <= n; i++) {
        result = result.multiply(BigInteger.valueOf(i));
    }
    return result;
}
```

---

# 부록

## 자료구조 선택 가이드

```
어떤 자료구조를 써야 할까?

순서가 중요하고 인덱스 접근이 필요?
├── Yes → ArrayList / 배열
└── No ──┐
         ▼
중복을 허용하나?
├── No (중복 제거) ──┐
│                    ▼
│              정렬이 필요?
│              ├── Yes → TreeSet
│              └── No → HashSet
│
└── Yes ──┐
          ▼
Key-Value 쌍인가?
├── Yes ──┐
│         ▼
│    정렬이 필요?
│    ├── Yes → TreeMap
│    └── No → HashMap
│
└── No ──┐
         ▼
우선순위 (최대/최소) 필요?
├── Yes → PriorityQueue
│
└── No ──┐
         ▼
앞뒤 모두 삽입/삭제?
├── Yes → Deque (ArrayDeque)
│
└── No ──┐
         ▼
LIFO (스택)?
├── Yes → Deque (push/pop)
│
└── FIFO (큐)?
    └── Yes → Deque (offer/poll)
```

---

## 시간복잡도 정리

| 자료구조 | 접근 | 검색 | 삽입 | 삭제 |
|---------|------|------|------|------|
| 배열 | O(1) | O(n) | O(n) | O(n) |
| ArrayList | O(1) | O(n) | O(n) | O(n) |
| LinkedList | O(n) | O(n) | O(1) | O(1) |
| HashMap | - | O(1) | O(1) | O(1) |
| HashSet | - | O(1) | O(1) | O(1) |
| TreeMap | - | O(log n) | O(log n) | O(log n) |
| TreeSet | - | O(log n) | O(log n) | O(log n) |
| PriorityQueue | O(1)* | O(n) | O(log n) | O(log n) |
| Deque | O(1)** | O(n) | O(1) | O(1) |

\* peek만 O(1), 임의 접근은 O(n)
\** 양 끝만 O(1), 임의 접근은 O(n)

---

## 자주 쓰는 코드 템플릿

### 기본 템플릿

```java
import java.io.*;
import java.util.*;

public class Main {
    static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    static StringBuilder sb = new StringBuilder();
    static StringTokenizer st;

    public static void main(String[] args) throws IOException {
        int n = Integer.parseInt(br.readLine());

        // 풀이

        System.out.print(sb);
    }
}
```

### 2차원 방향 이동 (BFS/DFS)

```java
// 상하좌우
int[] dx = {-1, 1, 0, 0};
int[] dy = {0, 0, -1, 1};

// 8방향
int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

// 범위 체크
for (int d = 0; d < 4; d++) {
    int nx = x + dx[d];
    int ny = y + dy[d];
    if (nx >= 0 && nx < n && ny >= 0 && ny < m) {
        // 유효한 좌표
    }
}
```

### BFS 템플릿

```java
public static void bfs(int startX, int startY) {
    Deque<int[]> queue = new ArrayDeque<>();
    boolean[][] visited = new boolean[n][m];

    queue.offer(new int[]{startX, startY});
    visited[startX][startY] = true;

    while (!queue.isEmpty()) {
        int[] cur = queue.poll();
        int x = cur[0], y = cur[1];

        for (int d = 0; d < 4; d++) {
            int nx = x + dx[d];
            int ny = y + dy[d];

            if (nx < 0 || nx >= n || ny < 0 || ny >= m) continue;
            if (visited[nx][ny]) continue;

            visited[nx][ny] = true;
            queue.offer(new int[]{nx, ny});
        }
    }
}
```

### DFS 템플릿

```java
// 재귀 DFS
public static void dfs(int x, int y) {
    visited[x][y] = true;

    for (int d = 0; d < 4; d++) {
        int nx = x + dx[d];
        int ny = y + dy[d];

        if (nx < 0 || nx >= n || ny < 0 || ny >= m) continue;
        if (visited[nx][ny]) continue;

        dfs(nx, ny);
    }
}

// 스택 DFS
public static void dfsStack(int startX, int startY) {
    Deque<int[]> stack = new ArrayDeque<>();
    boolean[][] visited = new boolean[n][m];

    stack.push(new int[]{startX, startY});

    while (!stack.isEmpty()) {
        int[] cur = stack.pop();
        int x = cur[0], y = cur[1];

        if (visited[x][y]) continue;
        visited[x][y] = true;

        for (int d = 0; d < 4; d++) {
            int nx = x + dx[d];
            int ny = y + dy[d];

            if (nx < 0 || nx >= n || ny < 0 || ny >= m) continue;
            if (visited[nx][ny]) continue;

            stack.push(new int[]{nx, ny});
        }
    }
}
```

### 이진 탐색 템플릿

```java
// lower bound (target 이상인 첫 위치)
public static int lowerBound(int[] arr, int target) {
    int lo = 0, hi = arr.length;
    while (lo < hi) {
        int mid = (lo + hi) / 2;
        if (arr[mid] >= target) {
            hi = mid;
        } else {
            lo = mid + 1;
        }
    }
    return lo;
}

// upper bound (target 초과인 첫 위치)
public static int upperBound(int[] arr, int target) {
    int lo = 0, hi = arr.length;
    while (lo < hi) {
        int mid = (lo + hi) / 2;
        if (arr[mid] > target) {
            hi = mid;
        } else {
            lo = mid + 1;
        }
    }
    return lo;
}

// 파라메트릭 서치 (조건을 만족하는 최솟값)
public static int parametric(int lo, int hi) {
    while (lo < hi) {
        int mid = (lo + hi) / 2;
        if (check(mid)) {  // 조건 만족?
            hi = mid;
        } else {
            lo = mid + 1;
        }
    }
    return lo;
}
```

### 순열/조합 템플릿

```java
// 순열 (nPr)
public static void permutation(int[] arr, int depth, int r, boolean[] visited, int[] result) {
    if (depth == r) {
        System.out.println(Arrays.toString(result));
        return;
    }

    for (int i = 0; i < arr.length; i++) {
        if (!visited[i]) {
            visited[i] = true;
            result[depth] = arr[i];
            permutation(arr, depth + 1, r, visited, result);
            visited[i] = false;
        }
    }
}

// 조합 (nCr)
public static void combination(int[] arr, int start, int depth, int r, int[] result) {
    if (depth == r) {
        System.out.println(Arrays.toString(result));
        return;
    }

    for (int i = start; i < arr.length; i++) {
        result[depth] = arr[i];
        combination(arr, i + 1, depth + 1, r, result);
    }
}

// 사용
int[] arr = {1, 2, 3, 4};
permutation(arr, 0, 2, new boolean[arr.length], new int[2]);  // 4P2
combination(arr, 0, 0, 2, new int[2]);                         // 4C2
```

### 소수 판별/에라토스테네스 체

```java
// 소수 판별
public static boolean isPrime(int n) {
    if (n < 2) return false;
    if (n == 2) return true;
    if (n % 2 == 0) return false;
    for (int i = 3; i * i <= n; i += 2) {
        if (n % i == 0) return false;
    }
    return true;
}

// 에라토스테네스의 체
public static boolean[] sieve(int n) {
    boolean[] isPrime = new boolean[n + 1];
    Arrays.fill(isPrime, true);
    isPrime[0] = isPrime[1] = false;

    for (int i = 2; i * i <= n; i++) {
        if (isPrime[i]) {
            for (int j = i * i; j <= n; j += i) {
                isPrime[j] = false;
            }
        }
    }
    return isPrime;
}
```

---

## 마무리 체크리스트

```
코딩테스트 전 확인:

[ ] BufferedReader/StringTokenizer 사용 (Scanner X)
[ ] StringBuilder로 출력 모으기
[ ] int 범위 초과 시 long 사용
[ ] 배열 크기 여유있게 (+1 또는 +10)
[ ] 0-indexed vs 1-indexed 확인
[ ] 엣지 케이스: 빈 입력, 최솟값, 최댓값
[ ] 시간복잡도 계산 (1억 연산 ≈ 1초)
```