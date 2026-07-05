# 코딩테스트 통합 정리 (설명 + 예제 코드)

> Java 백엔드 엔지니어용 코테 대비 통합본. 알고리즘 패턴 · 자바 문법 · BFS/DFS · 다익스트라 · 백트래킹 · SQL(MySQL)

> 코테 코드는 HackerRank(Java 15) 호환. 실무는 최신 Java / Spring Boot 기준 설명 병기.

**목차**

- 1부. 알고리즘 패턴 12선
- 2부. 자바 문법 치트시트
- 3부. BFS / DFS
- 4부. 다익스트라
- 5부. 백트래킹
- 6부. SQL (MySQL)

---

\newpage

## 1부. 알고리즘 패턴 12선

> 대상: Java 백엔드 엔지니어
> **호환성: HackerRank 환경(Java 15까지) 기준으로 작성** — 예제 코드는 별도 플래그 없이 그대로 컴파일됨
> 각 기법마다 **대표 문제 → 완성 코드 → 핵심 설명 → 복잡도 → 신호**를 붙여 실전에서 바로 꺼내 쓸 수 있게 정리

> **Java 버전 주의 (HackerRank는 Java 15까지 지원)**
> - `record`는 Java 14·15에서 **preview**라 `--enable-preview` 없이는 컴파일 실패 → 이 문서는 **일반 클래스**로 작성. (Java 16+에선 record로 축약 가능)
> - `Stream.toList()`는 Java 16+ → `collect(Collectors.toList())` 사용.
> - `switch` 표현식(화살표 `->`), text block(`"""`), `var`는 Java 14/15에서 표준이라 **사용 가능**.

---

1. [누적합 (Prefix Sum)](#1-누적합-prefix-sum)
2. [차이 배열 (Difference Array)](#2-차이-배열-difference-array)
3. [투 포인터 (Two Pointers)](#3-투-포인터-two-pointers)
4. [해시맵으로 짝 찾기 (Two Sum)](#4-해시맵으로-짝-찾기-two-sum)
5. [XOR 트릭](#5-xor-트릭)
6. [크기 k 힙 (Top-K)](#6-크기-k-힙-top-k)
7. [정렬 후 인접 비교](#7-정렬-후-인접-비교)
8. [답을 이진 탐색 (Parametric Search)](#8-답을-이진-탐색-parametric-search)
9. [단조 스택 (Monotonic Stack)](#9-단조-스택-monotonic-stack)
10. [Union-Find (Disjoint Set)](#10-union-find-disjoint-set)
11. [좌표 압축 (Coordinate Compression)](#11-좌표-압축-coordinate-compression)
12. [비트마스크 (Bitmask)](#12-비트마스크-bitmask)
13. [보너스: 빈도 배열로 k번째 값](#보너스-빈도-배열로-k번째-값)

---

### 1. 누적합 (Prefix Sum)

**대표 문제**: 배열 `arr`이 주어지고, "구간 [l, r]의 합"을 여러 번(q개) 물어본다. 각 쿼리를 빠르게 답하라.

**아이디어**: 매 쿼리마다 구간을 더하면 쿼리당 O(n). 미리 "0번부터 i번 직전까지의 합"을 담은 누적합 배열을 만들어두면, 구간 합은 뺄셈 한 번으로 O(1)에 나온다.

```java
public class PrefixSum {
    private final long[] prefix;

    public PrefixSum(int[] arr) {
        // prefix[i] = arr[0] + ... + arr[i-1]. 크기를 n+1로 잡아 경계 처리를 단순화
        prefix = new long[arr.length + 1];
        for (int i = 0; i < arr.length; i++) {
            prefix[i + 1] = prefix[i] + arr[i];   // 이전 누적 + 현재 원소
        }
    }

    // 구간 [l, r] 합 (양끝 포함). O(1)
    public long rangeSum(int l, int r) {
        return prefix[r + 1] - prefix[l];
    }
}
```

**한 줄 설명**
- `prefix` 크기를 `n+1`로 잡는 이유: `prefix[0] = 0`을 "아무것도 안 더한 상태"로 두면, `rangeSum(0, r)`도 예외 없이 `prefix[r+1] - prefix[0]`으로 처리된다. 경계 조건 분기가 사라진다.
- `long`을 쓰는 이유: 원소가 많거나 크면 합이 `int`(약 21억)를 넘는다.
- `rangeSum`이 `prefix[r+1] - prefix[l]`인 이유: `(0~r 합) - (0~l-1 합) = l~r 합`. 겹치는 앞부분이 상쇄된다.

**복잡도**: 전처리 O(n), 쿼리당 O(1)
**신호**: "구간의 합/개수를 반복해서 묻는다"
**2D 확장**: `prefix[i][j]`를 "왼쪽 위 직사각형 합"으로 정의하면 부분 직사각형 합도 O(1) (포함-배제 원리로 4개 항 가감).

---

### 2. 차이 배열 (Difference Array)

**대표 문제** (HackerRank *Array Manipulation*): 크기 n짜리 0 배열에 "구간 [l, r]에 전부 +x" 연산이 q번 들어온다. 모든 연산 후 배열의 **최댓값**을 구하라.

**아이디어**: 구간 전체를 갱신하면 O(n·q)로 터진다. 값이 아니라 **변화가 시작/끝나는 지점**만 O(1)로 기록하고, 마지막에 누적합으로 한 번에 복원한다. 누적합의 역연산이다.

```java
public static long arrayManipulation(int n, int[][] queries) {
    long[] diff = new long[n + 2];   // r+1 접근 대비 여유 크기

    for (int[] q : queries) {
        int l = q[0], r = q[1], x = q[2];
        diff[l]     += x;   // l부터 +x 효과 시작
        diff[r + 1] -= x;   // r+1부터 효과 종료(취소)
    }

    // 누적합으로 실제 값 복원하면서 최댓값 추적
    long max = 0, running = 0;
    for (int i = 1; i <= n; i++) {
        running += diff[i];          // 여기까지의 실제 값
        max = Math.max(max, running);
    }
    return max;
}
```

**한 줄 설명**
- `diff[l] += x`: "이 지점부터 값이 x만큼 높아진다"는 표시. 누적합을 돌리면 이 x가 뒤로 계속 이어진다.
- `diff[r+1] -= x`: `r+1`에서 그 x를 상쇄. 결과적으로 l~r 구간에만 +x가 적용된다.
- `running`을 누적하며 바로 `max` 갱신: 최댓값만 필요하니 실제 배열을 만들 필요 없이 훑으면서 최댓값만 들고 간다.
- `long` 필수: 여러 x가 겹쳐 쌓이면 `int` 범위를 넘는다.

**복잡도**: O(n + q) (순진한 방식 O(n·q))
**신호**: "구간 전체에 값을 더하는 연산이 여러 번" + "조회는 마지막에 몰려 있음(offline)"
**주의**: 업데이트 중간에 조회가 끼면 매번 O(n) 복원이라 무너진다. 그 경우엔 펜윅 트리.

---

### 3. 투 포인터 (Two Pointers)

**대표 문제**: **정렬된** 배열에서 두 원소의 합이 `target`인 쌍이 있는지 찾아라.

**아이디어**: 모든 쌍을 보면 O(n²). 정렬돼 있으면 양 끝에 포인터를 두고, 합이 크면 오른쪽을 줄이고 작으면 왼쪽을 키운다. 각 포인터가 한 방향으로만 움직여 O(n).

```java
public static int[] twoSumSorted(int[] arr, int target) {
    int l = 0, r = arr.length - 1;
    while (l < r) {
        int sum = arr[l] + arr[r];
        if (sum == target) {
            return new int[]{l, r};      // 찾음
        } else if (sum < target) {
            l++;                          // 합이 작으니 더 큰 값 쪽으로
        } else {
            r--;                          // 합이 크니 더 작은 값 쪽으로
        }
    }
    return new int[]{-1, -1};             // 없음
}
```

**한 줄 설명**
- 정렬이 전제인 이유: "합이 작으면 왼쪽을 키운다"가 성립하려면 오른쪽으로 갈수록 값이 커진다는 보장이 필요하다.
- `l < r` 조건: 같은 원소를 두 번 쓰지 않도록 두 포인터가 만나면 종료.
- 각 반복에서 포인터가 최소 하나는 움직이고 되돌아오지 않으므로 전체 O(n).

**슬라이딩 윈도우 변형**: "합이 target 이하인 가장 긴 구간" 같은 문제는 두 포인터로 윈도우를 넓혔다 좁혔다 한다.

```java
// 합이 target 이하인 최장 연속 구간 길이 (양수 배열)
public static int longestSubarray(int[] arr, int target) {
    int l = 0, sum = 0, best = 0;
    for (int r = 0; r < arr.length; r++) {
        sum += arr[r];                    // 오른쪽 확장
        while (sum > target && l <= r) {  // 초과하면 왼쪽 수축
            sum -= arr[l++];
        }
        best = Math.max(best, r - l + 1);
    }
    return best;
}
```

**복잡도**: O(n) (정렬 안 돼 있으면 정렬 포함 O(n log n))
**신호**: "정렬된 상태에서 쌍/구간 조건", "연속 구간의 합/길이"

---

### 4. 해시맵으로 짝 찾기 (Two Sum)

**대표 문제**: 배열에서 합이 `target`인 두 **인덱스**를 반환하라. **정렬 불가**(원래 인덱스 유지 필요).

**아이디어**: 정렬을 못 하니 투 포인터가 안 된다. 대신 "내 짝(target - 현재값)이 이미 지나갔는가?"를 해시맵으로 O(1) 조회. 과거를 기록해두고 현재 시점에 필요한 과거를 즉시 찾는다.

```java
public static int[] twoSum(int[] arr, int target) {
    Map<Integer, Integer> seen = new HashMap<>();   // 값 → 인덱스
    for (int i = 0; i < arr.length; i++) {
        int need = target - arr[i];                 // 내가 필요로 하는 짝
        if (seen.containsKey(need)) {
            return new int[]{seen.get(need), i};    // 과거에 짝이 있었음
        }
        seen.put(arr[i], i);                        // 나를 미래를 위해 기록
    }
    return new int[]{-1, -1};
}
```

**한 줄 설명**
- `seen`에 값→인덱스를 저장: 나중 원소가 자기 짝을 찾을 때 인덱스까지 바로 얻도록.
- 조회를 먼저, 저장을 나중에: `arr[i]` 자기 자신을 짝으로 잘못 매칭하는 것을 방지 (같은 인덱스 두 번 사용 금지).
- 한 번의 스캔으로 끝: 이중 루프 O(n²)가 O(n)으로.

**복잡도**: O(n) 시간, O(n) 공간
**신호**: "합/차가 특정 값인 짝", "원래 순서·인덱스를 유지해야 함"
**연결**: Non-Divisible Subset의 "나머지 버킷으로 짝 판단"도 같은 혈통.

---

### 5. XOR 트릭

**대표 문제**: 모든 수가 정확히 두 번씩 나오는데 딱 하나만 한 번 나온다. 그 수를 O(1) 공간으로 찾아라.

**아이디어**: XOR은 `a^a=0`, `a^0=a`, 교환·결합법칙 성립. 전부 XOR하면 짝지어진 것들은 소거되고 홀로 남은 것만 살아남는다.

```java
public static int findSingle(int[] arr) {
    int result = 0;
    for (int x : arr) {
        result ^= x;      // 같은 값끼리 만나면 0으로 소거
    }
    return result;        // 짝 없는 하나만 살아남음
}
```

**한 줄 설명**
- `result ^= x`: 순서와 무관(교환법칙)하므로 어떤 순서로 XOR해도 짝은 결국 만나 0이 된다.
- 초기값 0: `0 ^ x = x`라 첫 원소가 그대로 들어온다.
- 공간 O(1): 해시맵으로 개수를 세는 방법(O(n) 공간)보다 우월.

**응용**
- 두 수가 홀수 번 나올 때: 전체 XOR로 `a^b`를 얻은 뒤, 켜진 비트 하나를 기준으로 배열을 두 그룹으로 나눠 각각 XOR.
- 1~n 중 빠진 수 찾기: `(1^2^...^n) ^ (배열 전체 XOR)`.

**복잡도**: O(n) 시간, O(1) 공간
**신호**: "쌍으로 소거되는 구조", "중복 속 유일값"

---

### 6. 크기 k 힙 (Top-K)

**대표 문제**: 배열에서 **k번째로 큰 값**을 구하라 (또는 상위 k개).

**아이디어**: 전체 정렬은 O(n log n). 크기 k짜리 **최소 힙**만 유지하면, 힙에는 항상 "지금까지 본 상위 k개"만 남는다. 힙의 top(최소)이 k번째 큰 값. O(n log k)로 더 빠르고, 스트리밍 데이터에도 대응된다.

```java
public static int kthLargest(int[] arr, int k) {
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();   // 최소 힙
    for (int x : arr) {
        minHeap.offer(x);
        if (minHeap.size() > k) {
            minHeap.poll();          // 가장 작은 걸 버려 상위 k개만 유지
        }
    }
    return minHeap.peek();           // 남은 것 중 최소 = k번째 큰 값
}
```

**한 줄 설명**
- **최소** 힙인데 **큰** 값을 찾는 게 헷갈릴 수 있다: 힙에 상위 k개만 남기면, 그 k개 중 가장 작은 게 곧 "k번째로 큰 값"이다.
- `size() > k`일 때 `poll()`: 작은 값부터 밀려나므로 큰 값들만 살아남는다.
- 스트리밍 이점: 전체를 메모리에 안 들고도, 원소가 하나씩 흘러들어올 때 O(log k)로 처리 가능.

**객체 정렬 버전** (클래스 + Comparator):

```java
// HackerRank는 Java 15까지 → record는 preview라 일반 클래스 사용
static class Score {
    final String name;
    final int value;
    Score(String name, int value) { this.name = name; this.value = value; }
    int value() { return value; }
}

// 점수 상위 k명
public static List<Score> topK(List<Score> scores, int k) {
    PriorityQueue<Score> heap =
        new PriorityQueue<>(Comparator.comparingInt(Score::value));  // value 최소 힙
    for (Score s : scores) {
        heap.offer(s);
        if (heap.size() > k) heap.poll();
    }
    return new ArrayList<>(heap);   // 순서 필요하면 별도 정렬
}
```

**복잡도**: O(n log k) 시간, O(k) 공간
**신호**: "상위/하위 k개", "k번째로 큰/작은", "스트리밍에서 top-k"
**주의**: 전부 넣고 전부 빼는 상황(Minimum Loss 등)이면 힙이 오히려 과하다. 그땐 정렬.

---

### 7. 정렬 후 인접 비교

**대표 문제** (HackerRank *Minimum Loss* 계열): 값들이 주어질 때, **차이가 최소인 두 값**을 찾아라. 단, 원래 순서 조건이 있을 수 있다(예: 더 비싼 값이 더 앞 인덱스여야 함).

**아이디어**: "차이가 최소인 쌍"은 **정렬하면 반드시 인접**해 있다. 중간에 다른 값이 끼면 그 값과의 차이가 더 작기 때문. 그래서 O(n²) 쌍 비교가 O(n log n) 정렬 + O(n) 인접 스캔으로 줄어든다. 원래 위치가 필요하면 `(값, 인덱스)`를 클래스로 묶어 정렬.

```java
// HackerRank는 Java 15까지 → record는 preview라 일반 클래스 사용
static class Point {
    final long value;
    final int index;
    Point(long value, int index) { this.value = value; this.index = index; }
    long value() { return value; }
    int index() { return index; }
}

public static long minimumLoss(List<Long> price) {
    int n = price.size();

    // 값 + 원래 인덱스 묶기
    List<Point> points = new ArrayList<>();
    for (int i = 0; i < n; i++) {
        points.add(new Point(price.get(i), i));
    }

    // 값 오름차순 정렬
    points.sort(Comparator.comparingLong(Point::value));

    long min = Long.MAX_VALUE;
    for (int i = 0; i < n - 1; i++) {
        Point lower  = points.get(i);       // 더 싼 값
        Point higher = points.get(i + 1);   // 인접한 더 비싼 값

        // 조건: 비싼 값이 더 이른 해(작은 인덱스)여야 유효한 손실
        if (higher.index() < lower.index()) {
            min = Math.min(min, higher.value() - lower.value());
        }
    }
    return min;
}
```

**한 줄 설명**
- `static class Point`: 정렬로 순서가 뒤섞여도 원래 인덱스를 잃지 않으려는 묶음. (Java 16+라면 `record Point(long value, int index) {}` 한 줄로 대체 가능 — accessor·equals·hashCode 자동 생성)
- 인접 쌍만 보는 이유: 정렬된 상태에서 `points[i]`와 `points[i+1]`의 차이가 가능한 최소 후보들. 떨어진 쌍은 항상 더 크다.
- `higher.index() < lower.index()` 검사: 값 정렬만으로는 "순서 제약"을 못 지키므로 인덱스로 유효성 확인.
- `long` 사용: 값이 크면 뺄셈 결과가 `int`를 넘는다.

**복잡도**: O(n log n) (정렬이 지배적)
**신호**: "가장 가까운 두 값", "최소 차이", "정렬하면 이웃끼리 비교로 충분"

---

### 8. 답을 이진 탐색 (Parametric Search)

**대표 문제**: n개의 물건을 무게 순서 그대로 k개의 트럭에 나눠 싣는다. 각 트럭이 나르는 무게의 **최댓값을 최소화**하라. (또는 "최소 적재량으로 며칠 안에 배송" 류)

**아이디어**: 답 자체를 직접 구하기 어렵다. 대신 **"최대 적재를 x로 제한하면 k대 안에 가능한가?"**라는 판정(feasible)은 그리디로 O(n)에 쉽게 확인된다. 그러면 x를 이진 탐색으로 좁혀 최적 답을 찾는다. 판정이 단조(x가 크면 무조건 가능, 작으면 불가능)라서 성립.

```java
public static int minMaxLoad(int[] weights, int k) {
    long lo = 0, hi = 0;
    for (int w : weights) {
        lo = Math.max(lo, w);   // 한 물건은 반드시 실어야 하니 최소 하한은 최대 원소
        hi += w;                // 한 트럭에 다 실으면 전체 합이 상한
    }

    while (lo < hi) {
        long mid = (lo + hi) / 2;
        if (feasible(weights, k, mid)) {
            hi = mid;           // 가능 → 더 줄여보기
        } else {
            lo = mid + 1;       // 불가능 → 키워야 함
        }
    }
    return (int) lo;            // lo == hi, 가능한 최소값
}

// 최대 적재를 limit으로 제한할 때 k대 이하로 나눠지는가?
private static boolean feasible(int[] weights, int k, long limit) {
    int trucks = 1;
    long current = 0;
    for (int w : weights) {
        if (current + w > limit) {
            trucks++;            // 넘치면 새 트럭
            current = w;
            if (trucks > k) return false;
        } else {
            current += w;
        }
    }
    return true;
}
```

**한 줄 설명**
- 탐색 대상이 배열 인덱스가 아니라 **답의 값(적재량)**: 값의 범위 `[최대원소, 전체합]`을 이진 탐색.
- `feasible`이 단조인 게 핵심: limit이 크면 클수록 트럭이 덜 필요 → 어느 지점부터 "가능"이 계속 참. 그 경계가 답.
- `feasible`이 참이면 `hi = mid`(자신 포함해 줄이기), 거짓이면 `lo = mid+1`: 최소값을 찾는 표준형.

**복잡도**: O(n log(합)) — 판정 O(n) × 이진 탐색 log(값 범위)
**신호**: "최댓값의 최소화" / "최솟값의 최대화", "~하도록 나누는 최적값"

---

### 9. 단조 스택 (Monotonic Stack)

**대표 문제**: 각 원소마다 "오른쪽에서 처음으로 자기보다 큰 값"의 인덱스를 구하라 (Next Greater Element).

**아이디어**: 모든 쌍을 보면 O(n²). 값이 감소하도록 유지되는 스택에 인덱스를 쌓으면, 새 원소가 스택 top보다 클 때 "그 top의 다음 큰 값이 지금 나타났다"고 확정할 수 있다. 각 원소가 스택에 한 번 들어가고 한 번 나가 O(n).

```java
public static int[] nextGreater(int[] arr) {
    int n = arr.length;
    int[] result = new int[n];
    Arrays.fill(result, -1);                 // 없으면 -1

    Deque<Integer> stack = new ArrayDeque<>();  // 인덱스 저장, 대응 값은 감소 순
    for (int i = 0; i < n; i++) {
        // 현재 값이 스택 top의 값보다 크면, top의 답이 i로 확정
        while (!stack.isEmpty() && arr[stack.peek()] < arr[i]) {
            result[stack.pop()] = i;
        }
        stack.push(i);
    }
    return result;
}
```

**한 줄 설명**
- 스택에 **인덱스**를 저장: 답으로 위치가 필요하고, 값은 `arr[stack.peek()]`로 언제든 참조.
- `while` 루프: 새 원소 하나가 여러 개의 대기 중인 원소의 답을 한꺼번에 확정시킬 수 있다.
- 왜 O(n)인가: 각 인덱스는 push 한 번, pop 최대 한 번. 안쪽 while가 있어도 총 pop 횟수는 n을 넘지 않는다.

**대표 응용**
- **히스토그램 최대 직사각형**: 각 막대의 좌우로 "자기보다 낮은 첫 막대"를 단조 스택으로 찾아 너비 계산.
- **빗물 받기(Trapping Rain Water)**: 좌우 경계를 스택으로 추적.

**복잡도**: O(n) 시간, O(n) 공간
**신호**: "다음으로 큰/작은 원소", "구간 최대/최소로 결정되는 넓이"

---

### 10. Union-Find (Disjoint Set)

**대표 문제**: 노드들이 간선으로 연결된다. "두 노드가 같은 그룹인가?" 또는 "연결 요소가 몇 개인가?"를 반복 질의하라.

**아이디어**: 매번 BFS/DFS를 돌리면 비싸다. 각 원소가 "대표(root)"를 가리키게 하고, 같은 root면 같은 그룹. 경로 압축 + union by rank로 연산당 사실상 상수 시간(역 아커만).

```java
public class UnionFind {
    private final int[] parent;
    private final int[] rank;
    private int count;                 // 그룹 개수

    public UnionFind(int n) {
        parent = new int[n];
        rank = new int[n];
        count = n;
        for (int i = 0; i < n; i++) parent[i] = i;   // 처음엔 각자가 대표
    }

    public int find(int x) {
        if (parent[x] != x) {
            parent[x] = find(parent[x]);   // 경로 압축: 조회하며 root에 직접 연결
        }
        return parent[x];
    }

    public boolean union(int a, int b) {
        int ra = find(a), rb = find(b);
        if (ra == rb) return false;        // 이미 같은 그룹
        // union by rank: 낮은 트리를 높은 트리 밑에 붙여 높이 억제
        if (rank[ra] < rank[rb]) { int t = ra; ra = rb; rb = t; }
        parent[rb] = ra;
        if (rank[ra] == rank[rb]) rank[ra]++;
        count--;                           // 그룹이 하나 합쳐짐
        return true;
    }

    public boolean connected(int a, int b) { return find(a) == find(b); }
    public int count() { return count; }
}
```

**한 줄 설명**
- `parent[i] = i` 초기화: 처음엔 모두가 자기 자신이 대표인 독립 그룹.
- 경로 압축: `find` 도중 만난 노드들을 root에 바로 연결해 다음 조회를 빠르게.
- union by rank: 트리가 한쪽으로 길어지는 것을 막아 높이를 낮게 유지.
- `count`: union이 성공할 때마다 1 감소 → 연결 요소 수를 O(1)에 조회.

**대표 응용**: 크루스칼 MST(사이클 판정), 친구 관계 그룹핑, 격자 섬 개수.
**복잡도**: 연산당 거의 O(1) (정확히는 O(α(n)), α는 역 아커만 함수)
**신호**: "연결되어 있는가", "그룹/집합 개수", "간선을 추가하며 병합"

---

### 11. 좌표 압축 (Coordinate Compression)

**대표 문제**: 값의 범위는 최대 10⁹인데 실제 원소는 10⁵개뿐. 빈도 배열이나 세그먼트 트리처럼 "값을 인덱스로 쓰는" 기법을 적용하고 싶다.

**아이디어**: 큰 값 자체를 인덱스로 쓰면 배열이 안 잡힌다. 실제 등장하는 값들만 모아 정렬한 뒤, **"이 값이 몇 번째로 작은가"라는 순위(0, 1, 2, …)로 치환**한다. 상대적 대소 관계는 보존되므로 순위 기반 알고리즘을 그대로 쓸 수 있다.

```java
public static int[] compress(int[] arr) {
    // 1. 중복 제거 + 정렬된 고유값 목록
    int[] sorted = Arrays.stream(arr).distinct().sorted().toArray();

    // 2. 값 → 순위 매핑
    Map<Integer, Integer> rank = new HashMap<>();
    for (int i = 0; i < sorted.length; i++) {
        rank.put(sorted[i], i);          // i번째로 작은 값의 순위 = i
    }

    // 3. 원본을 순위로 치환
    int[] compressed = new int[arr.length];
    for (int i = 0; i < arr.length; i++) {
        compressed[i] = rank.get(arr[i]);
    }
    return compressed;
}
```

**한 줄 설명**
- `distinct().sorted()`: 등장하는 값들을 유일하게 만들고 크기순 배열. 이 배열의 인덱스가 곧 순위.
- `rank` 맵: 원래 값을 O(1)에 순위로 바꾸기 위한 사전. (정렬 배열에 `Arrays.binarySearch`를 써도 됨 — O(log n))
- 치환 후: 값 범위가 `0 ~ (고유값 수-1)`로 압축돼, 이제 크기 10⁵짜리 배열/펜윅/세그먼트 트리를 쓸 수 있다.

**주의**: 대소 비교·순위·"몇 개 이하" 같은 질의는 보존되지만, **실제 값의 크기 차(예: 두 값의 산술적 거리)** 가 필요한 문제엔 쓸 수 없다(순위는 간격 정보를 버린다).

**복잡도**: O(n log n) (정렬)
**신호**: "값 범위는 큰데 원소 수는 적다", "빈도 배열/세그먼트 트리를 쓰고 싶은데 값이 너무 크다"
**철학**: 희소성(sparsity) 활용 — Queens Attack의 "판은 큰데 장애물은 적다 → Set"과 같은 계열.

---

### 12. 비트마스크 (Bitmask)

**대표 문제**: 원소가 n개(n ≤ 20)일 때 모든 부분집합을 순회하거나, 방문 상태를 압축해 DP하라. (외판원 순회(TSP), 집합 커버 등)

**아이디어**: 원소가 20개 이하면 부분집합을 **int 하나의 비트**로 표현할 수 있다. `i`번째 비트가 1이면 i번째 원소 포함. 부분집합 전체 순회가 `0 ~ 2ⁿ-1` 정수 루프로 끝난다.

```java
public static void iterateSubsets(int[] arr) {
    int n = arr.length;
    for (int mask = 0; mask < (1 << n); mask++) {   // 0 ~ 2^n - 1
        List<Integer> subset = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if ((mask >> i & 1) == 1) {              // i번째 비트가 켜졌나
                subset.add(arr[i]);
            }
        }
        // subset 처리...
    }
}
```

**비트 연산 관용구**

```java
mask | (1 << i)     // i번째 원소 추가
mask & ~(1 << i)    // i번째 원소 제거
mask & (1 << i)     // i번째 포함 여부 (0이면 미포함)
mask >> i & 1       // 위와 같은 의미, 결과가 0/1
Integer.bitCount(mask)   // 포함된 원소 개수
(1 << n) - 1        // 전체 집합 (n비트 모두 1)
```

**비트마스크 DP 예시** (TSP 뼈대):

```java
// dp[mask][i] = mask 도시들을 방문했고 현재 i에 있을 때의 최소 비용
int[][] dp = new int[1 << n][n];
for (int[] row : dp) Arrays.fill(row, Integer.MAX_VALUE / 2);
dp[1][0] = 0;   // 0번 도시에서 출발(0번만 방문한 상태)

for (int mask = 1; mask < (1 << n); mask++) {
    for (int u = 0; u < n; u++) {
        if ((mask >> u & 1) == 0) continue;      // u가 방문 안 됐으면 skip
        for (int v = 0; v < n; v++) {
            if ((mask >> v & 1) == 1) continue;  // v가 이미 방문됐으면 skip
            int next = mask | (1 << v);
            dp[next][v] = Math.min(dp[next][v], dp[mask][u] + cost[u][v]);
        }
    }
}
```

**한 줄 설명**
- `1 << n`은 `2ⁿ`: 부분집합의 총 개수. n=20이면 약 100만으로 순회 가능.
- `mask >> i & 1`: `mask`를 오른쪽으로 i칸 밀어 i번째 비트를 맨 끝으로 가져온 뒤 `& 1`로 그 비트만 추출.
- DP에서 `dp[mask][i]`: "방문 집합"을 mask로, "현재 위치"를 i로 표현해 상태를 압축.

**복잡도**: 부분집합 순회 O(2ⁿ · n), TSP DP O(2ⁿ · n²)
**신호**: "n ≤ 20 (많아야 22)", "부분집합 완전탐색", "방문 상태를 들고 다니는 DP"

---

### 보너스: 빈도 배열로 k번째 값

**대표 문제** (HackerRank *Fraudulent Activity Notifications*): 매일 "직전 d일 지출의 **중앙값**의 2배 이상"이면 알림. 지출액 범위가 0~200으로 작다.

**아이디어**: 매일 d개를 정렬하면 O(n·d log d)로 터진다. **값 범위가 작다(0~200)**는 걸 이용해, 빈도 배열 + 누적합으로 k번째 작은 값(중앙값)을 상수 시간에 뽑고, 슬라이딩 윈도우로 O(1) 갱신한다.

```java
public static int activityNotifications(List<Integer> expenditure, int d) {
    final int MAX = 201;                     // 지출액 0~200
    int[] count = new int[MAX];
    int n = expenditure.size();
    int notifications = 0;

    // 첫 d일로 윈도우 초기화
    for (int i = 0; i < d; i++) {
        count[expenditure.get(i)]++;
    }

    for (int i = d; i < n; i++) {
        int medianTimes2 = medianTimes2(count, d);   // 중앙값의 2배 (소수점 회피)
        if (expenditure.get(i) >= medianTimes2) {
            notifications++;
        }
        // 슬라이딩: 가장 오래된 날 제거, 오늘 추가
        count[expenditure.get(i - d)]--;
        count[expenditure.get(i)]++;
    }
    return notifications;
}

// 중앙값의 2배 (짝수 d의 평균에서 소수점이 안 생기게 2배로 다룸)
private static int medianTimes2(int[] count, int d) {
    if (d % 2 == 1) {
        return kthSmallest(count, (d + 1) / 2) * 2;
    } else {
        int a = kthSmallest(count, d / 2);
        int b = kthSmallest(count, d / 2 + 1);
        return a + b;                        // (a+b)/2 의 2배 = a+b
    }
}

// 빈도 배열에서 k번째(1-based) 작은 값
private static int kthSmallest(int[] count, int k) {
    int cumulative = 0;
    for (int value = 0; value < count.length; value++) {
        cumulative += count[value];          // 여기까지 누적 개수
        if (cumulative >= k) return value;   // 처음 k에 도달하는 값이 답
    }
    return -1;
}
```

**한 줄 설명**
- `count[값]`: 현재 윈도우 안의 각 지출액 개수. 빈도 배열을 0부터 읽는 것 = 오름차순 순회.
- `kthSmallest`의 누적합: 정렬 배열에서 "여기까지 몇 칸 왔나"와 같은 의미. 처음 `k` 이상이 되는 값이 k번째 작은 값.
- `>=`로 검사: 같은 값이 여러 개일 때 누적이 k를 건너뛰어도 정확히 잡힌다.
- `medianTimes2`(중앙값 2배): 짝수 d의 `(a+b)/2` 나눗셈을 피해 정수만으로 비교(`오늘지출 >= a+b`)해 부동소수점 오차 제거.
- 슬라이딩 갱신: 윈도우 이동 시 배열 재생성 없이 빠지는 값 -1, 새 값 +1로 O(1).

**복잡도**: O(n × 201) ≈ O(n) (값 범위가 상수)
**신호**: "값 범위가 좁다" + "매번 중앙값/순위/백분위가 필요"
**실무 연결**: 응답시간 p50/p99 스트리밍 집계(HDR Histogram의 원리와 동일). 텔레메트리·모니터링 지표에 그대로 응용.

---

### 부록: 제약 조건 → 기법 역산표

| 제약 신호 | 떠올릴 기법 |
|---|---|
| 구간 합을 반복 조회 | 1. 누적합 |
| 구간 업데이트 반복 (조회는 마지막) | 2. 차이 배열 |
| 정렬된 배열에서 쌍/구간 | 3. 투 포인터 |
| 합/차가 특정 값인 짝 (순서 유지) | 4. 해시맵 (Two Sum) |
| 쌍으로 소거되는 구조 | 5. XOR |
| 상위/하위 k개, 스트리밍 top-k | 6. 크기 k 힙 |
| 최소 차이, 가장 가까운 쌍 | 7. 정렬 후 인접 비교 |
| "최댓값의 최소화 / 최솟값의 최대화" | 8. 답의 이진 탐색 |
| 다음으로 큰/작은 원소, 히스토그램 넓이 | 9. 단조 스택 |
| 연결성/그룹 개수 반복 질의 | 10. Union-Find |
| 값 범위 크고 원소 적음 | 11. 좌표 압축 |
| n ≤ 20, 부분집합 완전탐색 | 12. 비트마스크 |
| 값 범위 좁고 중앙값/순위 필요 | 보너스. 빈도 배열 |

> **메타 원칙**: 문제를 읽을 때 제약(`n`, 값 범위, 쿼리 수)부터 보고 "이 제약이면 O(얼마)까지 허용되지?"를 역산하면, 기법이 "발견"이 아니라 "체크리스트 조회"가 된다.

\newpage

## 2부. 자바 문법 치트시트

> 대상: Java 백엔드 엔지니어
> **호환성: HackerRank 환경(Java 15까지) 기준** — 예제는 별도 플래그 없이 컴파일됨. (`record`, `Stream.toList()`는 Java 16+라 제외/대체)
> 용도: HackerRank·기업 코테 대비. "몰라서 직접 구현하다 시간 날리는" 것 방지 + "알면 기가 막힌" 아이디어 정리

---

- [Part 1. 자주 쓰는 Java 내장 메소드 & 문법](#part-1-자주-쓰는-java-내장-메소드--문법)
  - [문자열 (String)](#문자열-string)
  - [배열 (Arrays)](#배열-arrays)
  - [List / ArrayList](#list--arraylist)
  - [Map / Set](#map--set)
  - [Stack / Queue / Deque / PriorityQueue](#자료구조-stack-queue-deque-priorityqueue)
  - [Comparator](#comparator--정렬-커스터마이징)
  - [Math / 숫자](#math--숫자)
  - [Stream](#stream--입출력-변환에-특히-유용)
  - [최신 문법 (Java 17~21)](#최신-문법-java-1721)
  - [빠른 입출력](#빠른-입출력)
- [Part 2. 코테용 알고리즘 아이디어 12선](#part-2-코테용-알고리즘-아이디어-12선)
- [Part 3. 관통하는 메타 원칙](#part-3-관통하는-메타-원칙)

---

### Part 1. 자주 쓰는 Java 내장 메소드 & 문법

### 문자열 (String)

```java
String s = "hello world";

s.length();                    // 길이
s.charAt(i);                   // i번째 문자
s.substring(2, 5);             // 인덱스 2~4 (끝 exclusive!)
s.indexOf("world");            // 첫 등장 위치, 없으면 -1
s.contains("lo");              // 포함 여부
s.startsWith("he") / s.endsWith("ld");
s.split(" ");                  // String[] 반환. 정규식 주의: "."은 "\\."
s.replace("l", "L");           // 전부 치환 (정규식 아님)
s.trim() / s.strip();          // 공백 제거 (strip이 유니코드까지, Java 11+)
s.toCharArray();               // char[] 변환 — 문자 단위 처리의 시작점
s.repeat(3);                   // "ababab" (Java 11+)
s.chars();                     // IntStream으로 문자 순회
String.join(",", list);        // 리스트를 구분자로 연결
String.valueOf(123);           // 숫자 → 문자열
```

`regionMatches(toffset, other, ooffset, len)` — substring 생성 없이 부분 비교. Grid Search 같은 2D 매칭에서 유용.

**문자 판별/변환** — 파싱 문제 단골:

```java
Character.isDigit(c);
Character.isAlphabetic(c);
Character.isUpperCase(c) / isLowerCase(c);
Character.toUpperCase(c) / toLowerCase(c);
c - '0';                       // 문자 숫자 → int (예: '7' - '0' = 7)
c - 'a';                       // 알파벳 → 0~25 인덱스 (빈도 배열용)
```

**StringBuilder** — 루프 안에서 `+`로 문자열 붙이면 O(n²)라 반드시 이걸로:

```java
StringBuilder sb = new StringBuilder();
sb.append("abc").append(1).append('x');
sb.insert(0, "prefix");
sb.deleteCharAt(sb.length() - 1);
sb.setCharAt(2, 'Z');
sb.reverse();                  // 문자열 뒤집기는 이게 제일 빠름
sb.toString();
```

### 배열 (Arrays)

```java
int[] arr = {5, 2, 8};

Arrays.sort(arr);                        // 오름차순 정렬
Arrays.sort(arr, 2, 5);                  // 부분 정렬
Arrays.fill(arr, -1);                    // 전부 -1로 초기화 (DP 메모 초기화)
Arrays.copyOf(arr, newLen);              // 크기 늘려 복사
Arrays.copyOfRange(arr, from, to);       // 부분 복사
Arrays.binarySearch(arr, key);           // 이진 탐색 (정렬 전제)
Arrays.equals(a, b);                     // 내용 비교 (== 쓰면 참조 비교!)
Arrays.toString(arr);                    // 디버깅 출력용
Arrays.deepToString(grid);               // 2D 배열 출력용
Arrays.stream(arr).max().getAsInt();     // 최대값
Arrays.stream(arr).sum();                // 합
```

**주의**: 원시 타입 `int[]`는 내림차순 정렬이 바로 안 됨. `Integer[]`로 박싱하거나 정렬 후 뒤집기:

```java
Integer[] boxed = {5, 2, 8};
Arrays.sort(boxed, Comparator.reverseOrder());
```

### List / ArrayList

```java
List<Integer> list = new ArrayList<>();

list.add(x);                       // 끝에 추가
list.add(i, x);                    // i 위치에 삽입 — O(n)이니 남용 금지
list.get(i) / list.set(i, x);
list.remove(i);                    // 인덱스로 삭제
list.remove(Integer.valueOf(x));   // 값으로 삭제 — int 넘기면 인덱스로 오해!
list.contains(x);                  // O(n)
list.indexOf(x);
list.size() / list.isEmpty();
Collections.sort(list);
list.sort(Comparator.reverseOrder());
Collections.reverse(list);
Collections.max(list) / Collections.min(list);
List.of(1, 2, 3);                  // 불변 리스트 (수정하면 예외!)
new ArrayList<>(List.of(1,2,3));   // 수정 가능한 초기화
```

> **함정**: `List<Integer>` 원소 비교는 **반드시 `.equals()`**. `==`/`!=`는 참조 비교라 Integer 캐싱 범위(-128~127)를 벗어나는 값(128 이상)에서 틀린 결과가 나옴.

### Map / Set

```java
Map<String, Integer> map = new HashMap<>();

map.put(k, v);
map.get(k);                          // 없으면 null
map.getOrDefault(k, 0);              // 없으면 기본값 — 빈도 세기의 핵심
map.containsKey(k) / containsValue(v);
map.remove(k);
map.keySet() / map.values() / map.entrySet();

// 빈도 카운팅 필수 관용구
map.merge(key, 1, Integer::sum);                          // 카운트 +1
map.computeIfAbsent(key, k -> new ArrayList<>()).add(v);  // 그룹핑
```

```java
Set<Integer> set = new HashSet<>();
set.add(x);          // 이미 있으면 false 반환 — 중복 검출에 활용
set.contains(x);     // O(1)
set.remove(x);
```

정렬 유지가 필요하면 `TreeMap` / `TreeSet`:

```java
TreeSet<Integer> ts = new TreeSet<>();
ts.first() / ts.last();
ts.floor(x);         // x 이하 중 최대 (없으면 null)
ts.ceiling(x);       // x 이상 중 최소
ts.higher(x) / ts.lower(x);   // 초과/미만
```

`floor`/`ceiling`은 "x보다 크거나 같은 첫 원소" 류를 이진 탐색 직접 구현 없이 해결.

### 자료구조: Stack, Queue, Deque, PriorityQueue

```java
// 스택/큐 모두 Deque 하나로 (Stack 클래스는 레거시라 비권장)
Deque<Integer> stack = new ArrayDeque<>();
stack.push(x); stack.pop(); stack.peek();

Deque<Integer> queue = new ArrayDeque<>();
queue.offer(x);      // 뒤에 추가
queue.poll();        // 앞에서 제거 (비었으면 null, remove()는 예외)
queue.peek();

// 우선순위 큐 (최소 힙이 기본)
PriorityQueue<Integer> minHeap = new PriorityQueue<>();
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
minHeap.offer(x); minHeap.poll(); minHeap.peek();
```

### Comparator — 정렬 커스터마이징

```java
// 특정 필드 기준
list.sort(Comparator.comparing(Person::getAge));

// 다중 조건: 나이 오름차순, 같으면 이름 내림차순
list.sort(Comparator.comparing(Person::getAge)
                    .thenComparing(Person::getName, Comparator.reverseOrder()));

// int[] 쌍 정렬 (구간 문제에서 단골)
intervals.sort((a, b) -> Integer.compare(a[0], b[0]));
// 또는
intervals.sort(Comparator.comparingInt(a -> a[0]));
```

> `a[0] - b[0]` 뺄셈 비교는 오버플로 위험이 있으니 `Integer.compare`가 안전.

### Math / 숫자

```java
Math.max(a, b) / Math.min(a, b);
Math.abs(x);
Math.pow(a, b);          // double 반환 주의 — 정수 거듭제곱은 직접 루프나 BigInteger
Math.sqrt(x);
Math.floorDiv(a, b);     // 음수에서도 내림 나눗셈
Math.floorMod(a, b);     // 항상 양수 나머지 — 음수 % 처리할 때
Integer.MAX_VALUE / Integer.MIN_VALUE;
Long.MAX_VALUE;
Integer.parseInt(s) / Long.parseLong(s);
Integer.toBinaryString(n);       // 2진수 문자열
Integer.bitCount(n);             // 켜진 비트 수 (비트마스크 DP에서 유용)
```

**BigInteger** (임의 정밀도) — `long`도 넘는 큰 수:

```java
BigInteger a = BigInteger.valueOf(100);
a = a.multiply(BigInteger.valueOf(2));   // 불변(immutable)이라 재할당 필수!
a.add(b); a.mod(m); a.compareTo(b);
```

### Stream — 입출력 변환에 특히 유용

```java
// List<Integer> → int[]
int[] arr = list.stream().mapToInt(Integer::intValue).toArray();

// int[] → List<Integer>
List<Integer> list = Arrays.stream(arr).boxed()
        .collect(Collectors.toList());   // Java 15 호환 (Stream.toList()는 Java 16+)

// 한 줄 입력 "1 2 3" → int[]
int[] nums = Arrays.stream(line.split(" "))
                   .mapToInt(Integer::parseInt).toArray();

// 합, 최대, 개수
int sum = list.stream().mapToInt(Integer::intValue).sum();
long cnt = list.stream().filter(x -> x > 0).count();

// 그룹핑
Map<Integer, List<String>> byLen = words.stream()
        .collect(Collectors.groupingBy(String::length));
```

> 코테에서 스트림은 로직보다 **변환**(타입 캐스팅, 입력 파싱)에 쓰는 게 실속 있음. 핵심 알고리즘은 명령형 루프가 디버깅·성능 면에서 유리.

### 최신 문법 — HackerRank(Java 15)에서 쓸 수 있는 것

> HackerRank는 **Java 15까지** 지원. 아래는 Java 14/15에서 표준이라 플래그 없이 바로 쓸 수 있음.

```java
// switch 표현식 (화살표, 값 반환) — Java 14 표준
String result = switch (m) {
    case 0 -> "o' clock";
    case 15 -> "quarter past";
    case 30 -> "half past";
    default -> m + " minutes";
};

// var — 타입이 뻔할 때 (Java 10 표준)
var map = new HashMap<String, List<Integer>>();

// 텍스트 블록 — 테스트 입력 붙여넣기 (Java 15 표준)
String input = """
    3 4
    1 2 3
    """;
```

#### ⚠️ HackerRank(Java 15)에서 못 쓰는 것 — 대체법

```java
// [불가] record — Java 14·15에선 preview라 --enable-preview 없이 컴파일 실패. Java 16+부터 표준
//   record Point(int r, int c) {}
// [대체] 일반 클래스로 (좌표/쌍을 묶고 Set·Map 키로 쓰려면 equals·hashCode도 직접)
static class Point {
    final int r, c;
    Point(int r, int c) { this.r = r; this.c = c; }
    @Override public boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        return r == p.r && c == p.c;
    }
    @Override public int hashCode() { return Objects.hash(r, c); }
}

// [불가] Stream.toList() — Java 16+
//   List<Integer> list = stream.toList();
// [대체] Collectors.toList()
List<Integer> list = stream.collect(Collectors.toList());
```

> **좌표를 Set/Map에 넣어야 할 때 (Java 15)**: `record`가 없으니 위처럼 클래스에 `equals`/`hashCode`를 직접 구현하거나, 더 간단하게는 좌표를 **하나의 정수로 인코딩**해서 `Set<Long>`에 넣는다 (예: `(long) r * COLS + c` — Queens Attack에서 쓴 방식). 코테에선 후자가 보일러플레이트 없이 빠르다.

### 빠른 입출력

```java
BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
StringTokenizer st = new StringTokenizer(br.readLine());
int n = Integer.parseInt(st.nextToken());

// 출력 많을 땐 모아서 한 번에
StringBuilder out = new StringBuilder();
out.append(ans).append('\n');
System.out.print(out);
```

> `Scanner`는 입력이 크면 시간 초과의 원인. 대량 입출력은 `BufferedReader` + `StringBuilder`가 안전.

---

### Part 2. 코테용 알고리즘 아이디어 12선

### 1. 누적합 (Prefix Sum) — 구간 합을 O(1)로

"구간 [l, r]의 합을 여러 번 물어보는" 문제. 미리 누적합 배열을 만들어두면 쿼리당 O(1).

```java
prefix[i] = arr[0] + ... + arr[i-1];
// 구간합(l, r) = prefix[r+1] - prefix[l];   // O(1)
```

전처리 O(n) 한 번이면 이후 모든 구간 합이 O(1). 2차원으로 확장하면 부분 직사각형 합도 O(1).
**신호: "구간의 합/개수를 반복해서 묻는다"**

### 2. 차이 배열 (Difference Array) — 구간 업데이트를 O(1)로

누적합의 쌍둥이. "구간 [l, r]에 전부 +x를 여러 번 한 뒤 최종 배열을 구하라". 값이 아니라 **변화가 시작/끝나는 지점**만 기록:

```java
diff[l]     += x;   // l부터 +x 효과 시작
diff[r + 1] -= x;   // r+1부터 효과 종료
// ... 모든 쿼리를 O(1)로 기록한 뒤
// 마지막에 누적합 한 번 돌리면 최종 배열 복원
arr[0] = diff[0];
for (int i = 1; i < n; i++) arr[i] = arr[i-1] + diff[i];
```

**원리**: `diff[i] = arr[i] - arr[i-1]` (인접 원소 간 차이). 누적합과 차이 배열은 서로 역연산(미분↔적분 관계). 차이의 누적합 = 원본.
**복잡도**: O(n·q) → **O(n + q)**
**신호: "구간 전체에 값을 더하는 연산이 여러 번"** (HackerRank "Array Manipulation". 누적은 `long`으로!)

### 3. 투 포인터 — 이중 루프를 한 번의 스캔으로

정렬된 배열에서 "합이 target인 쌍 찾기" 등. 양 끝에 포인터 두 개:

```java
int l = 0, r = n - 1;
while (l < r) {
    int sum = arr[l] + arr[r];
    if (sum == target) return ...;
    else if (sum < target) l++;   // 합이 작으면 왼쪽을 키움
    else r--;                     // 크면 오른쪽을 줄임
}
```

각 포인터가 한 방향으로만 이동 → O(n). 슬라이딩 윈도우도 이 가족.
**신호: "정렬된 상태에서 쌍/구간 조건"**

### 4. 해시맵으로 짝 찾기 — Two Sum 패턴

순서 유지가 필요하면 "내 짝이 이미 나왔었나?"를 해시로 O(1) 조회:

```java
Map<Integer, Integer> seen = new HashMap<>();  // 값 → 인덱스
for (int i = 0; i < n; i++) {
    int need = target - arr[i];
    if (seen.containsKey(need)) return new int[]{seen.get(need), i};
    seen.put(arr[i], i);
}
```

**핵심 사고: 미래를 위해 과거를 기록해두고, 현재 시점에 필요한 과거를 O(1)로 조회**

### 5. XOR 트릭 — 홀수 번 나온 원소 찾기

"모든 수가 두 번씩 나오는데 하나만 한 번" → 한 줄:

```java
int ans = 0;
for (int x : arr) ans ^= x;   // 같은 수끼리 XOR하면 0, 남는 게 답
```

`a^a=0`, `a^0=a`, 교환/결합법칙 → 짝지어진 것이 전부 소거. 공간 O(1).
**신호: "쌍으로 소거되는 구조"**

### 6. k번째 큰 원소 — 크기 k 힙 유지

전체 정렬 O(n log n) 대신 크기 k짜리 최소 힙 → O(n log k):

```java
PriorityQueue<Integer> heap = new PriorityQueue<>();  // 최소 힙
for (int x : arr) {
    heap.offer(x);
    if (heap.size() > k) heap.poll();  // 제일 작은 걸 버림
}
// heap.peek()이 k번째 큰 값
```

**신호: "상위 k개", "스트리밍에서 top-k"**

### 7. 정렬 후 인접 원소만 보기

**"차이가 최소인 쌍"은 정렬하면 반드시 이웃.** O(n²) 쌍 비교 → O(n log n) 정렬 + O(n) 스캔.
값으로 정렬하되 원래 위치가 필요하면 `(값, 인덱스)`를 클래스로 묶어서 정렬. (Java 15라 `record` 대신 일반 클래스 또는 `int[]{값, 인덱스}` 사용)
**신호: "가장 가까운 두 값", "최소 차이"** (HackerRank "Minimum Loss")

### 8. 답을 이진 탐색 (Parametric Search)

배열에서 값을 찾는 게 아니라, **"답이 x 이하로 가능한가?"라는 판정 문제로 바꿔 답 자체를 이진 탐색**:

```java
long lo = 최소가능답, hi = 최대가능답;
while (lo < hi) {
    long mid = (lo + hi) / 2;
    if (feasible(mid)) hi = mid;   // 되면 더 줄여보기
    else lo = mid + 1;             // 안 되면 키우기
}
// lo가 답
```

**신호: "최댓값의 최소화 / 최솟값의 최대화"**

### 9. 단조 스택 (Monotonic Stack) — 다음으로 큰 원소

"각 원소마다 오른쪽에서 처음 자기보다 큰 값은?"을 O(n)에:

```java
Deque<Integer> stack = new ArrayDeque<>();  // 인덱스 저장, 값은 감소 유지
int[] next = new int[n];
Arrays.fill(next, -1);
for (int i = 0; i < n; i++) {
    while (!stack.isEmpty() && arr[stack.peek()] < arr[i]) {
        next[stack.pop()] = i;   // 얘의 "다음 큰 값"이 지금 나타남
    }
    stack.push(i);
}
```

각 원소가 스택에 한 번 들어가고 한 번 나옴 → O(n). 히스토그램 최대 직사각형, 빗물 받기(Trapping Rain Water)의 핵심.

### 10. Union-Find — "같은 그룹인가?"를 거의 O(1)에

연결성/그룹 개수를 반복 질의할 때. BFS/DFS 반복 대신:

```java
int[] parent;
int find(int x) {
    if (parent[x] != x) parent[x] = find(parent[x]);  // 경로 압축
    return parent[x];
}
void union(int a, int b) { parent[find(a)] = find(b); }
```

경로 압축으로 연산당 사실상 상수 시간. 네트워크 연결, 친구 그룹, 사이클 검출(크루스칼 MST).

### 11. 좌표 압축 — 값이 크고 개수가 적을 때

값 범위 10⁹인데 원소가 10⁵개뿐이면, **값을 "정렬했을 때의 순위"로 치환**해서 범위를 10⁵로 압축 → 빈도 배열 등 범위 의존 기법을 다시 사용 가능.
**철학: 희소성(sparsity) 활용** (Queens Attack의 "판은 큰데 장애물은 적다 → Set"과 같은 계열)

### 12. 비트마스크로 부분집합 표현

원소가 20개 이하면 부분집합을 int 하나로 표현. `i`번째 포함 여부 = `(mask >> i) & 1`:

```java
for (int mask = 0; mask < (1 << n); mask++) {
    for (int i = 0; i < n; i++) {
        if ((mask >> i & 1) == 1) { /* i번째 원소 포함 */ }
    }
}
```

**신호: "n ≤ 20"** → 부분집합 완전탐색 / 비트마스크 DP

### 보너스. 빈도 배열로 중앙값/k번째 값 — 카운팅 정렬 응용

**값의 범위가 작을 때(예: 0~200)**, 정렬 없이 빈도 배열 + 누적합으로 k번째 작은 값을 상수 시간에:

```java
// count[값] = 그 값의 등장 횟수. 슬라이딩 윈도우로 O(1) 갱신 가능
private static int kthSmallest(int[] count, int k) {
    int cumulative = 0;
    for (int value = 0; value < count.length; value++) {
        cumulative += count[value];        // 누적 개수
        if (cumulative >= k) return value; // 처음 k에 도달하는 값이 답
    }
    return -1;
}
```

빈도 배열을 0부터 읽는 것 = 오름차순 순회. 누적이 처음 `k` 이상이 되는 값이 k번째 작은 값. 중앙값도 `kthSmallest(count, (d+1)/2)` 등으로 뽑음.
**신호: "값 범위가 좁다" + "매번 중앙값/순위가 필요"** (HackerRank "Fraudulent Activity Notifications")
**실무 연결**: 응답시간 p50/p99 같은 백분위 스트리밍 집계(HDR Histogram의 원리). 텔레메트리·모니터링 지표 집계에 그대로 응용.

---

### Part 3. 관통하는 메타 원칙

> **제약 조건이 풀이를 지시한다.**

| 제약 신호 | 떠올릴 기법 |
|---|---|
| 값 범위가 작다 | 빈도 배열, 카운팅 정렬 |
| 데이터가 희소하다 | Set/Map, 좌표 압축 |
| 정렬돼 있다(또는 해도 된다) | 투 포인터, 이진 탐색, 인접 비교 |
| n ≤ 20 | 비트마스크 완전탐색 |
| "최대의 최소" / "최소의 최대" | 답의 이진 탐색 (Parametric Search) |
| 구간 질의 반복 | 누적합 |
| 구간 업데이트 반복 | 차이 배열 |
| 연결성 질의 반복 | Union-Find |
| "다음으로 큰/작은 원소" | 단조 스택 |
| 최소 차이 / 가장 가까운 쌍 | 정렬 후 인접 비교 |

**문제 읽는 순서**: 제약(`n`, 값 범위, 쿼리 수)부터 본다 → "이 제약이면 O(얼마)까지 허용되지?" 역산 → 후보 기법 조회.
이게 습관이 되면 아이디어가 "기가 막힌 발견"이 아니라 **"체크리스트 조회"**가 된다.

---

### 자주 밟는 지뢰 모음 (실전 디버깅 체크리스트)

- **`Integer` 비교에 `==`/`!=` 사용** → 참조 비교라 128 이상에서 오답. `.equals()` 또는 언박싱.
- **`int` 오버플로** → 큰 수 합/곱은 `long`. 곱하기 *전에* `(long)` 캐스팅 (`(long) n * m`, `(long)(n*m)` 아님).
- **큰 격자를 `new int[n][n]`으로 생성** → 메모리 초과. 희소하면 Set/Map으로.
- **루프 안 문자열 `+` 연결** → O(n²). `StringBuilder` 사용.
- **`List.of(...)` 결과를 수정** → `UnsupportedOperationException`. `new ArrayList<>(...)`로 감싸기.
- **`Scanner`로 대량 입력** → 시간 초과. `BufferedReader`.
- **슬라이딩 윈도우/병합에서 마지막 구간 누락** → 루프 종료 후 마지막 상태를 한 번 더 처리.

\newpage

## 3부. BFS / DFS

### BFS / DFS 자바 구현 정리 (코테 실전용)

대상   : Java 백엔드 엔지니어 (그래프/그리드 탐색 코테)
기준   : 표준 Java. HackerRank(Java 15) 호환 — record 미사용, int[] 좌표로 처리
         (Java 16+ 실무라면 record Point(int r,int c){}로 축약 가능)
범위   : 그리드 탐색, 그래프 탐색, 최단거리, 연결요소, 위상정렬 등 빈출 패턴

핵심 요약:
- BFS = Queue, "최단 거리/레벨"이 필요할 때. 가까운 것부터 퍼짐.
- DFS = Stack 또는 재귀, "모든 경로 탐색/백트래킹/사이클"에 적합.
- 가중치 없는 그래프의 최단 경로는 반드시 BFS (DFS는 최단 보장 X).

1.  BFS 기본 (그래프, 인접 리스트)
2.  DFS 기본 (재귀 / 스택)
3.  그리드 BFS (2D 격자, 4방향/8방향)
4.  그리드 DFS (섬 개수 등)
5.  BFS 최단 거리 (레벨 추적)
6.  다중 시작점 BFS (Multi-source)
7.  연결 요소 개수 세기
8.  사이클 탐지 (방향/무방향)
9.  위상 정렬 (BFS - Kahn / DFS)
10. 자주 쓰는 뼈대 & 실수 포인트

### 1. BFS 기본 (그래프, 인접 리스트)

```java
// 인접 리스트: adj.get(u) = u와 연결된 노드들
public static List<Integer> bfs(int start, List<List<Integer>> adj, int n) {
    List<Integer> order = new ArrayList<>();
    boolean[] visited = new boolean[n];
    Deque<Integer> queue = new ArrayDeque<>();   // Queue는 ArrayDeque 권장

    visited[start] = true;      // 큐에 넣을 때 방문 표시 (중복 삽입 방지)
    queue.offer(start);

    while (!queue.isEmpty()) {
        int cur = queue.poll();
        order.add(cur);

        for (int next : adj.get(cur)) {
            if (!visited[next]) {
                visited[next] = true;   // 여기서 표시! (poll 시점 아님)
                queue.offer(next);
            }
        }
    }
    return order;
}
```

핵심: visited 표시는 "큐에 넣는 순간"에 한다.
      poll 시점에 표시하면 같은 노드가 큐에 여러 번 들어가 비효율/오답.

### 2. DFS 기본 (재귀 / 스택)

```java
// (A) 재귀 방식 - 가장 간결
public static void dfs(int cur, List<List<Integer>> adj, boolean[] visited,
                       List<Integer> order) {
    visited[cur] = true;
    order.add(cur);
    for (int next : adj.get(cur)) {
        if (!visited[next]) {
            dfs(next, adj, visited, order);
        }
    }
}
// 호출: dfs(start, adj, new boolean[n], order);
// 주의: 노드 수가 매우 많으면(수십만) 재귀 깊이로 StackOverflow → 스택 방식 사용

// (B) 명시적 스택 방식 - StackOverflow 회피
public static List<Integer> dfsIterative(int start, List<List<Integer>> adj, int n) {
    List<Integer> order = new ArrayList<>();
    boolean[] visited = new boolean[n];
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(start);

    while (!stack.isEmpty()) {
        int cur = stack.pop();
        if (visited[cur]) continue;   // 스택은 중복 push 가능 → pop 때 체크
        visited[cur] = true;
        order.add(cur);
        for (int next : adj.get(cur)) {
            if (!visited[next]) {
                stack.push(next);
            }
        }
    }
    return order;
}
```

BFS vs DFS 방문 체크 시점 차이:
- BFS  : 큐에 "넣을 때" visited 표시
- DFS(스택): "pop할 때" visited 체크 (넣을 때 표시해도 되지만 pop 체크가 안전)

### 3. 그리드 BFS (2D 격자)

```java
// 방향 벡터: 4방향 (상하좌우)
static int[] dr = {-1, 1, 0, 0};
static int[] dc = {0, 0, -1, 1};
// 8방향이면: dr={-1,-1,-1,0,0,1,1,1}, dc={-1,0,1,-1,1,-1,0,1}

public static void gridBfs(int[][] grid, int startR, int startC) {
    int rows = grid.length, cols = grid[0].length;
    boolean[][] visited = new boolean[rows][cols];
    Deque<int[]> queue = new ArrayDeque<>();

    visited[startR][startC] = true;
    queue.offer(new int[]{startR, startC});

    while (!queue.isEmpty()) {
        int[] cur = queue.poll();
        int r = cur[0], c = cur[1];

        for (int d = 0; d < 4; d++) {
            int nr = r + dr[d];
            int nc = c + dc[d];

            // 경계 체크 → 방문 체크 → 이동 가능 체크 순서
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
            if (visited[nr][nc]) continue;
            if (grid[nr][nc] == 0) continue;   // 예: 0은 벽, 1은 길

            visited[nr][nc] = true;
            queue.offer(new int[]{nr, nc});
        }
    }
}
```

좌표를 큐에 담는 법:
- int[]{r, c} 가 가장 흔하고 빠름 (HackerRank Java 15 호환)
- Java 16+ 실무: record Point(int r, int c){} 쓰면 Set<Point> 방문체크도 깔끔

### 4. 그리드 DFS (섬 개수 세기)

```java
// 1로 연결된 덩어리(섬) 개수. LeetCode 200 / 프로그래머스 유형
public static int numIslands(char[][] grid) {
    int rows = grid.length, cols = grid[0].length;
    int count = 0;
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == '1') {
                count++;
```

                sink(grid, r, c);   // 이 섬 전체를 방문 처리(0으로)
```java
            }
        }
    }
    return count;
}

// 재귀 DFS로 연결된 1을 모두 0으로 (visited 배열 없이 grid 직접 수정)
private static void sink(char[][] grid, int r, int c) {
    if (r < 0 || r >= grid.length || c < 0 || c >= grid[0].length) return;
    if (grid[r][c] != '1') return;

    grid[r][c] = '0';               // 방문 표시 겸 가라앉히기
    sink(grid, r - 1, c);
    sink(grid, r + 1, c);
    sink(grid, r, c - 1);
    sink(grid, r, c + 1);
}
```

팁: grid를 직접 수정하면 visited 배열이 필요 없어 메모리 절약.
     단, 원본을 보존해야 하면 별도 visited[][] 사용.
     격자가 매우 크면 재귀 DFS는 StackOverflow 위험 → BFS로 전환.

### 5. BFS 최단 거리 (레벨 추적)

```java
// 시작점에서 각 칸까지 최단 거리. 가중치 없는 그래프의 최단 경로.
public static int[][] shortestDist(int[][] grid, int sr, int sc) {
    int rows = grid.length, cols = grid[0].length;
    int[][] dist = new int[rows][cols];
    for (int[] row : dist) Arrays.fill(row, -1);   // -1 = 미방문

    Deque<int[]> queue = new ArrayDeque<>();
    dist[sr][sc] = 0;
    queue.offer(new int[]{sr, sc});

    while (!queue.isEmpty()) {
        int[] cur = queue.poll();
        int r = cur[0], c = cur[1];

        for (int d = 0; d < 4; d++) {
            int nr = r + dr[d], nc = c + dc[d];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
            if (dist[nr][nc] != -1) continue;     // 이미 방문(더 짧은 거리로)
            if (grid[nr][nc] == 0) continue;      // 벽

            dist[nr][nc] = dist[r][c] + 1;        // 부모 거리 + 1
            queue.offer(new int[]{nr, nc});
        }
    }
    return dist;
}
```

핵심: dist 배열이 visited 역할을 겸함 (-1이면 미방문).
      BFS는 먼저 도달한 경로가 항상 최단이라, 처음 방문 시 거리가 확정됨.

```java
// 레벨 단위로 처리하고 싶을 때 (한 겹씩)
int level = 0;
while (!queue.isEmpty()) {
    int size = queue.size();          // 현재 레벨의 노드 수 고정
    for (int i = 0; i < size; i++) {
        int[] cur = queue.poll();
        // ... 이웃 탐색 후 queue에 추가
    }
```

    level++;                          // 한 겹 끝날 때마다 레벨 증가
```java
}
```

### 6. 다중 시작점 BFS (Multi-source)

```java
// 여러 출발점에서 동시에 퍼뜨리기. "썩은 토마토", "불 번지기" 유형.
// 모든 시작점을 처음에 큐에 다 넣고 BFS 돌리면 됨.

public static int rotting(int[][] grid) {
    int rows = grid.length, cols = grid[0].length;
    Deque<int[]> queue = new ArrayDeque<>();
    int fresh = 0;

    // 모든 시작점(썩은 토마토=2)을 큐에 초기 투입, 신선(1) 카운트
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == 2) queue.offer(new int[]{r, c});
            else if (grid[r][c] == 1) fresh++;
        }
    }

    int minutes = 0;
    while (!queue.isEmpty() && fresh > 0) {
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            int[] cur = queue.poll();
            for (int d = 0; d < 4; d++) {
                int nr = cur[0] + dr[d], nc = cur[1] + dc[d];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                if (grid[nr][nc] != 1) continue;   // 신선한 것만 감염

                grid[nr][nc] = 2;
                fresh--;
                queue.offer(new int[]{nr, nc});
            }
        }
```

        minutes++;                     // 한 겹(1분) 지남
```java
    }
    return fresh == 0 ? minutes : -1;  // 남은 신선이 있으면 불가능
}
```

핵심: 시작점을 한꺼번에 큐에 넣는 게 다중 시작점 BFS의 전부.
      레벨 단위 처리로 "몇 단계/몇 분 만에 전파되나"를 셈.

### 7. 연결 요소 개수 세기

```java
// 무방향 그래프에서 서로 연결된 덩어리가 몇 개인지
public static int countComponents(int n, List<List<Integer>> adj) {
    boolean[] visited = new boolean[n];
    int count = 0;

    for (int i = 0; i < n; i++) {
        if (!visited[i]) {
            count++;                   // 새 덩어리 발견
            // 이 덩어리 전체를 BFS/DFS로 방문 처리
            Deque<Integer> queue = new ArrayDeque<>();
            visited[i] = true;
            queue.offer(i);
            while (!queue.isEmpty()) {
                int cur = queue.poll();
                for (int next : adj.get(cur)) {
                    if (!visited[next]) {
                        visited[next] = true;
                        queue.offer(next);
                    }
                }
            }
        }
    }
    return count;
}
```

팁: 연결 요소는 Union-Find로도 풀 수 있음.
    간선이 실시간으로 계속 추가되면 Union-Find, 한 번에 다 주어지면 BFS/DFS.

### 8. 사이클 탐지

```java
// (A) 무방향 그래프: 방문한 노드를 부모가 아닌데 또 만나면 사이클
public static boolean hasCycleUndirected(int n, List<List<Integer>> adj) {
    boolean[] visited = new boolean[n];
    for (int i = 0; i < n; i++) {
        if (!visited[i]) {
            if (dfsCycle(i, -1, adj, visited)) return true;
        }
    }
    return false;
}
private static boolean dfsCycle(int cur, int parent,
                                List<List<Integer>> adj, boolean[] visited) {
    visited[cur] = true;
    for (int next : adj.get(cur)) {
        if (!visited[next]) {
            if (dfsCycle(next, cur, adj, visited)) return true;
        } else if (next != parent) {
            return true;               // 부모 아닌데 방문됨 = 사이클
        }
    }
    return false;
}

// (B) 방향 그래프: DFS 재귀 스택(현재 경로)에 있는 노드를 다시 만나면 사이클
//     상태 3가지: 0=미방문, 1=현재경로중, 2=완료
public static boolean hasCycleDirected(int n, List<List<Integer>> adj) {
    int[] state = new int[n];
    for (int i = 0; i < n; i++) {
        if (state[i] == 0 && dfsDir(i, adj, state)) return true;
    }
    return false;
}
private static boolean dfsDir(int cur, List<List<Integer>> adj, int[] state) {
    state[cur] = 1;                    // 현재 경로에 진입
    for (int next : adj.get(cur)) {
        if (state[next] == 1) return true;             // 경로 내 재방문 = 사이클
        if (state[next] == 0 && dfsDir(next, adj, state)) return true;
    }
    state[cur] = 2;                    // 완료 처리
    return false;
}
```

핵심: 무방향은 "부모 제외 재방문", 방향은 "현재 DFS 경로 내 재방문"이 사이클.

### 9. 위상 정렬 (Topological Sort)

```java
// 방향 비순환 그래프(DAG)에서 선후 관계 순서 나열. 선수과목/작업순서 유형.

// (A) BFS 방식 (Kahn's Algorithm) - 진입차수 활용
public static List<Integer> topoSort(int n, List<List<Integer>> adj) {
    int[] indegree = new int[n];
    for (int u = 0; u < n; u++)
        for (int v : adj.get(u)) indegree[v]++;   // 진입차수 계산

    Deque<Integer> queue = new ArrayDeque<>();
    for (int i = 0; i < n; i++)
        if (indegree[i] == 0) queue.offer(i);     // 진입차수 0부터 시작

    List<Integer> order = new ArrayList<>();
    while (!queue.isEmpty()) {
        int cur = queue.poll();
        order.add(cur);
        for (int next : adj.get(cur)) {
            if (--indegree[next] == 0) {           // 선행 다 처리되면 큐에
                queue.offer(next);
            }
        }
    }
    // order 크기가 n보다 작으면 사이클 존재 (위상정렬 불가)
    return order.size() == n ? order : new ArrayList<>();
}
```

팁: Kahn 알고리즘은 위상정렬과 사이클 탐지를 동시에 해줌
    (결과 크기 < n 이면 사이클 있음).

### 10. 자주 쓰는 뼈대 & 실수 포인트

[자료구조 선택]
- Queue  : Deque<T> queue = new ArrayDeque<>();  (LinkedList보다 빠름)
           offer(뒤 추가) / poll(앞 제거) / peek(앞 확인)
- Stack  : Deque<T> stack = new ArrayDeque<>();  (Stack 클래스는 레거시)
           push / pop / peek
- 우선순위: PriorityQueue<T> (다익스트라 등 가중치 있을 때)

[4방향 / 8방향 벡터]
```java
static int[] dr = {-1, 1, 0, 0};              // 상하좌우
static int[] dc = {0, 0, -1, 1};
static int[] dr8 = {-1,-1,-1, 0, 0, 1, 1, 1}; // 8방향
static int[] dc8 = {-1, 0, 1,-1, 1,-1, 0, 1};
```

[실수 포인트]
1. BFS에서 visited를 poll 때 표시 → 같은 노드 중복 삽입. 반드시 offer 때 표시.
2. 경계 체크를 방문/이동 체크보다 먼저. (배열 범위 초과 방지)
3. 가중치 있는 최단경로에 BFS 쓰기 → 오답. 가중치 있으면 다익스트라.
4. 재귀 DFS 깊이 초과(StackOverflow) → 노드 많으면 명시적 스택 BFS/DFS.
5. dist[][]를 -1로 초기화하면 visited 겸용 가능 (Arrays.fill 잊지 말기).
6. 큐에 int[] 좌표 담을 때 new int[]{r, c}. 꺼낼 때 cur[0], cur[1].
7. 무방향 그래프는 간선을 양쪽 다 추가 (adj[u].add(v); adj[v].add(u);).

[인접 리스트 초기화 뼈대]
```java
List<List<Integer>> adj = new ArrayList<>();
for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
for (int[] e : edges) {
    adj.get(e[0]).add(e[1]);
    adj.get(e[1]).add(e[0]);   // 무방향이면 양방향, 방향이면 이 줄 제거
}
```

[복잡도]
- BFS/DFS: O(V + E)  (노드 수 + 간선 수). 그리드는 O(행 x 열).
- 위상정렬: O(V + E)
- 각 노드/간선을 상수 번만 방문하므로 선형.

### 실무 연결 (Spring Boot 백엔드 관점)

- 그래프 탐색은 조직도/카테고리 트리/권한 계층 순회, 추천 시스템의
  연관 관계 탐색, 워크플로우 의존성 해소(위상정렬) 등에 직접 쓰임.
- DB 계층 구조는 재귀 CTE(WITH RECURSIVE)로 처리하는 게 보통 더 효율적.
  애플리케이션에서 N+1로 트리를 타고 내려가지 말 것.
- 대규모 그래프는 메모리 한계로 인접 리스트를 통째로 못 올릴 수 있음.
  이 경우 배치 조회 + 스트리밍 처리 또는 그래프 DB(Neo4j) 고려.
- BFS 레벨 처리 = 메시지 브로드캐스트 홉 수 계산, 소셜 그래프의
  N촌 관계 탐색 등과 구조가 동일.

\newpage

## 4부. 다익스트라

### 다익스트라(Dijkstra) 자바 구현 정리 (코테 실전용)

대상   : Java 백엔드 엔지니어 (가중치 그래프 최단경로 코테)
기준   : 최신 Java / Spring Boot 관점으로 설명.
         단, 코드는 HackerRank(Java 15)에서도 돌아가게 작성
         (record 미사용, int[] 사용. Java 16+면 record로 축약 가능 — 주석 병기)
범위   : 기본 다익스트라, 그리드, 경로 복원, 변형 유형, 다른 알고리즘과 비교

핵심 요약:
- 다익스트라 = "가중치가 양수인 그래프"의 단일 출발점 최단 경로.
- 우선순위 큐(최소 힙)로 "현재까지 가장 가까운 노드"를 매번 꺼내 확정.
- 음수 간선이 있으면 다익스트라 불가 → 벨만-포드 사용.
- 시간복잡도 O((V + E) log V) with 이진 힙.

1.  다익스트라 기본 (인접 리스트 + PriorityQueue)
2.  왜 동작하는가 (핵심 원리)
3.  경로 복원 (실제 경로 출력)
4.  그리드에서의 다익스트라 (가중치 격자)
5.  변형: 특정 목적지, 경유지, K번째 최단
6.  다른 최단경로 알고리즘과 비교
7.  자주 쓰는 뼈대 & 실수 포인트

### 1. 다익스트라 기본 (인접 리스트 + PriorityQueue)

```java
// 간선 표현: adj.get(u) = [{목적지 v, 가중치 w}, ...]
// int[]{v, w} 로 표현 (Java 15 호환)
// 최신 Java(16+) 실무라면: record Edge(int to, int weight) {} 로 더 명확하게

public static int[] dijkstra(int n, List<List<int[]>> adj, int start) {
    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);   // 무한대로 초기화
    dist[start] = 0;

    // 최소 힙: {현재까지 거리, 노드}. 거리 기준 오름차순.
    PriorityQueue<int[]> pq =
        new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
    pq.offer(new int[]{0, start});

    while (!pq.isEmpty()) {
        int[] cur = pq.poll();
        int curDist = cur[0];
        int u = cur[1];

        // 이미 더 짧은 경로로 확정된 노드면 스킵 (핵심 최적화)
        if (curDist > dist[u]) continue;

        for (int[] edge : adj.get(u)) {
            int v = edge[0];
            int w = edge[1];
            int newDist = curDist + w;       // u를 거쳐 v로 가는 거리

            if (newDist < dist[v]) {         // 더 짧으면 갱신 (relaxation)
                dist[v] = newDist;
                pq.offer(new int[]{newDist, v});
            }
        }
    }
    return dist;   // dist[i] = start에서 i까지 최단 거리 (MAX면 도달 불가)
}
```

핵심: "if (curDist > dist[u]) continue;" 가 성능의 핵심.
      큐엔 오래된(더 긴) 거리 항목이 남아있을 수 있어, 꺼낼 때 최신 dist와
      비교해 뒤처진 항목은 버린다. 이게 없으면 불필요한 재탐색이 늘어남.

### 2. 왜 동작하는가 (핵심 원리)

- 매 단계에서 "아직 확정 안 된 노드 중 dist가 가장 작은 것"을 꺼낸다.
- 가중치가 모두 양수이므로, 그 노드보다 더 가까운 경로는 존재할 수 없다.
  → 꺼내는 순간 그 노드의 최단 거리가 확정된다 (그리디).
- 확정된 노드에서 이웃으로 "완화(relaxation)": 더 짧은 길 발견 시 갱신.

왜 음수 간선이면 안 되나:
- 음수 간선이 있으면 "이미 확정한 노드"를 나중에 더 짧게 갱신할 수 있다.
- 다익스트라는 한 번 꺼내 확정하면 되돌아보지 않으므로 틀린 답이 나옴.
- 음수 간선 → 벨만-포드(O(VE)), 음수 사이클 감지도 가능.

relaxation(완화)이란:
```java
  if (dist[u] + w(u,v) < dist[v]) dist[v] = dist[u] + w(u,v);
```

  "u를 경유하는 게 더 짧으면 v의 거리를 줄인다"

### 3. 경로 복원 (실제 경로 출력)

```java
// 거리뿐 아니라 "어떤 경로로 가는지"가 필요할 때 parent 배열 사용

public static List<Integer> dijkstraPath(int n, List<List<int[]>> adj,
                                         int start, int end) {
    int[] dist = new int[n];
```

    int[] parent = new int[n];               // 각 노드의 직전 노드 기록
```java
    Arrays.fill(dist, Integer.MAX_VALUE);
    Arrays.fill(parent, -1);
    dist[start] = 0;

    PriorityQueue<int[]> pq =
        new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
    pq.offer(new int[]{0, start});

    while (!pq.isEmpty()) {
        int[] cur = pq.poll();
        int curDist = cur[0], u = cur[1];
        if (curDist > dist[u]) continue;

        for (int[] edge : adj.get(u)) {
            int v = edge[0], w = edge[1];
            if (curDist + w < dist[v]) {
                dist[v] = curDist + w;
```

                parent[v] = u;               // v로 오는 최단 경로의 직전 = u
```java
                pq.offer(new int[]{dist[v], v});
            }
        }
    }

    // parent를 역추적해 경로 복원
    List<Integer> path = new ArrayList<>();
    if (dist[end] == Integer.MAX_VALUE) return path;   // 도달 불가

    for (int at = end; at != -1; at = parent[at]) {
        path.add(at);
    }
    Collections.reverse(path);               // end→start 였으니 뒤집기
    return path;                             // start ... end 순서
}
```

핵심: parent[v]에 "v로 오는 최단 경로에서의 바로 앞 노드"를 저장.
      end에서 parent를 따라 -1(시작점의 부모)까지 거슬러 올라간 뒤 뒤집는다.

### 4. 그리드에서의 다익스트라 (가중치 격자)

```java
// 각 칸에 이동 비용이 있는 격자에서 좌상단→우하단 최소 비용.
// 비용이 다 1이면 BFS로 충분하지만, 칸마다 비용이 다르면 다익스트라.

static int[] dr = {-1, 1, 0, 0};
static int[] dc = {0, 0, -1, 1};

public static int gridDijkstra(int[][] cost) {
    int rows = cost.length, cols = cost[0].length;
    int[][] dist = new int[rows][cols];
    for (int[] row : dist) Arrays.fill(row, Integer.MAX_VALUE);
    dist[0][0] = cost[0][0];                 // 시작칸 비용 포함(문제 정의에 따라)

    // {누적비용, r, c}
    PriorityQueue<int[]> pq =
        new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
    pq.offer(new int[]{dist[0][0], 0, 0});

    while (!pq.isEmpty()) {
        int[] cur = pq.poll();
        int d = cur[0], r = cur[1], c = cur[2];
        if (d > dist[r][c]) continue;

        for (int dir = 0; dir < 4; dir++) {
            int nr = r + dr[dir], nc = c + dc[dir];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;

            int nd = d + cost[nr][nc];        // 다음 칸 비용 더하기
            if (nd < dist[nr][nc]) {
                dist[nr][nc] = nd;
                pq.offer(new int[]{nd, nr, nc});
            }
        }
    }
    return dist[rows - 1][cols - 1];
}
```

팁: 좌표를 int[]{비용, r, c}로 한 배열에 담으면 관리 편함.
     "0/1 가중치"만 있는 특수 케이스는 0-1 BFS(Deque 양끝 삽입)가 더 빠름.

### 5. 변형 유형

[특정 목적지에서 조기 종료]
  end 노드를 pq에서 poll하는 순간 dist[end]가 확정 → 즉시 return 가능.
  while 안에서: if (u == end) return dist[end];

[경유지 필수 (start → via → end)]
  다익스트라를 여러 번: dist(start→via) + dist(via→end).
  경유지가 여러 개면 순열로 조합하거나, 각 지점에서 다익스트라 실행.

[여러 출발점 중 최단 (Multi-source)]
  모든 출발점을 dist=0으로 초기화하고 전부 pq에 넣고 시작.
  BFS 다중 시작점과 같은 아이디어.

[K번째 최단 경로]
```java
  dist 배열 대신 "각 노드 도착 횟수"를 세고, K번 도착까지 허용.
```

  방문 확정으로 막지 않고 K번까지 큐 통과시킴.

[간선 뒤집기 (모든 노드 → 하나의 목적지)]
  그래프의 간선 방향을 모두 뒤집은 뒤, 목적지에서 다익스트라 1회.

### 6. 다른 최단경로 알고리즘과 비교 (면접 대비)

BFS
  - 조건: 가중치 없음(또는 전부 동일).
  - 복잡도 O(V+E). 큐 사용. 가장 단순.

다익스트라 (Dijkstra)
  - 조건: 가중치 양수, 단일 출발점.
  - 복잡도 O((V+E) log V). 우선순위 큐.
  - 가장 흔히 쓰이는 최단경로.

0-1 BFS
  - 조건: 가중치가 0 또는 1뿐.
  - 복잡도 O(V+E). Deque 사용(0이면 앞, 1이면 뒤 삽입).
  - 다익스트라보다 빠른 특수 케이스.

벨만-포드 (Bellman-Ford)
  - 조건: 음수 간선 허용, 음수 사이클 감지 가능.
  - 복잡도 O(VE). 느리지만 음수 처리 가능.

플로이드-워셜 (Floyd-Warshall)
  - 조건: 모든 쌍 최단경로(All-Pairs).
  - 복잡도 O(V^3). 노드 수 적을 때(수백) 간편.
  - 3중 for문으로 끝나 구현이 매우 짧음.

A* (에이스타)
  - 조건: 목적지가 명확 + 좋은 휴리스틱(예: 격자에서 맨해튼 거리).
  - 다익스트라에 방향성을 준 것. 게임/길찾기에서 빠름.

선택 기준 한 줄 요약:
  가중치 없다 → BFS
  가중치 0/1 → 0-1 BFS
  가중치 양수, 단일 출발 → 다익스트라
  음수 간선 → 벨만-포드
  모든 쌍 + 노드 적음 → 플로이드-워셜
  목적지 명확 + 휴리스틱 → A*

### 7. 자주 쓰는 뼈대 & 실수 포인트

[PriorityQueue 비교자]
```java
  // 거리 오름차순 (최소 힙)
  new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
  // Comparator.comparingInt로도 가능
  new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
  // 주의: a[0] - b[0] 뺄셈 비교는 오버플로 위험 → Integer.compare 사용
```

[인접 리스트 초기화]
```java
  List<List<int[]>> adj = new ArrayList<>();
  for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
  for (int[] e : edges) {                  // e = {u, v, w}
      adj.get(e[0]).add(new int[]{e[1], e[2]});
      adj.get(e[1]).add(new int[]{e[0], e[2]});  // 무방향이면 양방향
  }
```

[실수 포인트]
1. dist를 Integer.MAX_VALUE로 초기화한 뒤, 거리 합산 시 오버플로 주의.
   MAX_VALUE + w 가 음수로 뒤집힘 → 방문 안 한 노드는 갱신 전 스킵되게 로직 확인.
   거리가 크면 long[] dist 사용 고려.
2. "if (curDist > dist[u]) continue;" 빠뜨리면 느려짐(중복 처리).
3. 음수 간선에 다익스트라 사용 → 오답. 반드시 양수 확인.
4. visited 불린 배열 방식도 있지만, "꺼낼 때 dist 비교" 방식이 더 흔하고 안전.
5. 무방향 그래프는 간선을 양쪽 다 추가.
6. 시작칸 비용을 답에 포함할지(그리드) 문제 정의를 꼭 확인.
7. 도달 불가 판정: dist[end] == Integer.MAX_VALUE (또는 long이면 그 값).

[복잡도]
- 이진 힙 다익스트라: O((V + E) log V)
- 각 간선이 최대 한 번 완화되고 힙 연산이 log V.
- 정점이 많고 간선이 조밀하면 피보나치 힙으로 O(E + V log V) 이론상 개선
  (실무·코테에선 이진 힙 PriorityQueue로 충분).

### 실무 연결 (최신 Spring Boot 백엔드 관점)

- 최단경로는 배송/물류 경로 최적화, 지도 길찾기, 네트워크 라우팅,
  추천 시스템의 관계 거리 계산 등에 직접 쓰임.
- 대규모 그래프는 인접 리스트를 메모리에 다 못 올릴 수 있음 →
  그래프 DB(Neo4j)나 전용 엔진, 또는 사전 계산(pre-computation) + 캐시.
- 실시간 경로 API라면 다익스트라를 매 요청 실행하기보다,
  자주 쓰는 출발-도착 쌍을 캐싱(Redis)하거나 A*로 응답시간 단축.
- 가중치가 동적으로 바뀌는(교통량 등) 경우, 주기적 재계산을
  배치/스케줄러로 돌리고 결과를 조회용 저장소에 반영하는 구조가 흔함.
- Java 최신 버전이라면 그래프 노드/간선을 record로 표현하면 불변성과
  가독성이 좋아짐: record Edge(int to, long weight) {} 등.
  가상 스레드(virtual thread)로 다수의 독립적 최단경로 계산을
  동시에 처리하는 것도 최신 환경에서 고려할 만함.

\newpage

## 5부. 백트래킹

### 백트래킹(Backtracking) 자바 구현 정리 (코테 실전용)

대상   : Java 백엔드 엔지니어 (완전탐색/백트래킹 코테)
기준   : 최신 Java / Spring Boot 관점으로 설명.
         단, 코드는 HackerRank(Java 15)에서도 돌아가게 작성.
범위   : 부분집합/조합/순열 골격, N-Queens, 순열 복원, 가지치기 최적화

핵심 요약:
- 백트래킹 = "선택 → 재귀 → 선택 취소(되돌리기)"의 반복.
- DFS로 모든 경우를 탐색하되, 유망하지 않으면 조기에 가지치기(pruning).
- 부분집합/조합/순열 생성이 대표. 세 골격만 익히면 대부분 응용 가능.
- 최대 함정: 결과 리스트에 "현재 경로 참조"를 그대로 넣으면 전부 같은 값이 됨.
  반드시 복사본(new ArrayList<>(current))을 넣어야 함.

1.  백트래킹의 3단계 골격
2.  부분집합 (Subsets)
3.  조합 (Combinations)
4.  순열 (Permutations)
5.  중복 원소 처리 (중복 순열/조합)
6.  N-Queens (2D 백트래킹의 정석)
7.  대표 유형: 합이 target인 조합 (Combination Sum)
8.  가지치기(Pruning) 최적화
9.  자주 쓰는 뼈대 & 실수 포인트

### 1. 백트래킹의 3단계 골격

모든 백트래킹은 이 형태로 환원된다:

```java
void backtrack(상태, 현재경로) {
    if (종료 조건) {                    // 답을 하나 완성
        result.add(복사본);            // ★ 반드시 복사본!
        return;
    }
    for (각 선택지) {
        if (유효하지 않으면) continue;   // 가지치기
        선택();                        // current.add(x), used[x]=true 등
```

        backtrack(다음 상태, 현재경로);  // 재귀
        선택_취소();                    // current.remove(끝), used[x]=false  ← 되돌리기
```java
    }
}
```

세 동작이 핵심: [선택] → [재귀] → [선택 취소].
"선택 취소"를 빠뜨리면 이전 선택이 다음 분기에 오염되어 오답.

### 2. 부분집합 (Subsets)

```java
// [1,2,3]의 모든 부분집합: [], [1], [2], [3], [1,2], [1,3], [2,3], [1,2,3]
// 각 원소를 "넣는다/안 넣는다" 두 갈래. 총 2^n개.

public static List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(nums, 0, new ArrayList<>(), result);
    return result;
}

private static void backtrack(int[] nums, int start,
                              List<Integer> current, List<List<Integer>> result) {
    // 모든 노드가 부분집합 하나 (종료 조건 없이 매번 기록)
    result.add(new ArrayList<>(current));   // ★ 복사본 추가

    for (int i = start; i < nums.length; i++) {
        current.add(nums[i]);               // 선택
```

        backtrack(nums, i + 1, current, result);  // i+1: 뒤쪽만 (중복 방지)
```java
        current.remove(current.size() - 1); // 선택 취소
    }
}
```

핵심: start 인덱스로 "이미 지나온 원소는 다시 안 봄" → 조합적 중복 방지.
      i + 1 을 넘겨 각 원소를 최대 한 번만 사용.

### 3. 조합 (Combinations)

```java
// n개 중 k개 고르기. 예: nCk. LeetCode 77.
// 부분집합과 거의 같되, "크기가 k일 때만" 기록.

public static List<List<Integer>> combine(int n, int k) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(1, n, k, new ArrayList<>(), result);
    return result;
}

private static void backtrack(int start, int n, int k,
                              List<Integer> current, List<List<Integer>> result) {
    if (current.size() == k) {              // 목표 크기 도달
        result.add(new ArrayList<>(current));
        return;
    }
    // 가지치기: 남은 원소로 k를 채울 수 없으면 중단
    // 남은 필요 수 = k - current.size(), 남은 후보 = n - i + 1
    for (int i = start; i <= n - (k - current.size()) + 1; i++) {
        current.add(i);
        backtrack(i + 1, n, k, current, result);
        current.remove(current.size() - 1);
    }
}
```

가지치기 포인트: 상한을 n이 아니라 "n - (남은 필요 수) + 1"로 제한.
                남은 원소가 부족한 가지는 아예 진입 안 함 → 큰 속도 향상.

### 4. 순열 (Permutations)

```java
// [1,2,3]의 모든 순열: 순서가 다르면 다른 것. 총 n!개.
// used[] 배열로 "이미 쓴 원소" 추적 (start 인덱스 방식과 다름).

public static List<List<Integer>> permute(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    boolean[] used = new boolean[nums.length];
    backtrack(nums, used, new ArrayList<>(), result);
    return result;
}

private static void backtrack(int[] nums, boolean[] used,
                              List<Integer> current, List<List<Integer>> result) {
    if (current.size() == nums.length) {    // 전부 사용
        result.add(new ArrayList<>(current));
        return;
    }
    for (int i = 0; i < nums.length; i++) {
        if (used[i]) continue;              // 이미 쓴 원소 스킵
        used[i] = true;                     // 선택
        current.add(nums[i]);
        backtrack(nums, used, current, result);
        current.remove(current.size() - 1); // 선택 취소
        used[i] = false;                    // ★ used도 되돌리기!
    }
}
```

조합 vs 순열 차이:
- 조합: start 인덱스로 앞쪽 원소 재사용 방지 (순서 무관).
- 순열: used[] 배열로 사용 여부 추적, 매번 0부터 순회 (순서 중요).
- used[i]=false 되돌리기를 빠뜨리면 다음 분기에서 원소를 못 씀 → 오답.

### 5. 중복 원소 처리

```java
// 입력에 중복이 있을 때 (예: [1,1,2]) 결과의 중복을 제거하려면
// (1) 정렬 후 (2) "같은 값을 같은 depth에서 두 번 쓰지 않기"

public static List<List<Integer>> permuteUnique(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    Arrays.sort(nums);                      // ★ 먼저 정렬 (중복이 인접하도록)
    boolean[] used = new boolean[nums.length];
    backtrack(nums, used, new ArrayList<>(), result);
    return result;
}

private static void backtrack(int[] nums, boolean[] used,
                              List<Integer> current, List<List<Integer>> result) {
    if (current.size() == nums.length) {
        result.add(new ArrayList<>(current));
        return;
    }
    for (int i = 0; i < nums.length; i++) {
        if (used[i]) continue;
        // 중복 가지치기: 같은 값이고, 앞의 같은 값이 아직 안 쓰였으면 스킵
        if (i > 0 && nums[i] == nums[i - 1] && !used[i - 1]) continue;

        used[i] = true;
        current.add(nums[i]);
        backtrack(nums, used, current, result);
        current.remove(current.size() - 1);
        used[i] = false;
    }
}
```

핵심 조건: nums[i] == nums[i-1] && !used[i-1]
  "같은 값 그룹에서는 앞쪽부터 순서대로만 사용" → 동일 순열 중복 방지.
  조합에서 중복 제거할 땐: if (i > start && nums[i] == nums[i-1]) continue;

### 6. N-Queens (2D 백트래킹의 정석)

```java
// N x N 체스판에 퀸 N개를 서로 공격 못 하게 배치하는 경우의 수.
// 행마다 퀸을 하나씩 놓되, 열/대각선 충돌을 체크하며 가지치기.

public static int totalNQueens(int n) {
    // 열, 두 방향 대각선 사용 여부를 boolean으로 추적
    boolean[] cols = new boolean[n];
    boolean[] diag1 = new boolean[2 * n];   // r + c (↘ 대각선)
    boolean[] diag2 = new boolean[2 * n];   // r - c + n (↗ 대각선)
    return place(0, n, cols, diag1, diag2);
}

private static int place(int row, int n,
                         boolean[] cols, boolean[] diag1, boolean[] diag2) {
    if (row == n) return 1;                 // 모든 행에 배치 성공 = 해 1개

    int count = 0;
    for (int col = 0; col < n; col++) {
        int d1 = row + col;
        int d2 = row - col + n;
        if (cols[col] || diag1[d1] || diag2[d2]) continue;  // 충돌 → 가지치기

        cols[col] = diag1[d1] = diag2[d2] = true;   // 선택 (퀸 배치)
        count += place(row + 1, n, cols, diag1, diag2);
        cols[col] = diag1[d1] = diag2[d2] = false;  // 선택 취소
    }
    return count;
}
```

핵심 트릭: 대각선을 인덱스로 표현.
  ↘ 대각선(왼위→오른아래): r + c 가 같음
  ↗ 대각선(오른위→왼아래): r - c 가 같음 (음수 방지로 +n)
```java
  boolean 배열 조회로 충돌 판정이 O(1) → 매우 빠름.
```

### 7. 대표 유형: 합이 target인 조합 (Combination Sum)

```java
// candidates에서 골라 합이 target이 되는 모든 조합. 원소 재사용 가능 버전.
// LeetCode 39.

public static List<List<Integer>> combinationSum(int[] candidates, int target) {
    List<List<Integer>> result = new ArrayList<>();
    Arrays.sort(candidates);                // 정렬하면 가지치기 쉬움
    backtrack(candidates, target, 0, new ArrayList<>(), result);
    return result;
}

private static void backtrack(int[] candidates, int remain, int start,
                              List<Integer> current, List<List<Integer>> result) {
    if (remain == 0) {                      // 정확히 target 도달
        result.add(new ArrayList<>(current));
        return;
    }
    for (int i = start; i < candidates.length; i++) {
        if (candidates[i] > remain) break;  // 정렬됐으므로 이후는 다 초과 → 중단
        current.add(candidates[i]);
        // i (i+1 아님): 같은 원소 재사용 허용. 재사용 불가면 i+1 전달.
        backtrack(candidates, remain - candidates[i], i, current, result);
        current.remove(current.size() - 1);
    }
}
```

재사용 여부로 재귀 인자가 갈림:
- 원소 재사용 허용 → backtrack(..., i, ...)
- 각 원소 한 번만    → backtrack(..., i + 1, ...)

### 8. 가지치기(Pruning) 최적화

백트래킹의 성능은 "얼마나 빨리 가망 없는 가지를 쳐내느냐"에 달림.

주요 가지치기 기법:
1. 정렬 후 조기 중단
   - 후보를 정렬하고, 현재 값이 이미 목표 초과면 break (이후도 다 초과).

2. 남은 자원 계산
   - 조합에서 "남은 원소로 목표 크기를 못 채우면" 진입 안 함.
   - 상한 인덱스를 n - (필요 수) + 1 로 제한.

3. 상태 배열로 O(1) 충돌 검사
   - N-Queens의 cols/diag 배열처럼, 매번 전체 스캔 대신 배열 조회.

4. 부분합 상한/하한 체크
   - 남은 후보들의 합이 target에 못 미치면 중단.

5. 대칭성 제거
   - N-Queens 첫 행을 절반만 탐색 후 결과 x2 (대칭 활용).

가지치기가 없으면 2^n / n! 폭발로 시간 초과. 있으면 실제 유효 가지만 탐색.

### 9. 자주 쓰는 뼈대 & 실수 포인트

[가장 흔한 치명적 실수: 참조 vs 복사]
```java
  // 틀림 - current의 "참조"를 넣으면, 이후 current가 바뀌면 다 같이 바뀜
  result.add(current);                 // ✗ 모든 결과가 최종 상태로 동일해짐

  // 맞음 - 그 시점의 스냅샷(복사본)을 넣어야 함
  result.add(new ArrayList<>(current)); // ✓
```

  이유: List는 참조 타입. current는 재귀 내내 계속 수정되는 하나의 객체.
        참조만 담으면 result 안 항목들이 전부 같은 객체를 가리킴.

[선택 취소를 잊지 말기]
  선택()과 선택_취소()는 반드시 짝. add 했으면 remove,
  used=true 했으면 used=false. 한쪽만 있으면 상태 오염.

[조합 vs 순열 구분]
  - 순서 무관(조합/부분집합): start 인덱스로 앞쪽 재사용 차단.
  - 순서 중요(순열): used[] 배열, 매번 0부터 순회.

[중복 제거]
  - 입력 정렬 필수.
  - 순열: if (i>0 && nums[i]==nums[i-1] && !used[i-1]) continue;
  - 조합: if (i>start && nums[i]==nums[i-1]) continue;

[재귀 깊이]
  - n이 크면 백트래킹 자체가 지수 시간이라 애초에 부적합.
  - 보통 n <= 15~20 정도에서만 완전탐색 백트래킹이 통함.
  - 그 이상은 DP나 그리디 등 다른 접근 필요.

[복잡도 (대략)]
  - 부분집합: O(2^n * n)   (2^n개 * 복사 n)
  - 순열: O(n! * n)
  - 조합 nCk: O(C(n,k) * k)
  - N-Queens: 대략 O(n!)이지만 가지치기로 실제론 훨씬 적음.

[불변 조건 체크리스트]
  1. 종료 조건이 명확한가?
  2. result에 복사본을 넣는가?
  3. 모든 선택에 대응하는 취소가 있는가?
  4. 가지치기로 불필요한 탐색을 막는가?
  5. start/used 중 문제 유형에 맞는 걸 쓰는가?

### 실무 연결 (최신 Java / Spring Boot 백엔드 관점)

- 백트래킹은 설정 조합 탐색, 스케줄링(제약 만족), 권한 조합 검증,
  파서/규칙 엔진의 경우의 수 탐색 등에 응용됨.
- 실무에서 "모든 경우의 수"를 런타임에 완전탐색하는 건 드묾.
  대신 제약 조건을 모델링해 유효 조합만 생성하는 데 아이디어를 씀.
- 최신 Java라면 결과 스냅샷을 record나 List.copyOf(불변 리스트)로 담아
  의도치 않은 변경을 원천 차단하는 게 안전. (List.copyOf는 Java 10+)
  예: result.add(List.copyOf(current));  // 불변 복사본
- 경우의 수가 많고 각 가지가 독립적이면, 병렬 스트림이나 가상 스레드로
  분기를 나눠 처리하는 것도 최신 환경에서 고려 가능(단, 공유 상태 주의).
- 제약 만족 문제(CSP)가 크면 백트래킹 대신 전용 솔버(OR-Tools 등)나
  SAT 솔버를 붙이는 게 현실적.

\newpage

## 6부. SQL (MySQL)

### 코테·실무 SQL 패턴 정리 (MySQL 전용)

대상   : Java 백엔드 엔지니어 (SQL 코테 + 실무 공통)
기준   : MySQL 8.0+ (윈도우 함수, CTE, 재귀 쿼리 사용 가능)
범위   : HackerRank·프로그래머스 SQL 유형 + 실무에서 그대로 쓰는 쿼리 위주

주의   : HackerRank는 MySQL 선택 가능. 윈도우 함수/CTE는 MySQL 8.0부터.
         플랫폼 버전이 5.7이면 윈도우 함수·CTE가 없으니 서브쿼리로 우회.

1.  기본 필터링 & 정렬 (WHERE, ORDER BY, LIMIT)
2.  집계 & 그룹핑 (GROUP BY, HAVING)
3.  조인 (INNER / LEFT / SELF)
4.  서브쿼리 & 상관 서브쿼리
5.  윈도우 함수 (ROW_NUMBER, RANK, LAG/LEAD)
6.  N번째 값 / 그룹별 Top-N
7.  CASE 조건 분기 & 피벗
8.  NULL 처리 (COALESCE, IFNULL, NULLIF)
9.  문자열 처리
10. 날짜/시간 처리
11. 중복 처리 (DISTINCT, 중복 제거/탐지)
12. 집합 연산 (UNION)
13. CTE & 재귀 쿼리
14. 코테 문제 유형별 스니펫
15. 실무 성능 주의점

### 1. 기본 필터링 & 정렬

```java
-- 조건 필터 + 정렬 + 상위 N개
SELECT name, salary
FROM employees
WHERE salary > 50000 AND department = 'ENG'
ORDER BY salary DESC
```

LIMIT 10;

```java
-- 페이징: LIMIT 개수 OFFSET 건너뛸수
SELECT * FROM employees ORDER BY id LIMIT 10 OFFSET 20;   -- 21~30번째
SELECT * FROM employees ORDER BY id LIMIT 20, 10;         -- 같은 의미 (OFFSET, 개수)

-- 범위 / 집합 / 패턴
WHERE age BETWEEN 20 AND 30           -- 경계 포함
WHERE city IN ('Seoul', 'Busan')
WHERE name LIKE 'K%'                  -- K로 시작
WHERE name LIKE '%son'               -- son으로 끝
WHERE name LIKE '_ohn'               -- _ 는 한 글자 (John, Bohn...)

-- 정렬 커스텀: 특정 값 우선
ORDER BY
  CASE WHEN status = 'URGENT' THEN 0 ELSE 1 END,
```

  created_at DESC;

```java
-- FIELD로 지정 순서 정렬 (MySQL 전용)
ORDER BY FIELD(grade, 'A', 'B', 'C');
```

### 2. 집계 & 그룹핑

```java
-- 부서별 인원수, 평균 급여 (조건: 인원 5명 이상)
SELECT department,
       COUNT(*)     AS headcount,
       AVG(salary)  AS avg_salary,
       MAX(salary)  AS max_salary,
       SUM(salary)  AS total_salary
FROM employees
GROUP BY department
HAVING COUNT(*) >= 5           -- 그룹 필터는 HAVING (WHERE 아님)
ORDER BY avg_salary DESC;

WHERE vs HAVING:
```

- WHERE : 그룹핑 "전에" 행 필터 (집계함수 사용 불가)
- HAVING: 그룹핑 "후에" 그룹 필터 (집계함수 사용 가능)

```java
-- COUNT 종류
COUNT(*)                 -- NULL 포함 전체 행 수
COUNT(column)            -- 해당 컬럼이 NULL 아닌 행 수
COUNT(DISTINCT column)   -- 중복 제거 후 개수

-- GROUP_CONCAT: 그룹 내 값을 한 줄로 (MySQL 전용, 코테에 종종 나옴)
SELECT dept_id, GROUP_CONCAT(name ORDER BY name SEPARATOR ', ') AS members
FROM employees
GROUP BY dept_id;
```

### 3. 조인 (JOIN)

```java
-- INNER JOIN: 양쪽에 다 있는 것만
SELECT e.name, d.dept_name
FROM employees e
INNER JOIN departments d ON e.dept_id = d.id;

-- LEFT JOIN: 왼쪽 전부 + 매칭되는 오른쪽 (없으면 NULL)
SELECT e.name, d.dept_name
FROM employees e
LEFT JOIN departments d ON e.dept_id = d.id;

-- LEFT JOIN + IS NULL = "매칭 안 되는 것만" (안티조인)
SELECT e.name
FROM employees e
LEFT JOIN departments d ON e.dept_id = d.id
WHERE d.id IS NULL;              -- 부서 없는 직원

-- SELF JOIN: 같은 테이블끼리 (계층/비교)
SELECT e.name AS employee, m.name AS manager
FROM employees e
JOIN employees m ON e.manager_id = m.id;

-- 여러 조인 체이닝
SELECT o.id, c.name, p.title
FROM orders o
JOIN customers c ON o.customer_id = c.id
JOIN products  p ON o.product_id  = p.id;
```

코테 빈출: "A에는 있고 B에는 없는" 유형 → LEFT JOIN + IS NULL 또는 NOT EXISTS.
MySQL은 FULL OUTER JOIN 미지원 → LEFT JOIN UNION RIGHT JOIN 으로 우회.

### 4. 서브쿼리 & 상관 서브쿼리

```java
-- 스칼라 서브쿼리: 평균보다 급여 높은 직원
SELECT name, salary
FROM employees
WHERE salary > (SELECT AVG(salary) FROM employees);

-- IN 서브쿼리
SELECT name FROM employees
WHERE dept_id IN (SELECT id FROM departments WHERE location = 'Seoul');

-- 상관 서브쿼리 (바깥 행마다 안쪽 재실행): 부서별 최고 급여자
SELECT e.name, e.salary, e.dept_id
FROM employees e
WHERE e.salary = (SELECT MAX(salary)
                  FROM employees
                  WHERE dept_id = e.dept_id);   -- e 참조 = 상관

-- EXISTS: 존재 여부만 확인
SELECT c.name
FROM customers c
WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);

-- NOT EXISTS: 주문 없는 고객
SELECT c.name
FROM customers c
WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);
```

NOT IN 함정: 서브쿼리 결과에 NULL이 하나라도 있으면 전체 결과가 비어버림
             → NOT EXISTS가 안전.
```java
FROM 절 서브쿼리(파생 테이블)에는 반드시 별칭(AS t) 필요 (MySQL 필수).
```

### 5. 윈도우 함수 (MySQL 8.0+, 가장 중요)

문법: FUNCTION() OVER (PARTITION BY ... ORDER BY ...)
- PARTITION BY: 그룹을 나눔 (GROUP BY와 달리 행이 사라지지 않음)
- ORDER BY: 그룹 내 순서

```java
-- 순위 3형제
SELECT name, dept_id, salary,
  ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS row_num,
  RANK()       OVER (PARTITION BY dept_id ORDER BY salary DESC) AS rnk,
  DENSE_RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS dense_rnk
FROM employees;
```

차이 (급여 100, 100, 90 인 경우):
- ROW_NUMBER : 1, 2, 3   (무조건 유일, 동점도 순서 매김)
- RANK       : 1, 1, 3   (동점 같은 순위, 다음은 건너뜀)
- DENSE_RANK : 1, 1, 2   (동점 같은 순위, 다음은 안 건너뜀)

```java
-- 이전/다음 행 값 (증감 계산)
SELECT order_date, amount,
  LAG(amount)  OVER (ORDER BY order_date) AS prev_amount,
  LEAD(amount) OVER (ORDER BY order_date) AS next_amount,
```

  amount - LAG(amount) OVER (ORDER BY order_date) AS diff
```java
FROM daily_sales;

-- 누적 합계 (running total)
SELECT order_date, amount,
  SUM(amount) OVER (ORDER BY order_date
                    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total
FROM daily_sales;

-- 그룹 내 비율 (전체 대비)
SELECT dept_id, salary,
```

  salary * 100.0 / SUM(salary) OVER (PARTITION BY dept_id) AS pct_of_dept
```java
FROM employees;

-- NTILE: n등분 (사분위 등)
SELECT name, salary,
```

  NTILE(4) OVER (ORDER BY salary DESC) AS quartile
```java
FROM employees;
```

### 6. N번째 값 / 그룹별 Top-N (코테 최빈출)

```java
-- 전체 2번째로 높은 급여 (동점 무시, 서브쿼리)
SELECT MAX(salary) AS second_highest
FROM employees
WHERE salary < (SELECT MAX(salary) FROM employees);

-- DISTINCT + LIMIT OFFSET
SELECT DISTINCT salary
FROM employees
ORDER BY salary DESC
```

LIMIT 1 OFFSET 1;              -- 2번째 (OFFSET은 0부터)

```java
-- N번째 (동점을 같은 등수로): DENSE_RANK
SELECT salary FROM (
  SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
  FROM employees
```

) t
```java
WHERE rnk = 2;

-- 부서별 급여 상위 3명 (그룹별 Top-N의 정석)
SELECT name, dept_id, salary
FROM (
  SELECT name, dept_id, salary,
         ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS rn
  FROM employees
```

) ranked
```java
WHERE rn <= 3;
-- 동점을 모두 포함하려면 ROW_NUMBER 대신 RANK/DENSE_RANK

-- 각 그룹에서 가장 최근 레코드 1건
SELECT * FROM (
  SELECT *,
    ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC) AS rn
  FROM logs
```

) t
```java
WHERE rn = 1;
```

### 7. CASE 조건 분기 & 피벗

```java
-- CASE로 구간 분류
SELECT name,
  CASE
    WHEN salary >= 100000 THEN 'High'
    WHEN salary >= 50000  THEN 'Mid'
```

    ELSE 'Low'
  END AS salary_grade
```java
FROM employees;

-- 조건부 집계 (행→열 피벗)
```

SELECT
  dept_id,
```java
  COUNT(CASE WHEN gender = 'M' THEN 1 END) AS male_count,
  COUNT(CASE WHEN gender = 'F' THEN 1 END) AS female_count,
  SUM(CASE WHEN salary > 50000 THEN salary ELSE 0 END) AS high_salary_sum
FROM employees
GROUP BY dept_id;

-- 월별 매출 피벗 (MONTH 함수 활용)
```

SELECT
  product_id,
```java
  SUM(CASE WHEN MONTH(sale_date) = 1 THEN amount ELSE 0 END) AS jan,
  SUM(CASE WHEN MONTH(sale_date) = 2 THEN amount ELSE 0 END) AS feb
FROM sales
GROUP BY product_id;

-- IF 함수로 간단 분기 (MySQL 전용, 2갈래일 때)
SELECT name, IF(salary > 50000, 'High', 'Low') AS grade FROM employees;
```

### 8. NULL 처리

```java
-- COALESCE: 첫 번째 non-NULL 반환 (표준)
SELECT name, COALESCE(phone, email, 'no contact') AS contact FROM users;

-- IFNULL: 2개 인자용 (MySQL 전용)
SELECT IFNULL(phone, 'none') FROM users;

-- NULL을 기본값으로 (집계 후 NULL 방지)
SELECT COALESCE(SUM(amount), 0) AS total FROM orders WHERE user_id = 5;

-- NULLIF: 두 값이 같으면 NULL → 0으로 나누기 방지
SELECT total / NULLIF(cnt, 0) AS average FROM stats;   -- cnt=0이면 NULL(에러X)

-- NULL 비교는 = 가 아니라 IS
WHERE deleted_at IS NULL          -- O
WHERE deleted_at = NULL           -- X (항상 false, 결과 없음)

-- NULL 안전 비교 <=> (MySQL 전용): NULL끼리도 true
WHERE a <=> b                     -- a,b 둘 다 NULL이어도 매칭
```

### 9. 문자열 처리

```java
-- 연결 (MySQL은 || 가 아니라 CONCAT)
CONCAT(first_name, ' ', last_name)
CONCAT_WS('-', y, m, d)           -- 구분자 지정: y-m-d

-- 부분 문자열 / 길이
SUBSTRING(name, 1, 3)             -- 1번째부터 3글자 (1-based)
```

LEFT(name, 3) / RIGHT(name, 3)
CHAR_LENGTH(name)                 -- 문자 수 (LENGTH는 바이트 수라 한글 주의!)
UPPER(name) / LOWER(name)
TRIM(name) / LTRIM / RTRIM

```java
-- 치환 / 위치
REPLACE(phone, '-', '')
```

LOCATE('@', email)                -- @ 위치 (없으면 0). INSTR도 동일
```java
SUBSTRING_INDEX(email, '@', 1)    -- @ 앞부분만 (MySQL 전용, 매우 유용)

-- 정규식 (MySQL)
WHERE city REGEXP '^[aeiou]'              -- 모음으로 시작
WHERE city REGEXP '[aeiou]$'              -- 모음으로 끝
WHERE name REGEXP '^[A-Z]'                -- 대문자로 시작
WHERE phone REGEXP '^[0-9]{3}-[0-9]{4}$'  -- 패턴 매칭

-- 코테 빈출: 모음으로 시작하고 끝나는 도시명
WHERE city REGEXP '^[aeiou].*[aeiou]$';

-- 패딩
```

LPAD(id, 5, '0')                  -- 00042 처럼 왼쪽 0 채우기

### 10. 날짜/시간 처리

```java
-- 현재 시각
```

NOW()             -- 날짜+시간 (2024-01-15 13:45:00)
CURDATE()         -- 날짜만
CURTIME()         -- 시간만

```java
-- 추출
```

YEAR(d), MONTH(d), DAY(d), HOUR(d), MINUTE(d)
DAYOFWEEK(d)      -- 1=일요일 ~ 7=토요일
DAYNAME(d)        -- 'Monday'
WEEK(d)           -- 몇 번째 주
QUARTER(d)        -- 분기 1~4

```java
-- 날짜 연산
DATE_ADD(order_date, INTERVAL 7 DAY)
DATE_SUB(NOW(), INTERVAL 30 DAY)
```

DATEDIFF(end_date, start_date)    -- 일수 차이 (end - start)
TIMESTAMPDIFF(HOUR, start, end)   -- 시간/분/초 단위 차이

```java
-- 포맷팅
DATE_FORMAT(order_date, '%Y-%m-%d')          -- 2024-01-15
DATE_FORMAT(order_date, '%Y-%m-%d %H:%i:%s') -- 시분초 (%i가 분!)
DATE_FORMAT(order_date, '%Y-%m')             -- 연-월

-- 최근 30일 주문
WHERE order_date >= DATE_SUB(NOW(), INTERVAL 30 DAY);

-- 월별 집계 (그룹핑)
SELECT DATE_FORMAT(order_date, '%Y-%m') AS ym, SUM(amount) AS total
FROM orders
GROUP BY DATE_FORMAT(order_date, '%Y-%m')
ORDER BY ym;
```

주의: %m=월(01), %i=분(00), %H=24시간, %h=12시간. 분을 %m으로 쓰는 실수 잦음.

### 11. 중복 처리

```java
-- 중복 제거 조회
SELECT DISTINCT city FROM customers;

-- 중복 값 탐지 (2번 이상 등장)
SELECT email, COUNT(*) AS cnt
FROM users
GROUP BY email
HAVING COUNT(*) > 1;

-- 중복 행 중 하나만 남기고 삭제 (윈도우 함수)
DELETE FROM users
WHERE id IN (
  SELECT id FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY email ORDER BY id) AS rn
    FROM users
```

  ) t
```java
  WHERE rn > 1
```

);

### 12. 집합 연산

```java
-- UNION: 합집합 (중복 제거) / UNION ALL: 중복 유지 (더 빠름)
SELECT name FROM customers
UNION
SELECT name FROM suppliers;
```

주의: 각 SELECT의 컬럼 수·타입 일치 필요.
MySQL은 INTERSECT/EXCEPT를 8.0.31부터 지원 (그 이하 버전이면 아래로 우회).

```java
-- 교집합 우회 (INTERSECT 대신 INNER JOIN 또는 IN)
SELECT DISTINCT a.id FROM table_a a
JOIN table_b b ON a.id = b.id;

-- 차집합 우회 (EXCEPT 대신 LEFT JOIN + IS NULL 또는 NOT IN)
SELECT a.id FROM table_a a
LEFT JOIN table_b b ON a.id = b.id
WHERE b.id IS NULL;
```

### 13. CTE & 재귀 쿼리 (MySQL 8.0+)

```java
-- CTE (WITH): 서브쿼리에 이름 붙여 가독성↑
WITH dept_avg AS (
  SELECT dept_id, AVG(salary) AS avg_sal
  FROM employees
  GROUP BY dept_id
```

)
```java
SELECT e.name, e.salary, d.avg_sal
FROM employees e
JOIN dept_avg d ON e.dept_id = d.dept_id
WHERE e.salary > d.avg_sal;             -- 부서 평균보다 높은 사람

-- 여러 CTE 체이닝
```

WITH
  filtered AS (SELECT * FROM logs WHERE status = 200),
```java
  counted  AS (SELECT user_id, COUNT(*) AS c FROM filtered GROUP BY user_id)
SELECT * FROM counted WHERE c > 100;

-- 재귀 CTE: 조직도 계층 탐색
WITH RECURSIVE org AS (
  SELECT id, name, manager_id, 1 AS lvl
  FROM employees
  WHERE manager_id IS NULL              -- 앵커: 최상위(CEO)
  UNION ALL
  SELECT e.id, e.name, e.manager_id, o.lvl + 1
  FROM employees e
  JOIN org o ON e.manager_id = o.id     -- 재귀: 부하 직원 계속 탐색
```

)
```java
SELECT * FROM org ORDER BY lvl;

-- 재귀로 숫자 시퀀스 생성 (1~10)
WITH RECURSIVE nums AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM nums WHERE n < 10
```

)
```java
SELECT n FROM nums;
```

재귀 CTE 용도: 조직도, 카테고리 트리, 연속 날짜/숫자 생성, 그래프 경로.

### 14. 코테 문제 유형별 스니펫

[유형] N번째로 높은/낮은 값
→ DISTINCT + ORDER BY + LIMIT OFFSET, 또는 DENSE_RANK

[유형] 그룹별 최댓값을 가진 행
→ 상관 서브쿼리 (salary = MAX) 또는 ROW_NUMBER() = 1

[유형] A에 있고 B에 없는
→ LEFT JOIN ... WHERE b.key IS NULL, 또는 NOT EXISTS

[유형] 연속된 값 찾기 (연속 좌석/번호)
→ (번호 - ROW_NUMBER) 가 같으면 같은 연속 그룹
```java
SELECT MIN(id) AS start_id, MAX(id) AS end_id
FROM (
  SELECT id, id - ROW_NUMBER() OVER (ORDER BY id) AS grp
  FROM seats
```

) t
```java
GROUP BY grp
HAVING COUNT(*) >= 3;          -- 3개 이상 연속
```

[유형] 중앙값
→ ROW_NUMBER로 위치 계산 후 가운데 선택 (MySQL엔 PERCENTILE 없음)
```java
SELECT AVG(salary) AS median FROM (
  SELECT salary,
    ROW_NUMBER() OVER (ORDER BY salary) AS rn,
    COUNT(*) OVER () AS cnt
  FROM employees
```

) t
```java
WHERE rn IN (FLOOR((cnt+1)/2), CEIL((cnt+1)/2));   -- 홀짝 모두 커버
```

[유형] 비율/백분율
→ 조건부 COUNT / 전체 COUNT, 또는 윈도우 SUM OVER

[유형] 피벗 (행→열)
→ SUM(CASE WHEN ... THEN ... ELSE 0 END) GROUP BY

[유형] 날짜 갭 / 연속 기간
→ LAG로 이전 날짜와 DATEDIFF 비교

[유형] 최빈값 (가장 많이 등장)
→ GROUP BY + COUNT DESC + LIMIT 1, 동점 포함은 서브쿼리로 MAX(cnt) 비교

### 15. 실무 성능 주의점 (면접 대비)

- 인덱스 탈락: WHERE에서 컬럼에 함수를 씌우면 인덱스 못 탐
```java
    BAD : WHERE YEAR(created_at) = 2024
    GOOD: WHERE created_at >= '2024-01-01' AND created_at < '2025-01-01'
```

- SELECT * 지양: 필요한 컬럼만. 커버링 인덱스 활용, 네트워크·메모리 절약

- LIKE '%keyword%': 앞 와일드카드는 인덱스 못 탐
    → 접두 검색(LIKE 'keyword%')만 인덱스 사용 가능
    → 부분 검색 필요하면 FULLTEXT 인덱스(MATCH ... AGAINST) 고려

- OFFSET 페이징 함정: OFFSET이 커질수록 앞 행을 다 스캔 → 느림
    BAD : ... ORDER BY id LIMIT 10 OFFSET 100000
    GOOD: WHERE id > 마지막_본_id ORDER BY id LIMIT 10  (커서 기반 페이징)

- COUNT(*) 전체 카운트는 대용량에서 비쌈 → 근사치/캐시 고려

- 조인 조건(ON 절) 컬럼에 인덱스 필수. 카디널리티 낮은 순으로 필터

- EXPLAIN으로 실행 계획 확인: type이 ALL(풀스캔)이면 인덱스 점검
```java
    EXPLAIN SELECT ... ;
```

Java / Spring Boot 실무 연결 (최신 기준):
- Spring Data JPA에서 N+1 문제는 @EntityGraph 또는 JOIN FETCH로 해결.
  복잡한 집계/랭킹은 QueryDSL 프로젝션이나 네이티브 쿼리 + DTO 매핑.
- 커서 기반 페이징(Keyset Pagination)이 대용량 목록 API의 표준.
  Spring Data의 Slice나 QueryDSL로 WHERE id > ? 방식 구현.
- 윈도우 함수(랭킹/누적/증감)는 애플리케이션 루프로 빼지 말고 DB에서 처리.
  MySQL 8.0 윈도우 함수를 네이티브 쿼리로 호출하는 게 대체로 가장 빠름.
- 통계성 조회는 읽기 전용 트랜잭션(@Transactional(readOnly = true))으로.
