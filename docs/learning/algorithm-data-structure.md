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
// 피보나치
int[] dp = new int[n + 1];
dp[0] = 0;
dp[1] = 1;
for (int i = 2; i <= n; i++) {
    dp[i] = dp[i-1] + dp[i-2];
}

// 계단 오르기 (1칸 또는 2칸)
dp[1] = 1;
dp[2] = 2;
for (int i = 3; i <= n; i++) {
    dp[i] = dp[i-1] + dp[i-2];
}

// 최대 부분 수열 합 (카데인 알고리즘)
int maxSum = arr[0];
int curSum = arr[0];
for (int i = 1; i < n; i++) {
    curSum = Math.max(arr[i], curSum + arr[i]);
    maxSum = Math.max(maxSum, curSum);
}
```

### 2차원 DP

```java
// 격자 경로 수 (우/하 이동만 가능)
int[][] dp = new int[n][m];
for (int i = 0; i < n; i++) dp[i][0] = 1;
for (int j = 0; j < m; j++) dp[0][j] = 1;

for (int i = 1; i < n; i++) {
    for (int j = 1; j < m; j++) {
        dp[i][j] = dp[i-1][j] + dp[i][j-1];
    }
}

// 격자 최소 비용 경로
int[][] cost = new int[n][m];  // 입력
int[][] dp = new int[n][m];
dp[0][0] = cost[0][0];

for (int i = 1; i < n; i++) dp[i][0] = dp[i-1][0] + cost[i][0];
for (int j = 1; j < m; j++) dp[0][j] = dp[0][j-1] + cost[0][j];

for (int i = 1; i < n; i++) {
    for (int j = 1; j < m; j++) {
        dp[i][j] = Math.min(dp[i-1][j], dp[i][j-1]) + cost[i][j];
    }
}
```

### LCS (최장 공통 부분수열)

```java
public static int lcs(String s1, String s2) {
    int n = s1.length(), m = s2.length();
    int[][] dp = new int[n + 1][m + 1];

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
// O(n^2) 풀이
public static int lisN2(int[] arr) {
    int n = arr.length;
    int[] dp = new int[n];
    Arrays.fill(dp, 1);

    for (int i = 1; i < n; i++) {
        for (int j = 0; j < i; j++) {
            if (arr[j] < arr[i]) {
                dp[i] = Math.max(dp[i], dp[j] + 1);
            }
        }
    }
    return Arrays.stream(dp).max().getAsInt();
}

// O(n log n) 풀이 (이진 탐색)
public static int lisNlogN(int[] arr) {
    int n = arr.length;
    List<Integer> lis = new ArrayList<>();

    for (int num : arr) {
        int pos = Collections.binarySearch(lis, num);
        if (pos < 0) pos = -(pos + 1);

        if (pos == lis.size()) {
            lis.add(num);
        } else {
            lis.set(pos, num);
        }
    }
    return lis.size();
}
```

### 배낭 문제 (Knapsack)

```java
// 0-1 배낭 (각 물건 1개씩만)
public static int knapsack01(int[] weights, int[] values, int capacity) {
    int n = weights.length;
    int[][] dp = new int[n + 1][capacity + 1];

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

// 1차원 배열로 최적화
public static int knapsack01Optimized(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];

    for (int i = 0; i < weights.length; i++) {
        int w = weights[i], v = values[i];
        for (int c = capacity; c >= w; c--) {  // 역순!
            dp[c] = Math.max(dp[c], dp[c-w] + v);
        }
    }
    return dp[capacity];
}

// 무한 배낭 (물건 무제한)
public static int knapsackUnbounded(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];

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
// 최소 동전 개수
public static int coinChange(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, amount + 1);  // 불가능한 값
    dp[0] = 0;

    for (int i = 1; i <= amount; i++) {
        for (int coin : coins) {
            if (coin <= i) {
                dp[i] = Math.min(dp[i], dp[i - coin] + 1);
            }
        }
    }
    return dp[amount] > amount ? -1 : dp[amount];
}

// 동전 조합 수 (순서 상관 X)
public static int coinCombinations(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    dp[0] = 1;

    for (int coin : coins) {  // 동전을 바깥 루프에!
        for (int i = coin; i <= amount; i++) {
            dp[i] += dp[i - coin];
        }
    }
    return dp[amount];
}
```

### Top-Down DP (메모이제이션)

```java
static int[] memo;

public static int fib(int n) {
    if (n <= 1) return n;
    if (memo[n] != -1) return memo[n];

    memo[n] = fib(n-1) + fib(n-2);
    return memo[n];
}

// 사용 전 초기화
memo = new int[n + 1];
Arrays.fill(memo, -1);
```

---

## 그래프 탐색

### 그래프 표현

```java
// 인접 리스트 (권장)
List<List<Integer>> graph = new ArrayList<>();
for (int i = 0; i < n; i++) {
    graph.add(new ArrayList<>());
}
graph.get(u).add(v);  // u → v 간선

// 가중치 그래프
List<List<int[]>> graph = new ArrayList<>();
graph.get(u).add(new int[]{v, weight});

// 인접 행렬 (밀집 그래프, N이 작을 때)
int[][] adj = new int[n][n];
adj[u][v] = weight;
```

### DFS (깊이 우선 탐색)

```java
static boolean[] visited;
static List<List<Integer>> graph;

// 재귀 DFS
public static void dfs(int node) {
    visited[node] = true;
    System.out.println(node);

    for (int next : graph.get(node)) {
        if (!visited[next]) {
            dfs(next);
        }
    }
}

// 스택 DFS
public static void dfsStack(int start) {
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(start);

    while (!stack.isEmpty()) {
        int node = stack.pop();
        if (visited[node]) continue;
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
// 기본 BFS
public static void bfs(int start) {
    Deque<Integer> queue = new ArrayDeque<>();
    boolean[] visited = new boolean[n];

    queue.offer(start);
    visited[start] = true;

    while (!queue.isEmpty()) {
        int node = queue.poll();

        for (int next : graph.get(node)) {
            if (!visited[next]) {
                visited[next] = true;
                queue.offer(next);
            }
        }
    }
}

// 최단 거리 BFS (가중치 없는 그래프)
public static int[] bfsDistance(int start) {
    int[] dist = new int[n];
    Arrays.fill(dist, -1);
    Deque<Integer> queue = new ArrayDeque<>();

    queue.offer(start);
    dist[start] = 0;

    while (!queue.isEmpty()) {
        int node = queue.poll();

        for (int next : graph.get(node)) {
            if (dist[next] == -1) {
                dist[next] = dist[node] + 1;
                queue.offer(next);
            }
        }
    }
    return dist;
}
```

### 연결 요소 개수

```java
public static int countComponents() {
    boolean[] visited = new boolean[n];
    int count = 0;

    for (int i = 0; i < n; i++) {
        if (!visited[i]) {
            dfs(i);  // 또는 bfs(i)
            count++;
        }
    }
    return count;
}
```

### 이분 그래프 판별

```java
public static boolean isBipartite() {
    int[] color = new int[n];  // 0: 미방문, 1: 색1, 2: 색2

    for (int i = 0; i < n; i++) {
        if (color[i] == 0) {
            if (!bfsCheck(i, color)) return false;
        }
    }
    return true;
}

private static boolean bfsCheck(int start, int[] color) {
    Deque<Integer> queue = new ArrayDeque<>();
    queue.offer(start);
    color[start] = 1;

    while (!queue.isEmpty()) {
        int node = queue.poll();

        for (int next : graph.get(node)) {
            if (color[next] == 0) {
                color[next] = 3 - color[node];  // 1 ↔ 2 전환
                queue.offer(next);
            } else if (color[next] == color[node]) {
                return false;
            }
        }
    }
    return true;
}
```

### 사이클 탐지

```java
// 무향 그래프 사이클 탐지 (Union-Find로도 가능)
static int[] parent;

public static boolean hasCycleUndirected() {
    parent = new int[n];
    for (int i = 0; i < n; i++) parent[i] = i;

    for (int u = 0; u < n; u++) {
        for (int v : graph.get(u)) {
            if (u < v) {  // 간선 중복 처리
                if (find(u) == find(v)) return true;
                union(u, v);
            }
        }
    }
    return false;
}

// 유향 그래프 사이클 탐지 (DFS 색칠)
static int[] state;  // 0: 미방문, 1: 방문중, 2: 완료

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
    state[node] = 1;  // 방문 중

    for (int next : graph.get(node)) {
        if (state[next] == 1) return true;  // 사이클!
        if (state[next] == 0 && dfsCycle(next)) return true;
    }

    state[node] = 2;  // 완료
    return false;
}
```

---

## 최단 경로

### 다익스트라 (Dijkstra)

> **음수 가중치 없는** 단일 시작점 최단 경로. O(E log V)

```java
public static int[] dijkstra(int start, List<List<int[]>> graph) {
    int n = graph.size();
    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[start] = 0;

    // {거리, 노드}
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, start});

    while (!pq.isEmpty()) {
        int[] cur = pq.poll();
        int d = cur[0], u = cur[1];

        if (d > dist[u]) continue;  // 이미 더 짧은 경로 발견됨

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
public static int[] bellmanFord(int start, int n, int[][] edges) {
    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[start] = 0;

    // V-1번 반복
    for (int i = 0; i < n - 1; i++) {
        for (int[] edge : edges) {
            int u = edge[0], v = edge[1], w = edge[2];
            if (dist[u] != Integer.MAX_VALUE && dist[u] + w < dist[v]) {
                dist[v] = dist[u] + w;
            }
        }
    }

    // 음수 사이클 체크 (한 번 더 반복)
    for (int[] edge : edges) {
        int u = edge[0], v = edge[1], w = edge[2];
        if (dist[u] != Integer.MAX_VALUE && dist[u] + w < dist[v]) {
            return null;  // 음수 사이클 존재
        }
    }

    return dist;
}
```

### 플로이드-워셜 (Floyd-Warshall)

> **모든 쌍 최단 경로**. O(V³)

```java
public static int[][] floydWarshall(int[][] graph) {
    int n = graph.length;
    int[][] dist = new int[n][n];
    int INF = Integer.MAX_VALUE / 2;  // 오버플로우 방지

    // 초기화
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
            if (i == j) dist[i][j] = 0;
            else if (graph[i][j] != 0) dist[i][j] = graph[i][j];
            else dist[i][j] = INF;
        }
    }

    // k를 거쳐가는 경로
    for (int k = 0; k < n; k++) {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
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
static int[] parent, rank_;

public static int kruskal(int n, int[][] edges) {
    // edges: {u, v, weight}
    Arrays.sort(edges, (a, b) -> a[2] - b[2]);

    parent = new int[n];
    rank_ = new int[n];
    for (int i = 0; i < n; i++) parent[i] = i;

    int mstWeight = 0;
    int edgeCount = 0;

    for (int[] edge : edges) {
        int u = edge[0], v = edge[1], w = edge[2];

        if (find(u) != find(v)) {
            union(u, v);
            mstWeight += w;
            edgeCount++;

            if (edgeCount == n - 1) break;
        }
    }

    return edgeCount == n - 1 ? mstWeight : -1;  // 연결 안 되면 -1
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
public static int prim(List<List<int[]>> graph) {
    int n = graph.size();
    boolean[] visited = new boolean[n];

    // {가중치, 노드}
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, 0});  // 시작 노드 0

    int mstWeight = 0;
    int edgeCount = 0;

    while (!pq.isEmpty() && edgeCount < n) {
        int[] cur = pq.poll();
        int w = cur[0], u = cur[1];

        if (visited[u]) continue;
        visited[u] = true;
        mstWeight += w;
        edgeCount++;

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
public static List<Integer> topologicalSort(List<List<Integer>> graph) {
    int n = graph.size();
    int[] indegree = new int[n];

    // 진입 차수 계산
    for (int u = 0; u < n; u++) {
        for (int v : graph.get(u)) {
            indegree[v]++;
        }
    }

    // 진입 차수 0인 노드 큐에 추가
    Deque<Integer> queue = new ArrayDeque<>();
    for (int i = 0; i < n; i++) {
        if (indegree[i] == 0) queue.offer(i);
    }

    List<Integer> result = new ArrayList<>();

    while (!queue.isEmpty()) {
        int u = queue.poll();
        result.add(u);

        for (int v : graph.get(u)) {
            indegree[v]--;
            if (indegree[v] == 0) {
                queue.offer(v);
            }
        }
    }

    // 사이클 존재 체크
    if (result.size() != n) {
        return null;  // 사이클 있음
    }

    return result;
}
```

### DFS 기반 위상 정렬

```java
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
        result.add(stack.pop());
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
    stack.push(node);  // 후위 순서로 스택에 추가
}
```

---

## 이진 탐색

### 기본 이진 탐색

```java
// 정확히 target 찾기
public static int binarySearch(int[] arr, int target) {
    int lo = 0, hi = arr.length - 1;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;  // 오버플로우 방지

        if (arr[mid] == target) return mid;
        else if (arr[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }

    return -1;  // 못 찾음
}
```

### Lower Bound / Upper Bound

```java
// Lower Bound: target 이상인 첫 번째 위치
public static int lowerBound(int[] arr, int target) {
    int lo = 0, hi = arr.length;

    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] >= target) hi = mid;
        else lo = mid + 1;
    }

    return lo;
}

// Upper Bound: target 초과인 첫 번째 위치
public static int upperBound(int[] arr, int target) {
    int lo = 0, hi = arr.length;

    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] > target) hi = mid;
        else lo = mid + 1;
    }

    return lo;
}

// target 개수 = upperBound - lowerBound
```

### 파라메트릭 서치

> "조건을 만족하는 최솟값/최댓값 찾기" → 이진 탐색으로 변환

```java
// 조건을 만족하는 최솟값 찾기
public static int parametricMin(int lo, int hi) {
    int answer = -1;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;

        if (check(mid)) {  // 조건 만족?
            answer = mid;
            hi = mid - 1;  // 더 작은 값 탐색
        } else {
            lo = mid + 1;
        }
    }

    return answer;
}

// 조건을 만족하는 최댓값 찾기
public static int parametricMax(int lo, int hi) {
    int answer = -1;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;

        if (check(mid)) {  // 조건 만족?
            answer = mid;
            lo = mid + 1;  // 더 큰 값 탐색
        } else {
            hi = mid - 1;
        }
    }

    return answer;
}
```

### 파라메트릭 서치 예제: 나무 자르기

```java
// 적어도 M 미터를 가져가려면 절단기 높이 H의 최댓값은?
public static int solve(int[] trees, int M) {
    int lo = 0, hi = Arrays.stream(trees).max().getAsInt();
    int answer = 0;

    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;

        long total = 0;
        for (int tree : trees) {
            if (tree > mid) total += tree - mid;
        }

        if (total >= M) {
            answer = mid;
            lo = mid + 1;
        } else {
            hi = mid - 1;
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
// 정렬된 배열에서 합이 target인 두 수 찾기
public static int[] twoSum(int[] arr, int target) {
    int left = 0, right = arr.length - 1;

    while (left < right) {
        int sum = arr[left] + arr[right];

        if (sum == target) {
            return new int[]{left, right};
        } else if (sum < target) {
            left++;
        } else {
            right--;
        }
    }

    return null;
}
```

### 부분합 (연속 구간)

```java
// 합이 target 이상인 최소 길이 구간
public static int minSubarrayLen(int[] arr, int target) {
    int n = arr.length;
    int left = 0, sum = 0;
    int minLen = Integer.MAX_VALUE;

    for (int right = 0; right < n; right++) {
        sum += arr[right];

        while (sum >= target) {
            minLen = Math.min(minLen, right - left + 1);
            sum -= arr[left++];
        }
    }

    return minLen == Integer.MAX_VALUE ? 0 : minLen;
}
```

### 중복 제거

```java
// 정렬된 배열에서 중복 제거
public static int removeDuplicates(int[] arr) {
    if (arr.length == 0) return 0;

    int slow = 0;
    for (int fast = 1; fast < arr.length; fast++) {
        if (arr[fast] != arr[slow]) {
            slow++;
            arr[slow] = arr[fast];
        }
    }
    return slow + 1;  // 유니크한 원소 개수
}
```

---

## 슬라이딩 윈도우

> 고정 크기 윈도우를 이동하며 계산. O(n)

```java
// 크기 k인 구간의 최대 합
public static int maxSumSubarray(int[] arr, int k) {
    int n = arr.length;
    int windowSum = 0;

    // 첫 윈도우
    for (int i = 0; i < k; i++) {
        windowSum += arr[i];
    }

    int maxSum = windowSum;

    // 윈도우 슬라이딩
    for (int i = k; i < n; i++) {
        windowSum += arr[i] - arr[i - k];  // 새 원소 추가, 오래된 원소 제거
        maxSum = Math.max(maxSum, windowSum);
    }

    return maxSum;
}
```

### 가변 크기 슬라이딩 윈도우

```java
// 서로 다른 문자가 최대 k개인 가장 긴 부분 문자열
public static int longestSubstring(String s, int k) {
    int[] count = new int[128];
    int left = 0, distinct = 0, maxLen = 0;

    for (int right = 0; right < s.length(); right++) {
        if (count[s.charAt(right)]++ == 0) distinct++;

        while (distinct > k) {
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
public static void mergeSort(int[] arr, int lo, int hi) {
    if (lo >= hi) return;

    int mid = lo + (hi - lo) / 2;
    mergeSort(arr, lo, mid);
    mergeSort(arr, mid + 1, hi);
    merge(arr, lo, mid, hi);
}

private static void merge(int[] arr, int lo, int mid, int hi) {
    int[] temp = new int[hi - lo + 1];
    int i = lo, j = mid + 1, k = 0;

    while (i <= mid && j <= hi) {
        if (arr[i] <= arr[j]) temp[k++] = arr[i++];
        else temp[k++] = arr[j++];
    }

    while (i <= mid) temp[k++] = arr[i++];
    while (j <= hi) temp[k++] = arr[j++];

    for (k = 0; k < temp.length; k++) {
        arr[lo + k] = temp[k];
    }
}
```

### 거듭제곱 (분할 정복)

```java
// a^n mod m
public static long power(long a, long n, long m) {
    if (n == 0) return 1;

    long half = power(a, n / 2, m);
    long result = half * half % m;

    if (n % 2 == 1) {
        result = result * a % m;
    }

    return result;
}
```

### 행렬 거듭제곱

```java
public static long[][] matPow(long[][] A, long n, long mod) {
    int size = A.length;
    long[][] result = new long[size][size];

    // 단위 행렬
    for (int i = 0; i < size; i++) result[i][i] = 1;

    while (n > 0) {
        if (n % 2 == 1) {
            result = matMul(result, A, mod);
        }
        A = matMul(A, A, mod);
        n /= 2;
    }

    return result;
}

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
// 끝나는 시간 기준 정렬, 최대 회의 수
public static int maxMeetings(int[][] meetings) {
    Arrays.sort(meetings, (a, b) -> a[1] - b[1]);  // 끝나는 시간 기준

    int count = 0;
    int lastEnd = 0;

    for (int[] meeting : meetings) {
        if (meeting[0] >= lastEnd) {
            count++;
            lastEnd = meeting[1];
        }
    }

    return count;
}
```

### 동전 거스름돈

```java
// 최소 동전 개수 (그리디 가능: 동전이 배수 관계일 때)
public static int minCoins(int[] coins, int amount) {
    Arrays.sort(coins);
    int count = 0;

    for (int i = coins.length - 1; i >= 0; i--) {
        if (coins[i] <= amount) {
            count += amount / coins[i];
            amount %= coins[i];
        }
    }

    return amount == 0 ? count : -1;
}
```

### 분할 가능 배낭

```java
// 가치/무게 비율로 정렬
public static double fractionalKnapsack(int[][] items, int capacity) {
    // items: {가치, 무게}
    Arrays.sort(items, (a, b) ->
        Double.compare((double)b[0]/b[1], (double)a[0]/a[1]));

    double totalValue = 0;

    for (int[] item : items) {
        if (capacity >= item[1]) {
            capacity -= item[1];
            totalValue += item[0];
        } else {
            totalValue += (double)item[0] * capacity / item[1];
            break;
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
static int n, count;
static int[] col;  // col[i] = i행에 놓인 퀸의 열

public static int solveNQueens(int size) {
    n = size;
    col = new int[n];
    count = 0;
    backtrack(0);
    return count;
}

private static void backtrack(int row) {
    if (row == n) {
        count++;
        return;
    }

    for (int c = 0; c < n; c++) {
        if (isValid(row, c)) {
            col[row] = c;
            backtrack(row + 1);
        }
    }
}

private static boolean isValid(int row, int c) {
    for (int i = 0; i < row; i++) {
        if (col[i] == c) return false;  // 같은 열
        if (Math.abs(col[i] - c) == row - i) return false;  // 대각선
    }
    return true;
}
```

### 부분집합 합

```java
static List<List<Integer>> results;

public static List<List<Integer>> subsetSum(int[] arr, int target) {
    results = new ArrayList<>();
    backtrack(arr, 0, target, new ArrayList<>());
    return results;
}

private static void backtrack(int[] arr, int start, int remain, List<Integer> path) {
    if (remain == 0) {
        results.add(new ArrayList<>(path));
        return;
    }
    if (remain < 0) return;  // 가지치기

    for (int i = start; i < arr.length; i++) {
        path.add(arr[i]);
        backtrack(arr, i + 1, remain - arr[i], path);
        path.remove(path.size() - 1);  // 백트래킹
    }
}
```

---

# 특수 자료구조

## Union-Find (서로소 집합)

> 집합의 합치기(Union)와 찾기(Find). 경로 압축 + 랭크 최적화로 거의 O(1)

```java
class UnionFind {
    private int[] parent;
    private int[] rank;

    public UnionFind(int n) {
        parent = new int[n];
        rank = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
    }

    // 루트 찾기 + 경로 압축
    public int find(int x) {
        if (parent[x] != x) {
            parent[x] = find(parent[x]);
        }
        return parent[x];
    }

    // 합치기 + 랭크 최적화
    public boolean union(int x, int y) {
        int px = find(x), py = find(y);
        if (px == py) return false;  // 이미 같은 집합

        if (rank[px] < rank[py]) {
            int temp = px; px = py; py = temp;
        }
        parent[py] = px;
        if (rank[px] == rank[py]) rank[px]++;

        return true;
    }

    // 같은 집합인지 확인
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
class SegmentTree {
    private long[] tree;
    private int n;

    public SegmentTree(int[] arr) {
        n = arr.length;
        tree = new long[4 * n];
        build(arr, 1, 0, n - 1);
    }

    // 트리 구축
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

    // 점 업데이트: arr[idx] = val
    public void update(int idx, long val) {
        update(1, 0, n - 1, idx, val);
    }

    private void update(int node, int start, int end, int idx, long val) {
        if (start == end) {
            tree[node] = val;
        } else {
            int mid = (start + end) / 2;
            if (idx <= mid) {
                update(2 * node, start, mid, idx, val);
            } else {
                update(2 * node + 1, mid + 1, end, idx, val);
            }
            tree[node] = tree[2 * node] + tree[2 * node + 1];
        }
    }

    // 구간 합 쿼리: [l, r]
    public long query(int l, int r) {
        return query(1, 0, n - 1, l, r);
    }

    private long query(int node, int start, int end, int l, int r) {
        if (r < start || end < l) {
            return 0;  // 범위 밖
        }
        if (l <= start && end <= r) {
            return tree[node];  // 완전히 포함
        }
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
class LazySegmentTree {
    private long[] tree, lazy;
    private int n;

    public LazySegmentTree(int[] arr) {
        n = arr.length;
        tree = new long[4 * n];
        lazy = new long[4 * n];
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

    private void propagate(int node, int start, int end) {
        if (lazy[node] != 0) {
            tree[node] += (end - start + 1) * lazy[node];
            if (start != end) {
                lazy[2 * node] += lazy[node];
                lazy[2 * node + 1] += lazy[node];
            }
            lazy[node] = 0;
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
class FenwickTree {
    private long[] tree;
    private int n;

    public FenwickTree(int n) {
        this.n = n;
        this.tree = new long[n + 1];  // 1-indexed
    }

    // arr[i]로 초기화
    public FenwickTree(int[] arr) {
        this(arr.length);
        for (int i = 0; i < arr.length; i++) {
            update(i + 1, arr[i]);
        }
    }

    // idx 위치에 delta 더하기 (1-indexed)
    public void update(int idx, long delta) {
        while (idx <= n) {
            tree[idx] += delta;
            idx += idx & (-idx);  // 다음 구간
        }
    }

    // [1, idx] 구간 합 (1-indexed)
    public long prefixSum(int idx) {
        long sum = 0;
        while (idx > 0) {
            sum += tree[idx];
            idx -= idx & (-idx);  // 이전 구간
        }
        return sum;
    }

    // [l, r] 구간 합 (1-indexed)
    public long rangeSum(int l, int r) {
        return prefixSum(r) - prefixSum(l - 1);
    }
}
```

### 활용: 역순쌍 (Inversion Count)

```java
public static long countInversions(int[] arr) {
    int n = arr.length;
    int[] sorted = arr.clone();
    Arrays.sort(sorted);

    // 좌표 압축
    Map<Integer, Integer> rank = new HashMap<>();
    for (int i = 0; i < n; i++) {
        rank.put(sorted[i], i + 1);
    }

    FenwickTree bit = new FenwickTree(n);
    long inversions = 0;

    for (int i = n - 1; i >= 0; i--) {
        int r = rank.get(arr[i]);
        inversions += bit.prefixSum(r - 1);
        bit.update(r, 1);
    }

    return inversions;
}
```

---

## 트라이 (Trie)

> 문자열 검색 특화 트리. 삽입/검색 O(문자열 길이)

```java
class Trie {
    private TrieNode root;

    public Trie() {
        root = new TrieNode();
    }

    // 삽입
    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                node.children[idx] = new TrieNode();
            }
            node = node.children[idx];
        }
        node.isEnd = true;
    }

    // 완전 일치 검색
    public boolean search(String word) {
        TrieNode node = findNode(word);
        return node != null && node.isEnd;
    }

    // 접두사 검색
    public boolean startsWith(String prefix) {
        return findNode(prefix) != null;
    }

    private TrieNode findNode(String str) {
        TrieNode node = root;
        for (char c : str.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                return null;
            }
            node = node.children[idx];
        }
        return node;
    }

    private static class TrieNode {
        TrieNode[] children = new TrieNode[26];
        boolean isEnd = false;
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
class LCA {
    private int[][] parent;  // parent[k][v] = v의 2^k번째 조상
    private int[] depth;
    private int LOG;
    private int n;

    public LCA(List<List<Integer>> tree, int root) {
        n = tree.size();
        LOG = (int) Math.ceil(Math.log(n) / Math.log(2)) + 1;
        parent = new int[LOG][n];
        depth = new int[n];

        for (int[] row : parent) Arrays.fill(row, -1);

        // BFS로 깊이와 직계 부모 계산
        bfs(tree, root);

        // 희소 배열 구축
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
        // 깊이 맞추기
        if (depth[u] < depth[v]) {
            int temp = u; u = v; v = temp;
        }

        int diff = depth[u] - depth[v];
        for (int k = 0; k < LOG; k++) {
            if ((diff & (1 << k)) != 0) {
                u = parent[k][u];
            }
        }

        if (u == v) return u;

        // 동시에 올라가기
        for (int k = LOG - 1; k >= 0; k--) {
            if (parent[k][u] != parent[k][v]) {
                u = parent[k][u];
                v = parent[k][v];
            }
        }

        return parent[0][u];
    }

    // 두 노드 사이 거리
    public int distance(int u, int v) {
        return depth[u] + depth[v] - 2 * depth[lca(u, v)];
    }
}
```

---

## 희소 배열 (Sparse Table)

> 정적 배열의 구간 최소/최대 쿼리. 전처리 O(n log n), 쿼리 O(1)

```java
class SparseTable {
    private int[][] table;
    private int[] log;
    private int n;

    public SparseTable(int[] arr) {
        n = arr.length;
        int k = (int) (Math.log(n) / Math.log(2)) + 1;
        table = new int[k][n];
        log = new int[n + 1];

        // log 값 미리 계산
        log[1] = 0;
        for (int i = 2; i <= n; i++) {
            log[i] = log[i / 2] + 1;
        }

        // 구간 길이 1
        for (int i = 0; i < n; i++) {
            table[0][i] = arr[i];
        }

        // 구간 길이 2^j
        for (int j = 1; j < k; j++) {
            for (int i = 0; i + (1 << j) <= n; i++) {
                table[j][i] = Math.min(
                    table[j-1][i],
                    table[j-1][i + (1 << (j-1))]
                );
            }
        }
    }

    // [l, r] 구간 최솟값 (0-indexed)
    public int query(int l, int r) {
        int j = log[r - l + 1];
        return Math.min(
            table[j][l],
            table[j][r - (1 << j) + 1]
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
