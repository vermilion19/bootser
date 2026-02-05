package play;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ComparatorExam {
    static void main() {


        int[] arr = {4, 2, 1, 4, 2, 1, 7, 3, 9, 8};
        List<Integer> collect = Arrays.stream(arr).sorted().boxed().collect(Collectors.toList());
        System.out.println("collect = " + collect);

        int[][] data = {{9, 2}, {4, 2}, {1, 2, 4}, {8, 3}, {9, 8}};

        List<int[]> collect1 = Arrays.stream(data).sorted(Comparator.comparingInt(d -> d[0])).collect(Collectors.toList());
        System.out.println("collect1 = " + collect1);
    }
}
