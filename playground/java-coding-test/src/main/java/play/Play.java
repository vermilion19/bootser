package play;

import java.util.*;
import java.util.stream.Collectors;

public class Play {

    static void main() {

        int[][] data = new int[5][];

        for (int i = 0; i < data.length; i++) {
            data[i] = new int[]{i, i + 1, i * 10};
        }

        Arrays.stream(data).sorted(
                Comparator.comparingInt(d -> d[1])).toList();

        Arrays.stream(data).sorted(
                Comparator.<int[]>comparingInt(d -> d[1]).thenComparingInt(d -> d[0])).toList();

        List<int[]> collect = Arrays.stream(data).sorted(
                Comparator.<int[]>comparingInt(d -> d[1]).thenComparing(d -> d[0], Comparator.reverseOrder())).collect(Collectors.toList());



    }
}
