package programmers.java.level3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

//todo: 아직 정답아님
/**
 * 당신은 숫자 야구를 플레이하는 프로그램을 작성해야 합니다.
 *
 * 숫자 야구란 1 ~ 9 사이의 서로 다른 숫자 4개로 이루어진 비밀번호를 맞히는 게임입니다.
 *
 * 당신은 1000 이상 9999 이하의 정수를 제출할 수 있는 기회가 총 n번 있으며, 수를 제출할 때마다 비밀번호에 관한 단서가 주어집니다. 이때 제출한 수의 각 자릿수에 대해 아래와 같이 판정합니다.
 *
 * 숫자가 비밀번호에 포함되어 있지 않다면 : OUT
 * 숫자가 비밀번호에 포함되어 있지만, 위치가 틀렸다면 : BALL
 * 숫자가 비밀번호에 포함되어 있고, 위치까지 정확하다면 : STRIKE
 * 위와 같이 STRIKE, BALL으로 판정한 숫자의 개수를 세어, STRIKE가 x개 / BALL이 y개라면 "xS yB" 형식으로 단서가 주어집니다.
 *
 * 아래 표는 비밀번호가 1357일 때 제출한 수에 따른 단서의 예시입니다.
 */
public class 숫자야구 {

    class Solution {
        public int solution(int n, Function<Integer, String> submit) {
            // 1. 비밀번호 후보군 생성 (1~9 사이의 서로 다른 4자리 숫자)
            List<int[]> candidates = generateCandidates();

            for (int i = 0; i < n; i++) {
                if (candidates.isEmpty()) break;

                // 2. 현재 후보군 중 하나를 선택하여 제출
                int[] guessArr = candidates.get(0);
                int guessNum = arrayToNum(guessArr);

                String result = submit.apply(guessNum);
                if (result == null) continue;
                result = result.trim();

                // 4S 0B면 정답 반환
                if (result.equals("4S 0B")) {
                    return guessNum;
                }

                // 3. 받은 단서와 일치하지 않는 후보들 제거
                List<int[]> nextCandidates = new ArrayList<>();
                for (int[] cand : candidates) {
                    if (isCompatible(cand, guessArr, result)) {
                        nextCandidates.add(cand);
                    }
                }
                candidates = nextCandidates;
            }

            return 0;
        }

        // 후보군 생성 로직 (비밀번호 조건: 1~9, 서로 다른 숫자)
        private List<int[]> generateCandidates() {
            List<int[]> list = new ArrayList<>(3024);
            for (int a = 1; a <= 9; a++) {
                for (int b = 1; b <= 9; b++) {
                    if (a == b) continue;
                    for (int c = 1; c <= 9; c++) {
                        if (a == c || b == c) continue;
                        for (int d = 1; d <= 9; d++) {
                            if (a == d || b == d || c == d) continue;
                            list.add(new int[]{a, b, c, d});
                        }
                    }
                }
            }
            return list;
        }

        // 문제의 특수 규칙을 적용한 힌트 계산 로직
        private String getHint(int[] secret, int[] guess) {
            int strikes = 0;
            int balls = 0;

            // 제출한 숫자의 각 자릿수(i)를 순회하며 비밀번호와 대조
            for (int i = 0; i < 4; i++) {
                int gDigit = guess[i];

                // 비밀번호에 해당 숫자가 있는지 확인
                for (int j = 0; j < 4; j++) {
                    if (secret[j] == gDigit) {
                        if (i == j) strikes++;
                        else balls++;
                        // 비밀번호는 서로 다른 숫자이므로, 하나 찾으면 해당 자릿수 판정 종료
                        break;
                    }
                }
            }
            return strikes + "S " + balls + "B";
        }

        private boolean isCompatible(int[] cand, int[] guess, String result) {
            String hint = getHint(cand, guess);
            if (hint.equals(result)) return true;

            // 만약 0S 0B 대신 "OUT"이 올 경우를 대비
            if (result.equals("OUT") && hint.equals("0S 0B")) return true;

            return false;
        }

        private int arrayToNum(int[] arr) {
            return arr[0] * 1000 + arr[1] * 100 + arr[2] * 10 + arr[3];
        }
    }

}
