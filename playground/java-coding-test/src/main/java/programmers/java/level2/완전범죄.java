package programmers.java.level2;

import java.util.Arrays;

/**
 *A도둑과 B도둑이 팀을 이루어 모든 물건을 훔치려고 합니다. 단, 각 도둑이 물건을 훔칠 때 남기는 흔적이 누적되면 경찰에 붙잡히기 때문에, 두 도둑 중 누구도 경찰에 붙잡히지 않도록 흔적을 최소화해야 합니다.
 *
 * 물건을 훔칠 때 조건은 아래와 같습니다.
 *
 * 물건 i를 훔칠 때,
 * A도둑이 훔치면 info[i][0]개의 A에 대한 흔적을 남깁니다.
 * B도둑이 훔치면 info[i][1]개의 B에 대한 흔적을 남깁니다.
 * 각 물건에 대해 A도둑과 B도둑이 남기는 흔적의 개수는 1 이상 3 이하입니다.
 * 경찰에 붙잡히는 조건은 아래와 같습니다.
 *
 * A도둑은 자신이 남긴 흔적의 누적 개수가 n개 이상이면 경찰에 붙잡힙니다.
 * B도둑은 자신이 남긴 흔적의 누적 개수가 m개 이상이면 경찰에 붙잡힙니다.
 * 각 물건을 훔칠 때 생기는 흔적에 대한 정보를 담은 2차원 정수 배열 info, A도둑이 경찰에 붙잡히는 최소 흔적 개수를 나타내는 정수 n, B도둑이 경찰에 붙잡히는 최소 흔적 개수를 나타내는 정수 m이 매개변수로 주어집니다.
 * 두 도둑 모두 경찰에 붙잡히지 않도록 모든 물건을 훔쳤을 때, A도둑이 남긴 흔적의 누적 개수의 최솟값을 return 하도록 solution 함수를 완성해 주세요.
 * 만약 어떠한 방법으로도 두 도둑 모두 경찰에 붙잡히지 않게 할 수 없다면 -1을 return해 주세요.
 *
 */
public class 완전범죄 {

    class Solution {
        public int solution(int[][] info, int n, int m) {
            // A가 가질 수 있는 최대 흔적은 n-1까지입니다.
            int[] dp = new int[n];
            int INF = 1000000; // 충분히 큰 값
            Arrays.fill(dp, INF);
            dp[0] = 0;

            for (int[] item : info) {
                int aTrace = item[0];
                int bTrace = item[1];

                // 뒤에서부터 계산하여 1차원 배열로 이전 상태를 보존합니다 (Knapsack 방식)
                for (int j = n - 1; j >= 0; j--) {
                    // 기존에 B가 가져가는 경우 (현재 상태 유지 + bTrace)
                    int currentB = dp[j] + bTrace;

                    // 이번 물건을 A가 가져가는 경우 (이전 상태 dp[j - aTrace])
                    int nextB = INF;
                    if (j >= aTrace) {
                        nextB = dp[j - aTrace];
                    }

                    // 두 경우 중 B의 흔적을 최소화하는 값을 선택
                    dp[j] = Math.min(currentB, nextB);
                }
            }

            // 결과 탐색: A의 흔적 j가 n 미만이고, B의 최소 흔적 dp[j]가 m 미만인 최소 j 찾기
            int answer = INF;
            for (int j = 0; j < n; j++) {
                if (dp[j] < m) {
                    answer = Math.min(answer, j);
                }
            }

            return (answer == INF) ? -1 : answer;
        }
    }



}
