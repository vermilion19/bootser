package programmers.java.level1;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PCCE기출문제10번데이터분석 {

    class Solution {
        public int[][] solution(int[][] data, String ext, int val_ext, String sort_by) {
            int extIndex = 0;
            int sortingIndex = 0;

            if(ext.equals("code")) extIndex = 0;
            if(ext.equals("date")) extIndex = 1;
            if(ext.equals("maximum")) extIndex = 2;
            if(ext.equals("remain")) extIndex = 3;

            if(sort_by.equals("code")) sortingIndex = 0;
            if(sort_by.equals("date")) sortingIndex = 1;
            if(sort_by.equals("maximum")) sortingIndex = 2;
            if(sort_by.equals("remain")) sortingIndex = 3;


            List<int[]> list = Arrays.asList(data);
            int finalExtIndex = extIndex;
            int finalSortingIndex = sortingIndex;
//            List<int[]> list1 = list.stream().filter(ints -> ints[finalExtIndex] < val_ext).sorted(Comparator.comparingInt(arr -> arr[finalSortingIndex])).collect(Collectors.toList());
//            int[][] answer = new int[list1.size()][];
//
//            for (int i = 0; i < list1.size(); i++) {
//                answer[i] = list1.get(i);
//            }


            return list.stream()
                    .filter(ints -> ints[finalExtIndex] < val_ext)
                    .sorted(Comparator.comparingInt(arr -> arr[finalSortingIndex]))
                    .toArray(int[][]::new);

        }
    }
}
