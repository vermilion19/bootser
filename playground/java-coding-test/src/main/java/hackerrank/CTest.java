package hackerrank;


import java.util.*;
import java.util.stream.Collectors;

public class CTest {

    /**
     * Complete the 'maximumSum' function below.
     *
     * The function is expected to return a LONG_INTEGER.
     * The function accepts following parameters:
     *  1. LONG_INTEGER_ARRAY a
     *  2. LONG_INTEGER m
     */
    public static int cookies(int k, List<Integer> A) {
        PriorityQueue<Integer> pq = new PriorityQueue<>(A);  // 생성자로 바로 힙 구성

        int count = 0;
        while (pq.peek() < k) {
            if (pq.size() < 2) {
                return -1;
            }
            int first = pq.poll();
            int second = pq.poll();
            pq.add(first + 2 * second);
            count++;
        }
        return count;
    }

    static void main() {

    }
}
