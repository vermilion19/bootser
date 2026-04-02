package claude.dp;

import java.util.Arrays;

/**
 동전 교환 (Coin Change)
 서로 다른 종류의 동전이 주어집니다. 금액 amount를 만들기 위해 필요한 최소 동전 개수를 구하세요. 만들 수 없으면 -1을 반환하세요.
 각 동전은 무한히 사용할 수 있습니다.

 coins = {1, 5, 7}
 amount = 10

 0 ≤ amount ≤ 10,000
 coins 배열 길이는 최대 12개, 각 동전 값은 1 ≤ coin ≤ 5,000입니다.
 */

public class dp1 {

    static void main() {

        int[] coins = {1, 5, 7};
        int amount = 10;

        int dp[] = new int[amount + 1];
        Arrays.fill(dp, -1);
        dp[0] = 0;

        for (int coin : coins) {
            dp[coin] = 1;
        }



    }

}
