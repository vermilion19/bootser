package programmers.java.level1;


import java.util.Arrays;

public class 택배상자 {
    static class Solution {
        public int solution(int n, int w, int num) {
        int answer = 0;

        int h = (n / w) + 1;
        int[][] boxes = new int[h][w];
        int now = 1;

        int x = 0;
        int y = 0;

        for (int i = 0; i < h; i++) {

            if (i % 2 == 0) {
                for (int j = 0; j < w; j++) {
                    if (now > n) {
                        break;
                    }
                    boxes[i][j] = now;
                    if (now == num) {
                        y = i;
                        x = j;
                    }
                    now++;

                }
            } else {
                for (int j = w - 1; j >= 0; j--) {
                    if (now > n) {
                        break;
                    }
                    boxes[i][j] = now;
                    if (now == num) {
                        y = i;
                        x = j;
                    }
                    now++;
                }
            }
        }

        for (int i = y; i < h; i++) {
            if (boxes[i][x] != 0) {
                answer++;
            }
        }

        return answer;
    }


    }

    static void main() {
        Solution solution = new Solution();
        int solution1 = solution.solution(22, 6, 8);
        System.out.println("solution1 = " + solution1);
    }
}
