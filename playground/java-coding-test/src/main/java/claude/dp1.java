package claude;

import java.util.Arrays;

/**
 가장 긴 증가하는 부분 수열 (LIS, Longest Increasing Subsequence)
 정수 배열이 주어질 때, 순서를 유지하면서 원소가 순증가하는 부분 수열 중 가장 긴 것의 길이를 구하세요.
 입력 예시:
 int[] nums = {10, 9, 2, 5, 3, 7, 101, 18};
 출력: 4 (2, 3, 7, 18 또는 2, 3, 7, 101 등)
 제약:

 1 ≤ nums.length ≤ 100,000

 주의: 배열 길이가 최대 10만입니다. O(n²) DP로도 풀 수는 있지만, 실전 코테에서는 O(n log n) 풀이를 요구할 수 있습니다. 두 가지 접근법 모두 생각해 보세요.
 힌트:

 O(n²): dp[i] = nums[i]로 끝나는 LIS 길이로 정의하고, 매번 이전 원소들을 전부 확인
 O(n log n): DP 대신 "현재까지 만들 수 있는 증가 수열"을 유지하면서, 이진 탐색으로 교체할 위치를 찾는 방식
 */

public class dp1 {

    static void main() {
        int ans = 0;

        int[] nums = {10, 9, 2, 5, 3, 7, 101, 18};
        int max = Integer.MAX_VALUE;

        int[] sortedNums = nums.clone();
        Arrays.sort(sortedNums); // {2,3,5,7,9,10,18,101}

        //4,

        for (int i = 0; i < nums.length; i++) {
            int idx = Arrays.binarySearch(sortedNums, nums[i]);
            if (idx <= max) {
                max = idx;
            }
        }

    }

}
