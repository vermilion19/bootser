package play;

import java.util.*;

public class Play {

    static void main() {

        String[] words = {"abc", "banana", "aaa", "ca", "aa", "b"};

        Map<Integer, List<String>> groups = new HashMap<>();

        for (String word : words) {
            int len = word.length();
            groups.computeIfAbsent(len, k -> new ArrayList<>()).add(word);
        }

        System.out.println("groups = " + groups);
    }
}
