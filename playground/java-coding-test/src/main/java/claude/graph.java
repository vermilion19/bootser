package claude;

/**
 * 섬의 개수 (Number of Islands)
 * n x m 크기의 격자판이 주어집니다. 1은 땅, 0은 물입니다. 상하좌우로 연결된 1들의 묶음을 하나의 섬이라 할 때, 섬의 개수를 구하세요.
 * 입력 예시:
 * int[][] grid = {
 *     {1, 1, 0, 0, 0},
 *     {1, 1, 0, 0, 1},
 *     {0, 0, 0, 1, 1},
 *     {0, 0, 0, 0, 0}
 * };
 */

public class graph {

    static void main() {

        int island = 0;

        int[][] grid = {
                {1, 1, 0, 0, 0},
                {1, 1, 0, 0, 1},
                {0, 0, 0, 1, 1},
                {0, 0, 0, 0, 0}
        };

        boolean[][] visited = new boolean[grid.length][grid[0].length];

        int[] dy = {0, 0, 1, -1};
        int[] dx = {1, -1, 0, 0};



    }

    static boolean isInside(int x, int y, int[][] arr) {
        return x >= 0 && y >= 0 && x < arr.length && y < arr[0].length;
    }
}
