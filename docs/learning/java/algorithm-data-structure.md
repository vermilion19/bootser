# 알고리즘 & 특수 자료구조 치트시트

> 코딩테스트 필수 알고리즘과 직접 구현해야 하는 자료구조

---

## 목차

### 알고리즘
- [동적 프로그래밍 (DP)](#동적-프로그래밍-dp)
- [그래프 탐색 (DFS/BFS)](#그래프-탐색)
- [최단 경로](#최단-경로)
- [최소 신장 트리 (MST)](#최소-신장-트리-mst)
- [위상 정렬](#위상-정렬)
- [이진 탐색](#이진-탐색)
- [투 포인터](#투-포인터)
- [슬라이딩 윈도우](#슬라이딩-윈도우)
- [분할 정복](#분할-정복)
- [그리디](#그리디)
- [백트래킹](#백트래킹)

### 특수 자료구조
- [Union-Find (서로소 집합)](#union-find-서로소-집합)
- [세그먼트 트리](#세그먼트-트리)
- [펜윅 트리 (BIT)](#펜윅-트리-bit)
- [트라이 (Trie)](#트라이-trie)
- [LCA (최소 공통 조상)](#lca-최소-공통-조상)
- [희소 배열 (Sparse Table)](#희소-배열-sparse-table)

### 부록
- [알고리즘 선택 가이드](#알고리즘-선택-가이드)
- [문제 유형별 접근법](#문제-유형별-접근법)

---

# 알고리즘

## 동적 프로그래밍 (DP)

> **핵심**: 큰 문제를 작은 문제로 나누고, 중복 계산을 메모이제이션으로 제거

### DP 접근법

```
1. 문제가 DP인지 확인
   - 최적 부분 구조: 작은 문제의 최적해로 큰 문제 해결 가능?
   - 중복 부분 문제: 같은 계산이 반복되는가?

2. 점화식 정의
   - dp[i]가 무엇을 의미하는지 명확히 정의
   - dp[i]를 이전 값들로 어떻게 계산할지 결정

3. 초기값 설정
   - dp[0], dp[1] 등 기저 조건

4. 탐색 순서 결정
   - Bottom-Up: 작은 것부터 큰 것으로
   - Top-Down: 재귀 + 메모이제이션
```

### 1차원 DP

```java
// ── 피보나치 ──
// dp[i] = i번째 피보나치 수
// 점화식: dp[i] = dp[i-1] + dp[i-2]
// 예: dp[5] = 5 → 0, 1, 1, 2, 3, [5]
int[] dp = new int[n + 1];
dp[0] = 0;  // 기저: 0번째 피보나치 = 0
dp[1] = 1;  // 기저: 1번째 피보나치 = 1
for (int i = 2; i <= n; i++) {
    dp[i] = dp[i-1] + dp[i-2];
}

// ── 계단 오르기 (1칸 또는 2칸) ──
// dp[i] = i번째 계단에 도달하는 방법의 수
// 점화식: dp[i] = dp[i-1] + dp[i-2]
//   → i번째에 오려면 (i-1)에서 1칸 오르거나, (i-2)에서 2칸 오르거나
// 예: dp[4] = 5 → {1111, 112, 121, 211, 22} 5가지
dp[1] = 1;  // 1칸: 1가지 (1)
dp[2] = 2;  // 2칸: 2가지 (1+1, 2)
for (int i = 3; i <= n; i++) {
    dp[i] = dp[i-1] + dp[i-2];
}

// ── 최대 부분 수열 합 (카데인 알고리즘) ──
// curSum = arr[i]를 마지막 원소로 하는 연속 부분배열의 최대 합
// maxSum = 지금까지 발견한 전체 최대 합
// 핵심: arr[i]에서 "이전까지의 합을 이어갈지, 새로 시작할지" 선택
// 예: arr = [-2, 1, -3, 4, -1, 2, 1, -5, 4]
//     curSum 변화: -2, 1, -2, 4, 3, 5, 6, 1, 5 → maxSum = 6 (구간: [4,-1,2,1])
int maxSum = arr[0];
int curSum = arr[0];
for (int i = 1; i < n; i++) {
    curSum = Math.max(arr[i], curSum + arr[i]);
    maxSum = Math.max(maxSum, curSum);
}
```

### 2차원 DP

```java
// ── 격자 경로 수 (우/하 이동만 가능) ──
// dp[i][j] = (0,0)에서 (i,j)까지 도달하는 경로의 수
// 점화식: dp[i][j] = dp[i-1][j] + dp[i][j-1]
//   → (i,j)에 오려면 위(i-1,j)에서 내려오거나, 왼쪽(i,j-1)에서 오른쪽으로 오거나
// 예: 3×3 격자 → dp[2][2] = 6
//   dp = [[1, 1, 1],
//          [1, 2, 3],
//          [1, 3, 6]]
int[][] dp = new int[n][m];
for (int i = 0; i < n; i++) dp[i][0] = 1;  // 첫 열: 아래로만 갈 수 있으므로 1가지
for (int j = 0; j < m; j++) dp[0][j] = 1;  // 첫 행: 오른쪽으로만 갈 수 있으므로 1가지

for (int i = 1; i < n; i++) {
    for (int j = 1; j < m; j++) {
        dp[i][j] = dp[i-1][j] + dp[i][j-1];
    }
}

// ── 격자 최소 비용 경로 ──
// dp[i][j] = (0,0)에서 (i,j)까지의 최소 비용 합
// 점화식: dp[i][j] = min(dp[i-1][j], dp[i][j-1]) + cost[i][j]
//   → 위에서 오는 비용 vs 왼쪽에서 오는 비용 중 작은 쪽 + 현재 칸 비용
// 예: cost = [[1,3,1],[1,5,1],[4,2,1]]
//     dp   = [[1,4,5],[2,7,6],[6,8,7]] → 최소비용 = 7
int[][] cost = new int[n][m];  // 입력
int[][] dp = new int[n][m];
dp[0][0] = cost[0][0];  // 시작점 비용

for (int i = 1; i < n; i++) dp[i][0] = dp[i-1][0] + cost[i][0];  // 첫 열: 위에서만 올 수 있음
for (int j = 1; j < m; j++) dp[0][j] = dp[0][j-1] + cost[0][j];  // 첫 행: 왼쪽에서만 올 수 있음

for (int i = 1; i < n; i++) {
    for (int j = 1; j < m; j++) {
        dp[i][j] = Math.min(dp[i-1][j], dp[i][j-1]) + cost[i][j];
    }
}
```

### LCS (최장 공통 부분수열)

```java
// dp[i][j] = s1의 앞 i글자와 s2의 앞 j글자 사이의 최장 공통 부분수열 길이
// 점화식:
//   s1[i-1] == s2[j-1] → dp[i][j] = dp[i-1][j-1] + 1  (공통 문자 발견, 이전 LCS에 +1)
//   s1[i-1] != s2[j-1] → dp[i][j] = max(dp[i-1][j], dp[i][j-1])  (한쪽을 빼고 더 긴 쪽 선택)
// 예: s1="ABCDE", s2="ACE"
//     dp[5][3] = 3 → LCS = "ACE"
//       ""  A  C  E
//   ""  [0, 0, 0, 0]
//   A   [0, 1, 1, 1]
//   B   [0, 1, 1, 1]
//   C   [0, 1, 2, 2]
//   D   [0, 1, 2, 2]
//   E   [0, 1, 2, 3]
public static int lcs(String s1, String s2) {
    int n = s1.length(), m = s2.length();
    int[][] dp = new int[n + 1][m + 1];  // dp[0][*], dp[*][0]은 빈 문자열이므로 0

    for (int i = 1; i <= n; i++) {
        for (int j = 1; j <= m; j++) {
            if (s1.charAt(i-1) == s2.charAt(j-1)) {
                dp[i][j] = dp[i-1][j-1] + 1;
            } else {
                dp[i][j] = Math.max(dp[i-1][j], dp[i][j-1]);
            }
        }
    }
    return dp[n][m];
}
```

### LIS (최장 증가 부분수열)

```java
// ── O(n^2) 풀이 ──
// dp[i] = arr[i]를 마지막 원소로 하는 최장 증가 부분수열의 길이
// 점화식: arr[j] < arr[i]인 모든 j(0..i-1)에 대해 dp[i] = max(dp[j] + 1)
//   → i 이전에 나보다 작은 값 중, 가장 긴 LIS 뒤에 나를 붙인다
// 예: arr = [10, 20, 10, 30, 20, 50]
//     dp  = [ 1,  2,  1,  3,  2,  4] → 답 = 4 (10,20,30,50)
public static int lisN2(int[] arr) {
    int n = arr.length;
    int[] dp = new int[n];
    Arrays.fill(dp, 1);  // 자기 자신만으로 길이 1

    for (int i = 1; i < n; i++) {
        for (int j = 0; j < i; j++) {
            if (arr[j] < arr[i]) {
                dp[i] = Math.max(dp[i], dp[j] + 1);
            }
        }
    }
    return Arrays.stream(dp).max().getAsInt();
}

// ── O(n log n) 풀이 (이진 탐색) ──
// lis 리스트: "길이가 k인 증가 부분수열의 마지막 원소 최솟값"을 유지하는 배열
// lis[k] = 길이가 (k+1)인 LIS를 만들 수 있는 가장 작은 마지막 값
// 주의: lis 배열 자체가 실제 LIS는 아님! 길이만 정확함
// 예: arr = [10, 20, 10, 30, 20, 50]
//     lis 변화: [10] → [10,20] → [10,20] → [10,20,30] → [10,20,30] → [10,20,30,50]
//     → lis.size() = 4
public static int lisNlogN(int[] arr) {
    int n = arr.length;
    List<Integer> lis = new ArrayList<>();

    for (int num : arr) {
        int pos = Collections.binarySearch(lis, num);
        if (pos < 0) pos = -(pos + 1);  // 삽입 위치

        if (pos == lis.size()) {
            lis.add(num);       // 현재 LIS보다 큰 값 → 뒤에 추가 (LIS 길이 증가)
        } else {
            lis.set(pos, num);  // 기존 위치 값을 더 작은 값으로 교체 (미래에 더 긴 LIS 가능)
        }
    }
    return lis.size();
}
```

### 배낭 문제 (Knapsack)

```java
// ── 0-1 배낭 (각 물건 1개씩만) ──
// dp[i][c] = 앞에서 i개의 물건을 고려했을 때, 배낭 용량 c에서의 최대 가치
// 점화식:
//   w > c (물건이 안 들어감) → dp[i][c] = dp[i-1][c]         (i번째 물건 안 넣음)
//   w <= c                  → dp[i][c] = max(dp[i-1][c],      (안 넣는 경우)
//                                            dp[i-1][c-w] + v) (넣는 경우: 용량 w만큼 비우고 가치 v 얻음)
// 예: weights=[2,3,4], values=[3,4,5], capacity=5
//     dp[3][5] = 7 (물건0 + 물건1: 무게 2+3=5, 가치 3+4=7)
public static int knapsack01(int[] weights, int[] values, int capacity) {
    int n = weights.length;
    int[][] dp = new int[n + 1][capacity + 1];  // dp[0][*] = 0 (물건 0개 → 가치 0)

    for (int i = 1; i <= n; i++) {
        int w = weights[i-1], v = values[i-1];
        for (int c = 0; c <= capacity; c++) {
            if (w > c) {
                dp[i][c] = dp[i-1][c];
            } else {
                dp[i][c] = Math.max(dp[i-1][c], dp[i-1][c-w] + v);
            }
        }
    }
    return dp[n][capacity];
}

// ── 1차원 배열로 최적화 (공간 O(capacity)) ──
// dp[c] = 현재까지 고려한 물건들로, 용량 c에서의 최대 가치
// 역순 순회 이유: dp[c-w]가 "이전 행(i-1)"의 값이어야 하는데,
//   정순이면 이미 갱신된 "현재 행(i)"의 값을 참조하게 됨 → 같은 물건 중복 사용 문제
//   역순이면 dp[c-w]는 아직 갱신 안 된 이전 행 값 → 올바른 0-1 배낭
public static int knapsack01Optimized(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];  // dp[0] = 0 (용량 0 → 가치 0)

    for (int i = 0; i < weights.length; i++) {
        int w = weights[i], v = values[i];
        for (int c = capacity; c >= w; c--) {  // 역순! (0-1 배낭의 핵심)
            dp[c] = Math.max(dp[c], dp[c-w] + v);
        }
    }
    return dp[capacity];
}

// ── 무한 배낭 (물건 무제한) ──
// dp[c] = 용량 c에서 얻을 수 있는 최대 가치 (각 물건을 여러 번 사용 가능)
// 0-1 배낭과의 차이: 용량을 정순으로 순회
//   → dp[c-w]가 "현재 행"의 값 = 같은 물건을 이미 넣은 상태에서 또 넣을 수 있음
// 예: weights=[2,3], values=[3,4], capacity=7
//     dp[7] = 10 (무게2 물건 2개 + 무게3 물건 1개: 3+3+4=10)
public static int knapsackUnbounded(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];  // dp[0] = 0

    for (int c = 1; c <= capacity; c++) {
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= c) {
                dp[c] = Math.max(dp[c], dp[c - weights[i]] + values[i]);
            }
        }
    }
    return dp[capacity];
}
```

### 동전 교환

```java
// ── 최소 동전 개수 ──
// dp[i] = 금액 i를 만들기 위해 필요한 최소 동전 개수
// 점화식: dp[i] = min(dp[i - coin] + 1) for each coin <= i
//   → 금액 i에서 동전 하나를 빼면 (i-coin)이 되고, 거기에 동전 1개 추가
// 예: coins=[1,3,5], amount=7
//     dp = [0, 1, 2, 1, 2, 1, 2, 3] → dp[7] = 3 (5+1+1 또는 3+3+1)
public static int coinChange(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, amount + 1);  // 불가능한 값 (amount+1은 절대 나올 수 없는 큰 값)
    dp[0] = 0;                    // 기저: 금액 0을 만드는 데 동전 0개

    for (int i = 1; i <= amount; i++) {
        for (int coin : coins) {
            if (coin <= i) {
                dp[i] = Math.min(dp[i], dp[i - coin] + 1);
            }
        }
    }
    return dp[amount] > amount ? -1 : dp[amount];  // amount+1이면 만들 수 없는 금액
}

// ── 동전 조합 수 (순서 상관 X) ──
// dp[i] = 금액 i를 만드는 조합의 수 (1+2와 2+1을 같은 것으로 취급)
// 바깥 루프가 동전인 이유:
//   동전 [1,2]로 금액 3을 만들 때
//   - 동전이 바깥 루프: 1을 먼저 다 쓰고, 그 다음 2를 고려 → {1+1+1, 1+2} = 2가지 (조합)
//   - 금액이 바깥 루프: 매 금액마다 모든 동전 선택 가능 → {1+1+1, 1+2, 2+1} = 3가지 (순열)
// 예: coins=[1,2,5], amount=5
//     dp[5] = 4 → {11111, 1112, 122, 5}
public static int coinCombinations(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    dp[0] = 1;  // 기저: 금액 0을 만드는 방법 = 아무것도 안 쓰기 1가지

    for (int coin : coins) {  // 동전을 바깥 루프에! (조합 = 순서 무시)
        for (int i = coin; i <= amount; i++) {
            dp[i] += dp[i - coin];
        }
    }
    return dp[amount];
}
```

### Top-Down DP (메모이제이션)

```java
// memo[n] = n번째 피보나치 수 (계산 완료 시), -1이면 아직 미계산
// Top-Down vs Bottom-Up 차이:
//   Bottom-Up: for문으로 dp[0]부터 dp[n]까지 순차 계산 (위의 예시들)
//   Top-Down:  재귀로 dp[n]부터 시작, 필요한 하위 문제만 계산 + 결과 캐싱
// memo의 역할: 이미 계산한 값을 저장해서 같은 재귀 호출 시 O(1)로 반환
//   → memo 없으면 fib(50)은 2^50번 호출, memo 있으면 50번만 호출
static int[] memo;

public static int fib(int n) {
    if (n <= 1) return n;              // 기저 조건
    if (memo[n] != -1) return memo[n]; // 이미 계산했으면 바로 반환 (핵심!)

    memo[n] = fib(n-1) + fib(n-2);    // 계산 후 저장
    return memo[n];
}

// 사용 전 초기화 (-1 = "아직 계산 안 함"을 의미하는 센티넬 값)
memo = new int[n + 1];
Arrays.fill(memo, -1);
```

---

## 그래프 탐색

### 그래프 표현

```java
// ── 인접 리스트 (권장) ──
// graph.get(u) = 노드 u에서 갈 수 있는 이웃 노드 목록
// 메모리: O(V + E), 탐색: 특정 노드의 이웃 순회 O(degree)
// 대부분의 그래프 문제에서 사용 (간선이 적은 희소 그래프에 효율적)
List<List<Integer>> graph = new ArrayList<>();
for (int i = 0; i < n; i++) {
    graph.add(new ArrayList<>());
}
graph.get(u).add(v);  // u → v 간선 (유향)
// graph.get(v).add(u);  // 무향 그래프면 양방향 추가

// ── 가중치 그래프 ──
// graph.get(u) = {목적지, 가중치} 쌍의 목록
// 다익스트라, 프림 등 가중치 알고리즘에서 사용
List<List<int[]>> graph = new ArrayList<>();
graph.get(u).add(new int[]{v, weight});  // u → v, 비용 weight

// ── 인접 행렬 (밀집 그래프, N이 작을 때) ──
// adj[u][v] = u에서 v로의 가중치 (0이면 간선 없음)
// 메모리: O(V²), 간선 존재 확인: O(1)
// 플로이드-워셜, N이 작은 경우(N ≤ 500)에 사용
int[][] adj = new int[n][n];
adj[u][v] = weight;
```

### DFS (깊이 우선 탐색)

```java
// visited[i] = 노드 i를 이미 방문했는지 여부
// 방문 체크를 안 하면 무한 루프 (무향 그래프) 또는 중복 탐색 발생
static boolean[] visited;
static List<List<Integer>> graph;

// ── 재귀 DFS ──
// 한 방향으로 끝까지 깊이 들어간 뒤 되돌아옴 (콜스택 활용)
// 용도: 연결 요소 탐색, 사이클 탐지, 위상정렬, 경로 존재 확인
// 주의: 노드가 10만 이상이면 StackOverflow 위험 → 스택 DFS 사용
public static void dfs(int node) {
    visited[node] = true;
    System.out.println(node);

    for (int next : graph.get(node)) {
        if (!visited[next]) {
            dfs(next);
        }
    }
}

// ── 스택 DFS ──
// 재귀 대신 명시적 스택 사용 (StackOverflow 방지)
// 탐색 순서가 재귀 DFS와 약간 다를 수 있음 (이웃 추가 순서 역전)
public static void dfsStack(int start) {
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(start);

    while (!stack.isEmpty()) {
        int node = stack.pop();
        if (visited[node]) continue;  // 스택에 중복 삽입될 수 있어서 여기서 체크
        visited[node] = true;

        for (int next : graph.get(node)) {
            if (!visited[next]) {
                stack.push(next);
            }
        }
    }
}
```

### BFS (너비 우선 탐색)

```java
// ── 기본 BFS ──
// 시작점에서 가까운 노드부터 탐색 (레벨 순서)
// DFS와 달리 "최단 거리"를 보장 (가중치 없는 그래프에서)
// 핵심: visited를 큐에 넣을 때 체크 (꺼낼 때 하면 중복 삽입됨)
public static void bfs(int start) {
    Deque<Integer> queue = new ArrayDeque<>();
    boolean[] visited = new boolean[n];

    queue.offer(start);
    visited[start] = true;  // 넣을 때 방문 처리!

    while (!queue.isEmpty()) {
        int node = queue.poll();

        for (int next : graph.get(node)) {
            if (!visited[next]) {
                visited[next] = true;  // 넣을 때 방문 처리 (꺼낼 때 하면 중복 삽입)
                queue.offer(next);
            }
        }
    }
}

// ── 최단 거리 BFS (가중치 없는 그래프) ──
// dist[i] = 시작점에서 노드 i까지의 최단 거리 (간선 수)
//   -1이면 도달 불가능
// BFS는 가까운 노드부터 방문하므로, 처음 도달한 거리가 곧 최단 거리
// 예: 0→1→2, 0→3 그래프에서 bfsDistance(0) = [0, 1, 2, 1]
public static int[] bfsDistance(int start) {
    int[] dist = new int[n];
    Arrays.fill(dist, -1);  // -1 = 미방문 (visited 배열 역할도 겸함)
    Deque<Integer> queue = new ArrayDeque<>();

    queue.offer(start);
    dist[start] = 0;

    while (!queue.isEmpty()) {
        int node = queue.poll();

        for (int next : graph.get(node)) {
            if (dist[next] == -1) {  // 미방문
                dist[next] = dist[node] + 1;  // 이전 노드 거리 + 1
                queue.offer(next);
            }
        }
    }
    return dist;
}
```

### 연결 요소 개수

```java
// 서로 연결되지 않은 그래프 덩어리(컴포넌트)의 수를 구함
// 원리: 미방문 노드를 발견할 때마다 DFS/BFS로 연결된 모든 노드를 방문 → 1개의 컴포넌트
// 예: 0-1, 2-3, 4 (독립) → 3개의 연결 요소
public static int countComponents() {
    boolean[] visited = new boolean[n];
    int count = 0;

    for (int i = 0; i < n; i++) {
        if (!visited[i]) {   // 아직 어떤 컴포넌트에도 속하지 않은 노드 발견
            dfs(i);           // 이 노드와 연결된 모든 노드 방문 처리
            count++;          // 새로운 컴포넌트 1개 발견
        }
    }
    return count;
}
```

### 이분 그래프 판별

```java
// 이분 그래프: 모든 간선의 양 끝 노드가 서로 다른 색으로 칠해질 수 있는 그래프
// 즉, 노드를 두 그룹으로 나눌 때, 같은 그룹 내에 간선이 없어야 함
// 활용: 매칭 문제, "두 편으로 나눌 수 있는가?" 유형
// color[i] = 노드 i의 색 (0: 미방문, 1: 그룹A, 2: 그룹B)
public static boolean isBipartite() {
    int[] color = new int[n];

    for (int i = 0; i < n; i++) {
        if (color[i] == 0) {  // 연결 요소마다 따로 체크 (비연결 그래프 대응)
            if (!bfsCheck(i, color)) return false;
        }
    }
    return true;
}

private static boolean bfsCheck(int start, int[] color) {
    Deque<Integer> queue = new ArrayDeque<>();
    queue.offer(start);
    color[start] = 1;  // 시작 노드를 그룹A(1)로 칠함

    while (!queue.isEmpty()) {
        int node = queue.poll();

        for (int next : graph.get(node)) {
            if (color[next] == 0) {
                color[next] = 3 - color[node];  // 1→2, 2→1 (인접 노드는 반대 색)
                queue.offer(next);
            } else if (color[next] == color[node]) {
                return false;  // 인접한 두 노드가 같은 색 → 이분 그래프 아님
            }
        }
    }
    return true;
}
```

### 사이클 탐지

```java
// ── 무향 그래프 사이클 탐지 (Union-Find) ──
// 원리: 간선 (u, v)를 추가할 때, u와 v가 이미 같은 집합이면 사이클
//   → 이미 연결된 두 노드 사이에 또 간선이 생기면 순환 경로가 만들어짐
// parent[i] = 노드 i가 속한 집합의 대표 (루트)
static int[] parent;

public static boolean hasCycleUndirected() {
    parent = new int[n];
    for (int i = 0; i < n; i++) parent[i] = i;

    for (int u = 0; u < n; u++) {
        for (int v : graph.get(u)) {
            if (u < v) {  // 무향 간선 (u,v)와 (v,u)가 둘 다 있으므로 한 번만 처리
                if (find(u) == find(v)) return true;  // 이미 같은 집합 → 사이클!
                union(u, v);
            }
        }
    }
    return false;
}

// ── 유향 그래프 사이클 탐지 (DFS 3색 칠하기) ──
// state[i] = 노드 i의 탐색 상태
//   0(WHITE): 아직 방문 안 함
//   1(GRAY):  현재 DFS 경로 위에 있음 (방문 중, 아직 후손 탐색 중)
//   2(BLACK): 이 노드에서 시작하는 모든 탐색 완료
// 핵심: GRAY 노드를 다시 만나면 → 현재 경로에서 자기 자신으로 돌아온 것 → 사이클!
// BLACK 노드를 만나는 건 사이클이 아님 (이미 완료된 다른 경로)
static int[] state;

public static boolean hasCycleDirected() {
    state = new int[n];
    for (int i = 0; i < n; i++) {
        if (state[i] == 0 && dfsCycle(i)) {
            return true;
        }
    }
    return false;
}

private static boolean dfsCycle(int node) {
    state[node] = 1;  // GRAY: 현재 탐색 경로에 진입

    for (int next : graph.get(node)) {
        if (state[next] == 1) return true;   // GRAY를 다시 만남 → 사이클!
        if (state[next] == 0 && dfsCycle(next)) return true;  // WHITE → 탐색 계속
        // state[next] == 2 (BLACK)이면 이미 완료된 노드 → 무시
    }

    state[node] = 2;  // BLACK: 이 노드의 모든 후손 탐색 완료
    return false;
}
```

---

## 최단 경로

### 다익스트라 (Dijkstra)

> **음수 가중치 없는** 단일 시작점 최단 경로. O(E log V)

```java
// dist[i] = 시작점에서 노드 i까지의 최단 거리 (가중치 합)
// 원리: 아직 확정되지 않은 노드 중 거리가 가장 짧은 노드를 선택 → 그 노드를 통해 이웃 갱신
// PriorityQueue: 거리가 가장 짧은 노드를 O(log V)에 꺼냄
// 예: 0→1(4), 0→2(1), 2→1(2) → dist = [0, 3, 1] (0→2→1이 0→1보다 짧음)
public static int[] dijkstra(int start, List<List<int[]>> graph) {
    int n = graph.size();
    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);  // 무한대 = 아직 도달 불가
    dist[start] = 0;  // 시작점은 거리 0

    // {거리, 노드} — 거리가 작은 것부터 꺼냄 (최소 힙)
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, start});

    while (!pq.isEmpty()) {
        int[] cur = pq.poll();
        int d = cur[0], u = cur[1];

        // 이미 더 짧은 경로로 확정된 노드면 스킵 (중복 제거)
        // PQ에 같은 노드가 여러 번 들어갈 수 있기 때문에 필요
        if (d > dist[u]) continue;

        // u를 거쳐가는 경로가 기존보다 짧으면 갱신 (Relaxation)
        for (int[] edge : graph.get(u)) {
            int v = edge[0], w = edge[1];
            if (dist[u] + w < dist[v]) {
                dist[v] = dist[u] + w;
                pq.offer(new int[]{dist[v], v});
            }
        }
    }
    return dist;
}
```

### 벨만-포드 (Bellman-Ford)

> **음수 가중치 허용**, 음수 사이클 탐지. O(VE)

```java
// dist[i] = 시작점에서 노드 i까지의 최단 거리
// 원리: 모든 간선을 V-1번 반복해서 Relaxation
//   → 최단 경로는 최대 V-1개의 간선을 거치므로, V-1번이면 충분
// 다익스트라와의 차이: 음수 가중치 허용, 음수 사이클 탐지 가능
// 단점: O(VE)로 다익스트라보다 느림
public static int[] bellmanFord(int start, int n, int[][] edges) {
    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[start] = 0;

    // V-1번 반복: 매 반복마다 최소 1개 노드의 최단 거리가 확정됨
    for (int i = 0; i < n - 1; i++) {
        for (int[] edge : edges) {
            int u = edge[0], v = edge[1], w = edge[2];
            if (dist[u] != Integer.MAX_VALUE && dist[u] + w < dist[v]) {
                dist[v] = dist[u] + w;  // Relaxation
            }
        }
    }

    // 음수 사이클 체크: V-1번 후에도 갱신되면 → 돌 때마다 계속 줄어듦 → 음수 사이클
    for (int[] edge : edges) {
        int u = edge[0], v = edge[1], w = edge[2];
        if (dist[u] != Integer.MAX_VALUE && dist[u] + w < dist[v]) {
            return null;  // 음수 사이클 존재 → 최단 거리 정의 불가
        }
    }

    return dist;
}
```

### 플로이드-워셜 (Floyd-Warshall)

> **모든 쌍 최단 경로**. O(V³)

```java
// dist[i][j] = 노드 i에서 노드 j까지의 최단 거리
// 원리: "노드 0~k까지를 경유지로 사용했을 때의 최단 거리"를 점진적으로 확장
//   k=0: 직접 연결만 고려
//   k=1: 노드 0을 경유하는 경로도 고려
//   k=n-1: 모든 노드를 경유할 수 있음 → 전체 최단 거리 완성
// 핵심 점화식: dist[i][j] = min(dist[i][j], dist[i][k] + dist[k][j])
//   → i→j 직접 가는 것 vs i→k→j 경유하는 것 중 짧은 쪽
// 주의: k가 반드시 가장 바깥 루프여야 함! (i,j,k 순서로 하면 틀림)
public static int[][] floydWarshall(int[][] graph) {
    int n = graph.length;
    int[][] dist = new int[n][n];
    int INF = Integer.MAX_VALUE / 2;  // /2 하는 이유: dist[i][k]+dist[k][j] 시 오버플로우 방지

    // 초기화: 직접 연결된 간선의 가중치로 설정
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
            if (i == j) dist[i][j] = 0;            // 자기 자신까지 거리 = 0
            else if (graph[i][j] != 0) dist[i][j] = graph[i][j];  // 직접 간선
            else dist[i][j] = INF;                  // 간선 없음 = 무한대
        }
    }

    // k를 거쳐가는 경로를 고려하여 최단 거리 갱신
    for (int k = 0; k < n; k++) {       // 경유지 (반드시 가장 바깥!)
        for (int i = 0; i < n; i++) {   // 출발지
            for (int j = 0; j < n; j++) { // 도착지
                if (dist[i][k] + dist[k][j] < dist[i][j]) {
                    dist[i][j] = dist[i][k] + dist[k][j];
                }
            }
        }
    }

    return dist;
}
```

### 최단 경로 알고리즘 선택

| 상황 | 알고리즘 | 시간복잡도 |
|------|----------|-----------|
| 가중치 X (BFS로 충분) | BFS | O(V + E) |
| 가중치 O, 음수 X, 단일 시작점 | 다익스트라 | O(E log V) |
| 음수 가중치 O, 단일 시작점 | 벨만-포드 | O(VE) |
| 모든 쌍 최단 경로 | 플로이드-워셜 | O(V³) |

---

## 최소 신장 트리 (MST)

### 크루스칼 (Kruskal)

> 간선 정렬 + Union-Find. O(E log E)

```java
// MST(최소 신장 트리): 모든 노드를 연결하면서 간선 가중치 합이 최소인 트리
// 크루스칼 원리: 가중치가 작은 간선부터 선택, 사이클이 안 생기면 추가
//   → 간선 정렬 O(E log E) + Union-Find로 사이클 판별 O(α(N)) ≈ O(1)
// MST 간선 수 = V-1 (트리의 성질)
static int[] parent, rank_;

public static int kruskal(int n, int[][] edges) {
    // edges: {u, v, weight} — 가중치 오름차순 정렬
    Arrays.sort(edges, (a, b) -> a[2] - b[2]);

    parent = new int[n];
    rank_ = new int[n];
    for (int i = 0; i < n; i++) parent[i] = i;  // 초기: 각 노드가 자기 자신이 루트

    int mstWeight = 0;   // MST 총 가중치
    int edgeCount = 0;   // MST에 추가된 간선 수

    for (int[] edge : edges) {
        int u = edge[0], v = edge[1], w = edge[2];

        if (find(u) != find(v)) {  // u와 v가 다른 집합 → 사이클 안 생김
            union(u, v);            // 두 집합 합치기
            mstWeight += w;
            edgeCount++;

            if (edgeCount == n - 1) break;  // MST 완성 (간선 V-1개)
        }
        // find(u) == find(v)이면 같은 집합 → 추가하면 사이클 → 스킵
    }

    return edgeCount == n - 1 ? mstWeight : -1;  // V-1개 못 모으면 연결 안 된 그래프
}

private static int find(int x) {
    if (parent[x] != x) parent[x] = find(parent[x]);
    return parent[x];
}

private static void union(int x, int y) {
    int px = find(x), py = find(y);
    if (px == py) return;
    if (rank_[px] < rank_[py]) { int t = px; px = py; py = t; }
    parent[py] = px;
    if (rank_[px] == rank_[py]) rank_[px]++;
}
```

### 프림 (Prim)

> 정점 기준 확장. O(E log V)

```java
// 프림 원리: 이미 MST에 포함된 노드 집합에서 가장 가까운 노드를 하나씩 추가
//   → 다익스트라와 구조가 비슷하지만, PQ에 "시작점~노드 거리"가 아닌 "간선 가중치"를 넣음
// 크루스칼과의 차이:
//   크루스칼 = 간선 중심 (전체 간선 정렬), 프림 = 정점 중심 (현재 트리에서 확장)
//   밀집 그래프(E가 큰 경우)에서는 프림이 유리
public static int prim(List<List<int[]>> graph) {
    int n = graph.size();
    boolean[] visited = new boolean[n];  // MST에 포함된 노드

    // {가중치, 노드} — 가중치가 작은 것부터 꺼냄
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, 0});  // 시작 노드 0, 비용 0

    int mstWeight = 0;
    int edgeCount = 0;

    while (!pq.isEmpty() && edgeCount < n) {
        int[] cur = pq.poll();
        int w = cur[0], u = cur[1];

        if (visited[u]) continue;  // 이미 MST에 포함된 노드면 스킵
        visited[u] = true;         // MST에 추가
        mstWeight += w;
        edgeCount++;

        // 새로 추가된 노드의 이웃 간선을 PQ에 넣음
        for (int[] edge : graph.get(u)) {
            int v = edge[0], weight = edge[1];
            if (!visited[v]) {
                pq.offer(new int[]{weight, v});
            }
        }
    }

    return edgeCount == n ? mstWeight : -1;
}
```

---

## 위상 정렬

> **DAG (사이클 없는 유향 그래프)** 에서 순서 결정. O(V + E)

### Kahn's Algorithm (BFS)

```java
// 위상 정렬: 방향 그래프에서 "선행 조건을 만족하는 순서"로 노드를 나열
// 예: 수강 선수과목, 빌드 의존성, 작업 스케줄링
// indegree[i] = 노드 i로 들어오는 간선 수 (선행 조건의 수)
//   → indegree가 0 = 선행 조건이 없음 = 지금 바로 수행 가능
// Kahn's 원리: indegree 0인 노드를 꺼내고, 그 노드에서 나가는 간선을 제거 → 반복
public static List<Integer> topologicalSort(List<List<Integer>> graph) {
    int n = graph.size();
    int[] indegree = new int[n];

    // 각 노드의 진입 차수 계산
    for (int u = 0; u < n; u++) {
        for (int v : graph.get(u)) {
            indegree[v]++;  // u→v 간선이 있으면 v의 진입 차수 증가
        }
    }

    // 진입 차수 0인 노드 = 선행 조건 없음 → 바로 처리 가능
    Deque<Integer> queue = new ArrayDeque<>();
    for (int i = 0; i < n; i++) {
        if (indegree[i] == 0) queue.offer(i);
    }

    List<Integer> result = new ArrayList<>();

    while (!queue.isEmpty()) {
        int u = queue.poll();
        result.add(u);  // u를 결과에 추가 (u의 선행 조건은 모두 처리됨)

        for (int v : graph.get(u)) {
            indegree[v]--;  // u→v 간선 제거 (u가 처리됐으므로)
            if (indegree[v] == 0) {  // v의 모든 선행 조건이 처리됨
                queue.offer(v);
            }
        }
    }

    // 모든 노드가 결과에 포함되지 않으면 → 사이클 존재 (상호 의존)
    if (result.size() != n) {
        return null;
    }

    return result;
}
```

### DFS 기반 위상 정렬

```java
// DFS 기반 위상 정렬
// 원리: DFS에서 모든 후손 탐색이 끝난 뒤(후위 순서) 스택에 넣으면
//   → 스택을 pop하면 위상 순서가 됨
// 이유: 노드 A가 B보다 먼저 와야 한다면 (A→B), DFS에서 B가 먼저 완료되고
//   A가 나중에 완료됨 → 스택에서는 A가 위에 있음 → pop하면 A가 먼저 나옴
static boolean[] visited;
static Deque<Integer> stack;

public static List<Integer> topologicalSortDFS(List<List<Integer>> graph) {
    int n = graph.size();
    visited = new boolean[n];
    stack = new ArrayDeque<>();

    for (int i = 0; i < n; i++) {
        if (!visited[i]) {
            dfs(i, graph);
        }
    }

    List<Integer> result = new ArrayList<>();
    while (!stack.isEmpty()) {
        result.add(stack.pop());  // 나중에 완료된 노드가 먼저 나옴 = 위상 순서
    }
    return result;
}

private static void dfs(int node, List<List<Integer>> graph) {
    visited[node] = true;
    for (int next : graph.get(node)) {
        if (!visited[next]) {
            dfs(next, graph);
        }
    }
    stack.push(node);  // 후위 순서: 모든 후손 탐색 완료 후 스택에 추가
}
```

---

## 이진 탐색

### 기본 이진 탐색

```java
// ── 기본 이진 탐색 ──
// 정렬된 배열에서 target의 인덱스를 찾음
// lo, hi = 탐색 범위의 양 끝 인덱스
// mid = lo + (hi - lo) / 2 → (lo + hi) / 2와 같지만 오버플로우 방지
// 종료 조건: lo > hi (lo <= hi인 동안 반복)
// 예: arr=[1,3,5,7,9], target=5 → mid 순서: 2 → return 2
public static int binarySearch(int[] arr, int target) {
    int lo = 0, hi = arr.length - 1;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;

        if (arr[mid] == target) return mid;       // 찾음!
        else if (arr[mid] < target) lo = mid + 1; // target이 오른쪽에 있음
        else hi = mid - 1;                        // target이 왼쪽에 있음
    }

    return -1;  // 배열에 없음
}
```

### Lower Bound / Upper Bound

```java
// ── Lower Bound: target 이상인 첫 번째 위치 ──
// 반환값: arr[lo] >= target인 가장 작은 lo
// 용도: "target을 삽입할 수 있는 가장 왼쪽 위치"
// 주의: lo < hi (등호 없음), hi = arr.length (배열 끝 너머까지)
// 예: arr=[1,2,4,4,6], target=4 → return 2 (첫 번째 4의 위치)
// 예: arr=[1,2,4,4,6], target=3 → return 2 (3이 들어갈 위치)
public static int lowerBound(int[] arr, int target) {
    int lo = 0, hi = arr.length;  // hi = length (배열 밖, "모든 원소가 target 미만"인 경우 대응)

    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] >= target) hi = mid;  // target 이상이면 왼쪽으로 (이 위치가 답일 수 있음)
        else lo = mid + 1;                 // target 미만이면 오른쪽으로
    }

    return lo;
}

// ── Upper Bound: target 초과인 첫 번째 위치 ──
// 반환값: arr[lo] > target인 가장 작은 lo
// 용도: "target을 삽입할 수 있는 가장 오른쪽 위치"
// 예: arr=[1,2,4,4,6], target=4 → return 4 (마지막 4 다음 위치)
public static int upperBound(int[] arr, int target) {
    int lo = 0, hi = arr.length;

    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] > target) hi = mid;  // target 초과면 왼쪽으로
        else lo = mid + 1;               // target 이하면 오른쪽으로
    }

    return lo;
}

// target 개수 = upperBound - lowerBound
// 예: arr=[1,2,4,4,6], target=4 → upper(4)-lower(4) = 4-2 = 2개
```

### 파라메트릭 서치

> "조건을 만족하는 최솟값/최댓값 찾기" → 이진 탐색으로 변환

```java
// ── 파라메트릭 서치: 조건을 만족하는 최솟값 찾기 ──
// 전제: check(x)가 특정 값 이상에서 true, 미만에서 false (단조성)
//   → FFFFTTTTT 형태에서 첫 번째 T를 찾는 것
// 예: "길이 x 이상의 막대를 3개 이상 만들 수 있는가?" → 최소 x 찾기
public static int parametricMin(int lo, int hi) {
    int answer = -1;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;

        if (check(mid)) {
            answer = mid;     // 조건 만족 → 일단 저장
            hi = mid - 1;     // 더 작은 값에서도 만족하는지 탐색
        } else {
            lo = mid + 1;     // 조건 불만족 → 더 큰 값으로
        }
    }

    return answer;
}

// ── 파라메트릭 서치: 조건을 만족하는 최댓값 찾기 ──
// 전제: check(x)가 특정 값 이하에서 true, 초과에서 false (단조성)
//   → TTTTTFFFF 형태에서 마지막 T를 찾는 것
// 예: "절단기 높이 x로 잘라도 M미터를 가져갈 수 있는가?" → 최대 x 찾기
public static int parametricMax(int lo, int hi) {
    int answer = -1;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;

        if (check(mid)) {
            answer = mid;     // 조건 만족 → 일단 저장
            lo = mid + 1;     // 더 큰 값에서도 만족하는지 탐색
        } else {
            hi = mid - 1;     // 조건 불만족 → 더 작은 값으로
        }
    }

    return answer;
}
```

### 파라메트릭 서치 예제: 나무 자르기

```java
// 적어도 M 미터를 가져가려면 절단기 높이 H의 최댓값은?
// 탐색 대상: 절단기 높이 H (0 ~ 가장 높은 나무)
// check 조건: 높이 H로 잘랐을 때 얻는 나무 총량 >= M
// 높이가 높을수록 → 얻는 나무 적음. 높이가 낮을수록 → 얻는 나무 많음 (단조 감소)
//   → TTTTTFFFF 형태에서 마지막 T (최대 높이) 찾기
public static int solve(int[] trees, int M) {
    int lo = 0, hi = Arrays.stream(trees).max().getAsInt();
    int answer = 0;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;

        // 높이 mid로 잘랐을 때 얻는 나무 총량 계산
        long total = 0;
        for (int tree : trees) {
            if (tree > mid) total += tree - mid;  // mid보다 높은 부분만 가져감
        }

        if (total >= M) {   // M 이상 가져갈 수 있음
            answer = mid;    // 일단 가능한 높이로 저장
            lo = mid + 1;    // 더 높이 잘라도 되는지 탐색 (최댓값이니까)
        } else {
            hi = mid - 1;    // 나무 부족 → 더 낮게 잘라야 함
        }
    }

    return answer;
}
```

---

## 투 포인터

> 정렬된 배열에서 두 포인터를 이동하며 탐색. O(n)

### 두 수의 합

```java
// ── 정렬된 배열에서 합이 target인 두 수 찾기 ──
// left = 가장 작은 값, right = 가장 큰 값에서 시작
// 합이 작으면 left를 키워서 합을 증가, 합이 크면 right를 줄여서 합을 감소
// 정렬되어 있기 때문에 이 방향 조절이 성립 → O(n)
// 예: arr=[1,2,4,7,11], target=9 → left=1(2), right=3(7) → 합=9 ✓
public static int[] twoSum(int[] arr, int target) {
    int left = 0, right = arr.length - 1;

    while (left < right) {
        int sum = arr[left] + arr[right];

        if (sum == target) {
            return new int[]{left, right};
        } else if (sum < target) {
            left++;   // 합이 부족 → 더 큰 값 필요 → left 증가
        } else {
            right--;  // 합이 초과 → 더 작은 값 필요 → right 감소
        }
    }

    return null;
}
```

### 부분합 (연속 구간)

```java
// ── 합이 target 이상인 최소 길이 연속 구간 ──
// left, right = 구간의 양 끝 (right가 확장, left가 축소)
// right를 오른쪽으로 밀면서 합을 늘리고, 합이 target 이상이면 left를 줄여서 최소 길이 탐색
// 왜 O(n)인가: left와 right 모두 최대 n번씩만 이동 → 총 2n번
// 예: arr=[2,3,1,2,4,3], target=7
//     right=4일 때 sum=12 → left를 줄여가며 최소 구간 탐색 → [4,3] 길이 2
public static int minSubarrayLen(int[] arr, int target) {
    int n = arr.length;
    int left = 0, sum = 0;
    int minLen = Integer.MAX_VALUE;

    for (int right = 0; right < n; right++) {
        sum += arr[right];  // 구간 확장: right 원소 추가

        while (sum >= target) {  // 조건 만족하는 동안 구간 축소
            minLen = Math.min(minLen, right - left + 1);
            sum -= arr[left++];  // left 원소 제거하며 축소
        }
    }

    return minLen == Integer.MAX_VALUE ? 0 : minLen;
}
```

### 중복 제거

```java
// ── 정렬된 배열에서 중복 제거 (in-place) ──
// slow = 유니크 원소가 저장될 마지막 위치
// fast = 전체 배열을 순회하는 포인터
// fast가 새로운 값을 발견하면 slow 다음 위치에 복사
// 예: arr=[1,1,2,2,3] → slow: 0→0→1→1→2 → arr=[1,2,3,...] → 유니크 3개
public static int removeDuplicates(int[] arr) {
    if (arr.length == 0) return 0;

    int slow = 0;  // arr[0..slow]는 중복 없는 원소들
    for (int fast = 1; fast < arr.length; fast++) {
        if (arr[fast] != arr[slow]) {  // 새로운 값 발견
            slow++;
            arr[slow] = arr[fast];     // 유니크 영역 뒤에 추가
        }
        // arr[fast] == arr[slow]이면 중복 → 그냥 넘어감
    }
    return slow + 1;  // 유니크한 원소 개수 (인덱스 + 1)
}
```

---

## 슬라이딩 윈도우

> 고정 크기 윈도우를 이동하며 계산. O(n)

```java
// ── 크기 k인 구간의 최대 합 ──
// windowSum = 현재 윈도우(연속 k개 원소)의 합
// 윈도우를 한 칸 오른쪽으로 밀 때: 새 원소 추가 + 맨 왼쪽 원소 제거 → O(1)
// 브루트포스 O(nk) → 슬라이딩 윈도우 O(n)
// 예: arr=[1,3,-1,5,2], k=3
//     [1,3,-1]=3 → [3,-1,5]=7 → [-1,5,2]=6 → maxSum=7
public static int maxSumSubarray(int[] arr, int k) {
    int n = arr.length;
    int windowSum = 0;

    // 첫 윈도우 [0..k-1]의 합 계산
    for (int i = 0; i < k; i++) {
        windowSum += arr[i];
    }

    int maxSum = windowSum;

    // 윈도우를 오른쪽으로 한 칸씩 밀기
    for (int i = k; i < n; i++) {
        windowSum += arr[i] - arr[i - k];  // arr[i] 추가, arr[i-k] 제거
        maxSum = Math.max(maxSum, windowSum);
    }

    return maxSum;
}
```

### 가변 크기 슬라이딩 윈도우

```java
// ── 서로 다른 문자가 최대 k개인 가장 긴 부분 문자열 ──
// count[c] = 현재 윈도우 내 문자 c의 등장 횟수
// distinct = 현재 윈도우 내 서로 다른 문자의 종류 수
// right 확장 → 종류 수 초과 시 left 축소 (조건 회복할 때까지)
// 예: s="aabac", k=2 → "aaba"(a,b 2종류, 길이 4)
public static int longestSubstring(String s, int k) {
    int[] count = new int[128];  // ASCII 문자별 등장 횟수
    int left = 0, distinct = 0, maxLen = 0;

    for (int right = 0; right < s.length(); right++) {
        // 새 문자 추가: 처음 등장하는 문자면 종류 수 증가
        if (count[s.charAt(right)]++ == 0) distinct++;

        // 조건 위반(종류 > k) 시 left를 줄여서 조건 회복
        while (distinct > k) {
            // left 문자 제거: 횟수가 0이 되면 종류 수 감소
            if (--count[s.charAt(left++)] == 0) distinct--;
        }

        maxLen = Math.max(maxLen, right - left + 1);
    }

    return maxLen;
}
```

---

## 분할 정복

> 문제를 작은 문제로 분할, 해결 후 병합

### 병합 정렬

```java
// ── 병합 정렬 O(n log n) ──
// 분할: 배열을 반으로 나눔 → 정복: 각각 정렬 → 병합: 정렬된 두 배열을 합침
// 안정 정렬 (같은 값의 순서 유지), 항상 O(n log n) 보장
// 단점: 추가 메모리 O(n) 필요 (temp 배열)
// 활용: 역순쌍(inversion) 카운팅에 자주 사용 (merge 시 교차 횟수 = 역순쌍 수)
public static void mergeSort(int[] arr, int lo, int hi) {
    if (lo >= hi) return;  // 원소 1개 이하 → 이미 정렬됨

    int mid = lo + (hi - lo) / 2;
    mergeSort(arr, lo, mid);       // 왼쪽 반 정렬
    mergeSort(arr, mid + 1, hi);   // 오른쪽 반 정렬
    merge(arr, lo, mid, hi);       // 정렬된 두 반을 병합
}

// 정렬된 [lo..mid]와 [mid+1..hi]를 하나의 정렬된 배열로 병합
private static void merge(int[] arr, int lo, int mid, int hi) {
    int[] temp = new int[hi - lo + 1];
    int i = lo, j = mid + 1, k = 0;  // i=왼쪽 포인터, j=오른쪽 포인터, k=결과 포인터

    // 두 배열에서 작은 값부터 temp에 넣음
    while (i <= mid && j <= hi) {
        if (arr[i] <= arr[j]) temp[k++] = arr[i++];
        else temp[k++] = arr[j++];
    }

    // 남은 원소 처리
    while (i <= mid) temp[k++] = arr[i++];
    while (j <= hi) temp[k++] = arr[j++];

    // temp → 원본 배열로 복사
    for (k = 0; k < temp.length; k++) {
        arr[lo + k] = temp[k];
    }
}
```

### 거듭제곱 (분할 정복)

```java
// ── a^n mod m (빠른 거듭제곱) ──
// 원리: a^n = (a^(n/2))^2 × (n이 홀수면 a 한 번 더 곱)
// O(log n) — 그냥 n번 곱하면 O(n)
// 예: 2^10 = (2^5)^2, 2^5 = (2^2)^2 × 2, ... → 곱셈 4번만에 계산
public static long power(long a, long n, long m) {
    if (n == 0) return 1;  // a^0 = 1

    long half = power(a, n / 2, m);  // a^(n/2)
    long result = half * half % m;    // (a^(n/2))^2

    if (n % 2 == 1) {                // n이 홀수면
        result = result * a % m;      // a 한 번 더 곱
    }

    return result;
}
```

### 행렬 거듭제곱

```java
// ── 행렬 A를 n번 거듭제곱 (A^n mod m) ──
// 활용: 피보나치 O(log n) 계산, 선형 점화식의 n번째 항 빠른 계산
// 예: fib(n) = [[1,1],[1,0]]^n 의 [0][0] 원소
// 원리: 스칼라 빠른 거듭제곱과 동일, 곱셈만 행렬곱으로 교체
public static long[][] matPow(long[][] A, long n, long mod) {
    int size = A.length;
    long[][] result = new long[size][size];

    // 단위 행렬 (곱셈의 항등원, 숫자의 1과 같은 역할)
    for (int i = 0; i < size; i++) result[i][i] = 1;

    while (n > 0) {
        if (n % 2 == 1) {                       // 비트가 1이면
            result = matMul(result, A, mod);      // 결과에 A 곱
        }
        A = matMul(A, A, mod);                   // A를 제곱
        n /= 2;
    }

    return result;
}

// 행렬 곱셈 C = A × B (mod)
private static long[][] matMul(long[][] A, long[][] B, long mod) {
    int n = A.length;
    long[][] C = new long[n][n];

    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
            for (int k = 0; k < n; k++) {
                C[i][j] = (C[i][j] + A[i][k] * B[k][j]) % mod;
            }
        }
    }

    return C;
}
```

---

## 그리디

> 각 단계에서 최선의 선택. 최적 부분 구조 + 탐욕 선택 속성 필요

### 회의실 배정 (활동 선택)

```java
// ── 회의실 배정 (활동 선택 문제) ──
// 그리디 전략: 끝나는 시간이 빠른 회의를 우선 선택
//   → 일찍 끝나야 다음 회의를 더 넣을 수 있으므로
// 왜 그리디가 최적인가: 일찍 끝나는 회의를 선택하면 남은 시간이 최대
//   → 남은 시간에 가장 많은 회의를 넣을 수 있음 (교환 논증으로 증명 가능)
// 예: [[1,3],[2,4],[3,5]] → 끝나는 순: [1,3],[2,4],[3,5] → [1,3],[3,5] 선택 → 2개
public static int maxMeetings(int[][] meetings) {
    Arrays.sort(meetings, (a, b) -> a[1] - b[1]);  // 끝나는 시간 기준 정렬

    int count = 0;
    int lastEnd = 0;  // 마지막 선택한 회의의 끝나는 시간

    for (int[] meeting : meetings) {
        if (meeting[0] >= lastEnd) {  // 시작 시간 >= 마지막 회의 종료 시간 → 겹치지 않음
            count++;
            lastEnd = meeting[1];
        }
    }

    return count;
}
```

### 동전 거스름돈

```java
// ── 최소 동전 개수 (그리디) ──
// 그리디 전략: 가장 큰 동전부터 최대한 사용
// 주의: 동전이 배수 관계(예: 500,100,50,10)일 때만 그리디가 최적!
//   → 배수 관계가 아닌 경우(예: [1,3,4]로 6 만들기) DP를 써야 함
//   → 그리디: 4+1+1=3개, DP: 3+3=2개 (그리디가 틀림!)
public static int minCoins(int[] coins, int amount) {
    Arrays.sort(coins);
    int count = 0;

    for (int i = coins.length - 1; i >= 0; i--) {  // 큰 동전부터
        if (coins[i] <= amount) {
            count += amount / coins[i];  // 이 동전으로 낼 수 있는 최대 개수
            amount %= coins[i];          // 나머지 금액
        }
    }

    return amount == 0 ? count : -1;  // amount가 0이 안 되면 불가능
}
```

### 분할 가능 배낭

```java
// ── 분할 가능 배낭 (Fractional Knapsack) ──
// 0-1 배낭과 달리 물건을 쪼갤 수 있음 → 그리디 가능
// 그리디 전략: 가치/무게 비율(단가)이 높은 물건부터 넣기
// 왜 그리디가 최적인가: 단가가 높은 물건을 먼저 넣으면 같은 무게에서 최대 가치
public static double fractionalKnapsack(int[][] items, int capacity) {
    // items: {가치, 무게} — 단가(가치/무게) 내림차순 정렬
    Arrays.sort(items, (a, b) ->
        Double.compare((double)b[0]/b[1], (double)a[0]/a[1]));

    double totalValue = 0;

    for (int[] item : items) {
        if (capacity >= item[1]) {
            capacity -= item[1];       // 통째로 넣기
            totalValue += item[0];
        } else {
            totalValue += (double)item[0] * capacity / item[1];  // 쪼개서 넣기
            break;  // 배낭 꽉 참
        }
    }

    return totalValue;
}
```

---

## 백트래킹

> 해를 찾다가 막히면 되돌아감. 가지치기로 탐색 공간 축소

### N-Queens

```java
// ── N-Queens: n×n 체스판에 n개의 퀸을 서로 공격하지 못하게 배치 ──
// col[i] = i행에 놓인 퀸의 열 번호
//   → 행마다 퀸은 반드시 1개 → 1차원 배열로 충분
// 백트래킹: row 0부터 한 행씩 퀸을 놓되, 불가능하면 되돌아감
// 가지치기: 같은 열/대각선에 이미 퀸이 있으면 즉시 스킵
// 4-Queens 해: col=[1,3,0,2] → (0,1),(1,3),(2,0),(3,2)
static int n, count;
static int[] col;

public static int solveNQueens(int size) {
    n = size;
    col = new int[n];
    count = 0;
    backtrack(0);
    return count;
}

private static void backtrack(int row) {
    if (row == n) {    // 모든 행에 퀸을 놓음 → 해 발견
        count++;
        return;
    }

    for (int c = 0; c < n; c++) {      // row행의 모든 열을 시도
        if (isValid(row, c)) {          // 놓을 수 있으면
            col[row] = c;               // 퀸 배치
            backtrack(row + 1);         // 다음 행으로
            // col[row]은 다음 반복에서 덮어쓰므로 별도 복원 불필요
        }
        // 놓을 수 없으면 → 이 열 스킵 (가지치기)
    }
    // 모든 열이 불가능 → 자동으로 이전 행으로 돌아감 (백트래킹)
}

private static boolean isValid(int row, int c) {
    for (int i = 0; i < row; i++) {     // 이전에 놓은 모든 퀸과 비교
        if (col[i] == c) return false;  // 같은 열에 퀸 있음
        if (Math.abs(col[i] - c) == row - i) return false;  // 대각선(기울기 ±1)
    }
    return true;
}
```

### 부분집합 합

```java
// ── 부분집합 합: 배열에서 합이 target인 부분집합 모두 찾기 ──
// path = 현재까지 선택한 원소들
// remain = 아직 더 필요한 값 (target - 선택한 원소 합)
// start = 중복 방지용 시작 인덱스 (이전에 선택한 원소 이후부터 탐색)
//   → start가 없으면 {1,2}와 {2,1}을 다른 것으로 중복 생성
// 예: arr=[2,3,5,7], target=7 → {2,5}, {7}
static List<List<Integer>> results;

public static List<List<Integer>> subsetSum(int[] arr, int target) {
    results = new ArrayList<>();
    backtrack(arr, 0, target, new ArrayList<>());
    return results;
}

private static void backtrack(int[] arr, int start, int remain, List<Integer> path) {
    if (remain == 0) {                          // 합이 딱 맞음 → 해 발견
        results.add(new ArrayList<>(path));      // path 복사 저장 (참조 공유 방지)
        return;
    }
    if (remain < 0) return;  // 합 초과 → 가지치기 (더 넣어봤자 소용없음)

    for (int i = start; i < arr.length; i++) {
        path.add(arr[i]);                                // 선택
        backtrack(arr, i + 1, remain - arr[i], path);    // 다음 원소부터 탐색
        path.remove(path.size() - 1);                    // 선택 취소 (백트래킹)
    }
}
```

---

# 특수 자료구조

## Union-Find (서로소 집합)

> 집합의 합치기(Union)와 찾기(Find). 경로 압축 + 랭크 최적화로 거의 O(1)

```java
// Union-Find: 원소들이 같은 그룹(집합)에 속하는지 빠르게 판별
// parent[i] = 노드 i의 부모 (루트면 자기 자신)
//   → find(x)로 루트를 따라가면 x가 속한 집합의 대표를 알 수 있음
// rank[i] = 노드 i를 루트로 하는 트리의 높이 (합칠 때 균형 유지용)
// 활용: 사이클 탐지(크루스칼), 연결 요소, "같은 그룹인가?" 판별
class UnionFind {
    private int[] parent;
    private int[] rank;

    public UnionFind(int n) {
        parent = new int[n];
        rank = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;  // 초기: 각 노드가 자기 자신이 루트 (1인 집합)
        }
    }

    // ── 루트 찾기 + 경로 압축 ──
    // 경로 압축: find 과정에서 만나는 모든 노드를 직접 루트에 연결
    //   → 트리가 납작해져서 다음 find가 O(1)에 가까워짐
    // 예: 1→2→3→4(루트) → find(1) 후: 1→4, 2→4, 3→4
    public int find(int x) {
        if (parent[x] != x) {
            parent[x] = find(parent[x]);  // 재귀적으로 루트까지 올라가며 압축
        }
        return parent[x];
    }

    // ── 합치기 + 랭크 최적화 ──
    // 랭크가 낮은 트리를 높은 트리 아래에 붙임 → 트리 높이 최소화
    // 반환값: true면 합쳐짐, false면 이미 같은 집합
    public boolean union(int x, int y) {
        int px = find(x), py = find(y);
        if (px == py) return false;  // 이미 같은 집합 → 합칠 필요 없음

        // 랭크가 높은 쪽이 루트가 됨 (트리 균형 유지)
        if (rank[px] < rank[py]) {
            int temp = px; px = py; py = temp;
        }
        parent[py] = px;  // py를 px 아래에 붙임
        if (rank[px] == rank[py]) rank[px]++;  // 같은 높이면 루트의 랭크 증가

        return true;
    }

    // 같은 집합인지 확인 → 루트가 같은지 비교
    public boolean connected(int x, int y) {
        return find(x) == find(y);
    }
}
```

### 활용: 사이클 탐지

```java
// 간선 추가 시 사이클 여부
UnionFind uf = new UnionFind(n);
for (int[] edge : edges) {
    if (!uf.union(edge[0], edge[1])) {
        // 이미 연결됨 = 사이클!
    }
}
```

---

## 세그먼트 트리

> 구간 쿼리 (합, 최소, 최대 등)와 점 업데이트. O(log n)

### 구간 합 세그먼트 트리

```java
// 세그먼트 트리: 배열의 "구간 연산(합, 최소, 최대)"을 O(log n)에 처리
// tree[node] = 해당 노드가 담당하는 구간의 합
// 트리 구조 (배열 [1,3,5,7,9]의 구간 합):
//           [25]           ← tree[1]: 전체 합 [0..4]
//        /       \
//      [9]       [16]      ← tree[2]: [0..2]합, tree[3]: [3..4]합
//     /   \     /    \
//   [4]   [5] [7]   [9]   ← tree[4]: [0..1], tree[5]: [2], ...
//   / \
// [1] [3]                  ← tree[8]: [0], tree[9]: [1]
//
// 왜 4*n 크기? 완전 이진 트리가 아닐 수 있어서 넉넉하게 잡음
// node의 자식: 왼쪽 = 2*node, 오른쪽 = 2*node+1
class SegmentTree {
    private long[] tree;  // tree[i] = i번 노드가 담당하는 구간의 합
    private int n;

    public SegmentTree(int[] arr) {
        n = arr.length;
        tree = new long[4 * n];
        build(arr, 1, 0, n - 1);  // 루트(node=1)부터 전체 구간[0..n-1] 구축
    }

    // 재귀적으로 트리 구축: node가 [start..end] 구간을 담당
    private void build(int[] arr, int node, int start, int end) {
        if (start == end) {
            tree[node] = arr[start];  // 리프 노드: 원소 1개
        } else {
            int mid = (start + end) / 2;
            build(arr, 2 * node, start, mid);         // 왼쪽 자식
            build(arr, 2 * node + 1, mid + 1, end);   // 오른쪽 자식
            tree[node] = tree[2 * node] + tree[2 * node + 1];  // 부모 = 자식 합
        }
    }

    // 점 업데이트: arr[idx] = val → 해당 경로의 모든 구간 합 갱신
    public void update(int idx, long val) {
        update(1, 0, n - 1, idx, val);
    }

    private void update(int node, int start, int end, int idx, long val) {
        if (start == end) {
            tree[node] = val;  // 리프 도달 → 값 변경
        } else {
            int mid = (start + end) / 2;
            if (idx <= mid) {
                update(2 * node, start, mid, idx, val);        // idx가 왼쪽에 있음
            } else {
                update(2 * node + 1, mid + 1, end, idx, val);  // idx가 오른쪽에 있음
            }
            tree[node] = tree[2 * node] + tree[2 * node + 1];  // 부모 갱신
        }
    }

    // 구간 합 쿼리: [l, r] 범위의 합
    public long query(int l, int r) {
        return query(1, 0, n - 1, l, r);
    }

    private long query(int node, int start, int end, int l, int r) {
        if (r < start || end < l) {
            return 0;           // 범위 밖 → 합에 영향 없음 (항등원)
        }
        if (l <= start && end <= r) {
            return tree[node];  // 완전히 포함 → 이 노드 값 그대로 반환
        }
        // 부분적으로 겹침 → 자식에게 위임
        int mid = (start + end) / 2;
        return query(2 * node, start, mid, l, r) +
               query(2 * node + 1, mid + 1, end, l, r);
    }
}
```

### 구간 최소 세그먼트 트리

```java
class MinSegmentTree {
    private int[] tree;
    private int n;
    private static final int INF = Integer.MAX_VALUE;

    public MinSegmentTree(int[] arr) {
        n = arr.length;
        tree = new int[4 * n];
        Arrays.fill(tree, INF);
        build(arr, 1, 0, n - 1);
    }

    private void build(int[] arr, int node, int start, int end) {
        if (start == end) {
            tree[node] = arr[start];
        } else {
            int mid = (start + end) / 2;
            build(arr, 2 * node, start, mid);
            build(arr, 2 * node + 1, mid + 1, end);
            tree[node] = Math.min(tree[2 * node], tree[2 * node + 1]);
        }
    }

    public void update(int idx, int val) {
        update(1, 0, n - 1, idx, val);
    }

    private void update(int node, int start, int end, int idx, int val) {
        if (start == end) {
            tree[node] = val;
        } else {
            int mid = (start + end) / 2;
            if (idx <= mid) update(2 * node, start, mid, idx, val);
            else update(2 * node + 1, mid + 1, end, idx, val);
            tree[node] = Math.min(tree[2 * node], tree[2 * node + 1]);
        }
    }

    public int query(int l, int r) {
        return query(1, 0, n - 1, l, r);
    }

    private int query(int node, int start, int end, int l, int r) {
        if (r < start || end < l) return INF;
        if (l <= start && end <= r) return tree[node];
        int mid = (start + end) / 2;
        return Math.min(
            query(2 * node, start, mid, l, r),
            query(2 * node + 1, mid + 1, end, l, r)
        );
    }
}
```

### Lazy Propagation (구간 업데이트)

```java
// Lazy Propagation: "구간 업데이트"를 O(log n)에 처리
// 기본 세그트리는 점 업데이트만 O(log n), 구간 업데이트는 O(n log n)
// Lazy: "이 구간에 val을 더해야 함"이라는 정보를 자식에 즉시 전파하지 않고 미뤄둠
//   → 나중에 해당 노드를 방문할 때 비로소 전파 (필요한 만큼만 작업)
// lazy[node] = 이 노드의 자식들에게 아직 전파되지 않은 "더해야 할 값"
class LazySegmentTree {
    private long[] tree;  // tree[node] = 구간 합
    private long[] lazy;  // lazy[node] = 자식에게 미전파된 더할 값
    private int n;

    public LazySegmentTree(int[] arr) {
        n = arr.length;
        tree = new long[4 * n];
        lazy = new long[4 * n];  // 초기: 0 (전파할 것 없음)
        build(arr, 1, 0, n - 1);
    }

    private void build(int[] arr, int node, int start, int end) {
        if (start == end) {
            tree[node] = arr[start];
        } else {
            int mid = (start + end) / 2;
            build(arr, 2 * node, start, mid);
            build(arr, 2 * node + 1, mid + 1, end);
            tree[node] = tree[2 * node] + tree[2 * node + 1];
        }
    }

    // 미뤄둔 업데이트를 실제로 적용 + 자식에게 전달
    private void propagate(int node, int start, int end) {
        if (lazy[node] != 0) {
            tree[node] += (end - start + 1) * lazy[node];  // 구간 길이 × 더할 값
            if (start != end) {  // 리프가 아니면 자식에게 전달
                lazy[2 * node] += lazy[node];
                lazy[2 * node + 1] += lazy[node];
            }
            lazy[node] = 0;  // 전파 완료
        }
    }

    // 구간 [l, r]에 val 더하기
    public void updateRange(int l, int r, long val) {
        updateRange(1, 0, n - 1, l, r, val);
    }

    private void updateRange(int node, int start, int end, int l, int r, long val) {
        propagate(node, start, end);
        if (r < start || end < l) return;
        if (l <= start && end <= r) {
            lazy[node] += val;
            propagate(node, start, end);
            return;
        }
        int mid = (start + end) / 2;
        updateRange(2 * node, start, mid, l, r, val);
        updateRange(2 * node + 1, mid + 1, end, l, r, val);
        tree[node] = tree[2 * node] + tree[2 * node + 1];
    }

    public long query(int l, int r) {
        return query(1, 0, n - 1, l, r);
    }

    private long query(int node, int start, int end, int l, int r) {
        propagate(node, start, end);
        if (r < start || end < l) return 0;
        if (l <= start && end <= r) return tree[node];
        int mid = (start + end) / 2;
        return query(2 * node, start, mid, l, r) +
               query(2 * node + 1, mid + 1, end, l, r);
    }
}
```

---

## 펜윅 트리 (BIT)

> Binary Indexed Tree. 구간 합 + 점 업데이트. 세그트리보다 간단. O(log n)

```java
// 펜윅 트리 (BIT): 세그트리와 같은 기능이지만 구현이 훨씬 간단
// tree[i] = "i를 포함하는 특정 구간"의 부분합
// 핵심 연산: idx & (-idx) = idx의 가장 낮은 비트 (LSB)
//   → 이 값이 해당 노드가 담당하는 구간의 길이
// 예: tree[6(=110)] → LSB=2 → arr[5..6]의 합을 담당
//     tree[8(=1000)] → LSB=8 → arr[1..8]의 합을 담당
// 세그트리 대비 장점: 코드가 짧고 상수가 작음
// 세그트리 대비 단점: 구간 업데이트는 추가 구현 필요, 최소/최대 쿼리 어려움
class FenwickTree {
    private long[] tree;
    private int n;

    public FenwickTree(int n) {
        this.n = n;
        this.tree = new long[n + 1];  // 1-indexed (0번은 사용 안 함)
    }

    public FenwickTree(int[] arr) {
        this(arr.length);
        for (int i = 0; i < arr.length; i++) {
            update(i + 1, arr[i]);
        }
    }

    // idx 위치에 delta를 더함 → idx를 포함하는 모든 구간을 갱신
    // idx += idx & (-idx): 자신보다 큰 구간으로 이동
    // 예: update(3) → tree[3], tree[4], tree[8], tree[16]... 갱신
    public void update(int idx, long delta) {
        while (idx <= n) {
            tree[idx] += delta;
            idx += idx & (-idx);  // 다음 상위 구간으로 이동
        }
    }

    // [1..idx] 구간 합 = prefix sum
    // idx -= idx & (-idx): 자신보다 작은 구간으로 이동하며 합산
    // 예: prefixSum(7) → tree[7] + tree[6] + tree[4] (111→110→100)
    public long prefixSum(int idx) {
        long sum = 0;
        while (idx > 0) {
            sum += tree[idx];
            idx -= idx & (-idx);  // 이전 구간으로 이동
        }
        return sum;
    }

    // [l, r] 구간 합 = prefix(r) - prefix(l-1)
    public long rangeSum(int l, int r) {
        return prefixSum(r) - prefixSum(l - 1);
    }
}
```

### 활용: 역순쌍 (Inversion Count)

```java
// ── 역순쌍: arr[i] > arr[j]이면서 i < j인 쌍의 수 ──
// 원리: 오른쪽부터 BIT에 등록하면서, 현재 값보다 작은 값이 몇 개 이미 등록됐는지 카운트
//   → "나보다 오른쪽에 있으면서 나보다 작은 값" = 역순쌍
// 좌표 압축: 값의 범위가 클 수 있으므로, 정렬 후 순위(rank)로 변환
// 예: arr=[3,1,2] → 역순쌍: (3,1), (3,2) = 2개
public static long countInversions(int[] arr) {
    int n = arr.length;
    int[] sorted = arr.clone();
    Arrays.sort(sorted);

    // 좌표 압축: 실제 값 → 1~n 순위로 매핑
    Map<Integer, Integer> rank = new HashMap<>();
    for (int i = 0; i < n; i++) {
        rank.put(sorted[i], i + 1);
    }

    FenwickTree bit = new FenwickTree(n);
    long inversions = 0;

    for (int i = n - 1; i >= 0; i--) {  // 오른쪽부터 처리
        int r = rank.get(arr[i]);
        inversions += bit.prefixSum(r - 1);  // 나보다 작은 값이 이미 몇 개 등록됐는지
        bit.update(r, 1);                     // 현재 값 등록
    }

    return inversions;
}
```

---

## 트라이 (Trie)

> 문자열 검색 특화 트리. 삽입/검색 O(문자열 길이)

```java
// 트라이: 문자열을 한 글자씩 트리에 저장하는 자료구조
// 구조 예시: "app", "apple", "api" 삽입 후
//   root → a → p → p (isEnd=true "app")
//                  └→ l → e (isEnd=true "apple")
//              └→ i (isEnd=true "api")
//
// HashMap으로 문자열 검색하면 O(문자열 길이)이지만, 트라이는 추가로:
//   - 접두사 검색: "ap"로 시작하는 단어 존재 여부 → O(접두사 길이)
//   - 자동완성: "ap" 입력 시 app, apple, api 반환
//   - 사전순 정렬: DFS하면 자동으로 사전순
// children[i] = 'a'+i 문자로의 자식 노드 (null이면 해당 문자 없음)
// isEnd = 이 노드에서 끝나는 단어가 있는지
class Trie {
    private TrieNode root;

    public Trie() {
        root = new TrieNode();
    }

    // 단어를 한 글자씩 트리에 삽입
    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                node.children[idx] = new TrieNode();  // 경로 없으면 생성
            }
            node = node.children[idx];  // 다음 글자로 이동
        }
        node.isEnd = true;  // 단어의 끝 표시
    }

    // 완전 일치 검색: 해당 단어가 존재하는가?
    public boolean search(String word) {
        TrieNode node = findNode(word);
        return node != null && node.isEnd;  // 노드 존재 + 단어 끝이어야 함
    }

    // 접두사 검색: 이 접두사로 시작하는 단어가 있는가?
    public boolean startsWith(String prefix) {
        return findNode(prefix) != null;  // 노드만 존재하면 됨 (isEnd 불필요)
    }

    // 공통 로직: 문자열의 각 글자를 따라 트리를 내려감
    private TrieNode findNode(String str) {
        TrieNode node = root;
        for (char c : str.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                return null;  // 경로가 끊김 → 해당 문자열 없음
            }
            node = node.children[idx];
        }
        return node;
    }

    private static class TrieNode {
        TrieNode[] children = new TrieNode[26];  // a~z 26글자
        boolean isEnd = false;                    // 이 노드에서 끝나는 단어 존재 여부
    }
}
```

### Map 기반 트라이 (유니코드 지원)

```java
class MapTrie {
    private Map<Character, MapTrie> children = new HashMap<>();
    private boolean isEnd = false;

    public void insert(String word) {
        MapTrie node = this;
        for (char c : word.toCharArray()) {
            node.children.putIfAbsent(c, new MapTrie());
            node = node.children.get(c);
        }
        node.isEnd = true;
    }

    public boolean search(String word) {
        MapTrie node = this;
        for (char c : word.toCharArray()) {
            node = node.children.get(c);
            if (node == null) return false;
        }
        return node.isEnd;
    }
}
```

### 자동완성

```java
public List<String> autocomplete(String prefix) {
    TrieNode node = findNode(prefix);
    if (node == null) return Collections.emptyList();

    List<String> results = new ArrayList<>();
    dfs(node, new StringBuilder(prefix), results);
    return results;
}

private void dfs(TrieNode node, StringBuilder sb, List<String> results) {
    if (node.isEnd) {
        results.add(sb.toString());
    }
    for (int i = 0; i < 26; i++) {
        if (node.children[i] != null) {
            sb.append((char)('a' + i));
            dfs(node.children[i], sb, results);
            sb.deleteCharAt(sb.length() - 1);
        }
    }
}
```

---

## LCA (최소 공통 조상)

> 두 노드의 가장 가까운 공통 조상. 희소 배열로 O(log n)

```java
// LCA: 트리에서 두 노드 u, v의 가장 가까운 공통 조상을 O(log n)에 찾음
// 활용: 트리 위 두 노드 사이 거리, 경로 쿼리
//
// parent[k][v] = v의 2^k번째 조상 (희소 배열 / Binary Lifting)
//   parent[0][v] = v의 직접 부모 (2^0 = 1번째)
//   parent[1][v] = v의 2번째 조상 (할아버지)
//   parent[2][v] = v의 4번째 조상
//   → parent[k][v] = parent[k-1][ parent[k-1][v] ]
//     (2^k번째 조상 = "2^(k-1)번째 조상"의 "2^(k-1)번째 조상")
//
// depth[v] = 루트에서 v까지의 깊이
class LCA {
    private int[][] parent;
    private int[] depth;
    private int LOG;  // 최대 점프 크기 = ceil(log2(n))
    private int n;

    public LCA(List<List<Integer>> tree, int root) {
        n = tree.size();
        LOG = (int) Math.ceil(Math.log(n) / Math.log(2)) + 1;
        parent = new int[LOG][n];
        depth = new int[n];

        for (int[] row : parent) Arrays.fill(row, -1);  // -1 = 조상 없음

        bfs(tree, root);  // depth와 parent[0] (직접 부모) 계산

        // 희소 배열 구축: parent[k][v]를 parent[k-1]로부터 계산
        for (int k = 1; k < LOG; k++) {
            for (int v = 0; v < n; v++) {
                if (parent[k-1][v] != -1) {
                    parent[k][v] = parent[k-1][parent[k-1][v]];
                }
            }
        }
    }

    private void bfs(List<List<Integer>> tree, int root) {
        Deque<Integer> queue = new ArrayDeque<>();
        queue.offer(root);
        depth[root] = 0;

        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : tree.get(u)) {
                if (parent[0][v] == -1 && v != root) {
                    parent[0][v] = u;
                    depth[v] = depth[u] + 1;
                    queue.offer(v);
                }
            }
        }
    }

    public int lca(int u, int v) {
        // 1단계: 깊이 맞추기 (깊은 쪽을 위로 올림)
        if (depth[u] < depth[v]) {
            int temp = u; u = v; v = temp;  // u가 항상 더 깊게
        }

        int diff = depth[u] - depth[v];
        // diff를 이진수로 분해하여 2^k씩 점프
        // 예: diff=5(101) → 2^0 + 2^2 = 1칸 + 4칸 점프
        for (int k = 0; k < LOG; k++) {
            if ((diff & (1 << k)) != 0) {
                u = parent[k][u];
            }
        }

        if (u == v) return u;  // v가 u의 조상이었던 경우

        // 2단계: 두 노드를 동시에 올려서 LCA 직전까지 이동
        // 큰 점프부터 시도: 조상이 같으면 너무 많이 올라간 것 → 스킵
        //                   조상이 다르면 아직 LCA 위에 못 간 것 → 점프
        for (int k = LOG - 1; k >= 0; k--) {
            if (parent[k][u] != parent[k][v]) {
                u = parent[k][u];
                v = parent[k][v];
            }
        }

        return parent[0][u];  // 한 칸 위가 LCA
    }

    // 두 노드 사이 거리 = (루트~u) + (루트~v) - 2×(루트~LCA)
    public int distance(int u, int v) {
        return depth[u] + depth[v] - 2 * depth[lca(u, v)];
    }
}
```

---

## 희소 배열 (Sparse Table)

> 정적 배열의 구간 최소/최대 쿼리. 전처리 O(n log n), 쿼리 O(1)

```java
// Sparse Table: 정적 배열(값이 바뀌지 않는)의 구간 최소/최대를 O(1)에 응답
// 전처리 O(n log n), 쿼리 O(1) — 세그트리(쿼리 O(log n))보다 빠름
// 단, 값 업데이트 불가 (정적 배열 전용)
//
// table[j][i] = arr[i..i+2^j-1] 구간(길이 2^j)의 최솟값
// 예: arr=[3,1,4,1,5]
//   table[0] = [3,1,4,1,5]   (길이 1: 자기 자신)
//   table[1] = [1,1,1,1,?]   (길이 2: min(3,1), min(1,4), min(4,1), min(1,5))
//   table[2] = [1,1,1,?,?]   (길이 4: min(3,1,4,1), min(1,4,1,5))
//
// 쿼리 원리: [l, r]을 길이 2^j인 두 구간으로 덮음 (겹쳐도 OK, min이니까)
//   → min(table[j][l], table[j][r-2^j+1])
class SparseTable {
    private int[][] table;  // table[j][i] = arr[i..i+2^j-1]의 최솟값
    private int[] log;      // log[i] = floor(log2(i)) 미리 계산
    private int n;

    public SparseTable(int[] arr) {
        n = arr.length;
        int k = (int) (Math.log(n) / Math.log(2)) + 1;
        table = new int[k][n];
        log = new int[n + 1];

        // log 테이블 전처리 (Math.log 대신 O(1) 조회)
        log[1] = 0;
        for (int i = 2; i <= n; i++) {
            log[i] = log[i / 2] + 1;
        }

        // 길이 1 구간 (2^0 = 1): 자기 자신
        for (int i = 0; i < n; i++) {
            table[0][i] = arr[i];
        }

        // 길이 2^j 구간: 두 개의 2^(j-1) 구간을 합침
        for (int j = 1; j < k; j++) {
            for (int i = 0; i + (1 << j) <= n; i++) {
                table[j][i] = Math.min(
                    table[j-1][i],                    // 왼쪽 절반
                    table[j-1][i + (1 << (j-1))]      // 오른쪽 절반
                );
            }
        }
    }

    // [l, r] 구간 최솟값 — O(1)!
    // j = 구간 길이에 맞는 가장 큰 2의 거듭제곱
    // 두 구간 [l..l+2^j-1]과 [r-2^j+1..r]로 전체를 덮음
    public int query(int l, int r) {
        int j = log[r - l + 1];
        return Math.min(
            table[j][l],                   // 왼쪽에서 2^j 길이
            table[j][r - (1 << j) + 1]     // 오른쪽에서 2^j 길이 (겹쳐도 OK)
        );
    }
}
```

---

# 부록

## 알고리즘 선택 가이드

```
최적화 문제인가?
├── 최단/최소/최대 구하기 ──┐
│                          ▼
│              DP로 풀 수 있나?
│              ├── 중복 부분문제 있음 → DP
│              └── 없음 → 그리디 시도
│
├── 모든 경우 탐색 ──┐
│                    ▼
│         가지치기 가능?
│         ├── Yes → 백트래킹
│         └── No → 완전탐색/브루트포스
│
└── 그래프 문제
    ├── 최단 경로 → 다익스트라/벨만포드/플로이드
    ├── 연결성 → DFS/BFS/Union-Find
    ├── 순서 결정 → 위상정렬
    └── MST → 크루스칼/프림
```

## 문제 유형별 접근법

| 문제 유형 | 핵심 키워드 | 알고리즘/자료구조 |
|----------|------------|-----------------|
| 최단 경로 | "최소 비용", "최단 거리" | 다익스트라, BFS |
| 구간 쿼리 | "구간 합", "구간 최소" | 세그먼트 트리, 펜윅 |
| 문자열 검색 | "접두사", "자동완성" | 트라이 |
| 집합 연산 | "그룹", "연결", "사이클" | Union-Find |
| 순서 결정 | "선후관계", "의존성" | 위상정렬 |
| 최적 부분구조 | "~하는 방법의 수", "최소 비용" | DP |
| 모든 조합/순열 | "모든 경우", "가능한 조합" | 백트래킹 |
| 이분 탐색 | "최솟값의 최댓값", "최댓값의 최솟값" | 파라메트릭 서치 |

## 시간복잡도 빠른 참조

| N 범위 | 허용 시간복잡도 | 예시 알고리즘 |
|--------|----------------|--------------|
| N ≤ 10 | O(N!) | 순열 완전탐색 |
| N ≤ 20 | O(2^N) | 부분집합 |
| N ≤ 500 | O(N³) | 플로이드 워셜 |
| N ≤ 5,000 | O(N²) | 2중 루프 |
| N ≤ 100,000 | O(N log N) | 정렬, 세그트리 |
| N ≤ 10,000,000 | O(N) | 선형 탐색 |
| N > 10,000,000 | O(log N), O(1) | 이분탐색, 수학 |
