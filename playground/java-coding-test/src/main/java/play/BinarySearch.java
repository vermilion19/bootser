package play;

public class BinarySearch {
    public static int search(int[] nums, int target) {
        int left = 0;
        int right = nums.length - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;

            if (nums[mid] == target) {
                return mid;
            } else if (nums[mid] < target) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        return -1;
    }

    public static void main(String[] args) {
        int[] nums = {2, 3, 5, 7, 9, 10, 18, 101};
        int target = 7;
        int result = search(nums, target);
        System.out.println("Target " + target + " found at index: " + result);
    }

}
