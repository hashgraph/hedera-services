package offheap;

public class OffHeapLongListTest {

    public static void main(String[] args) {
        try {
            OffHeapLongList longlist = new OffHeapLongList();
            for (int i = 0; i < 10_000_000; i++) {
                longlist.put(i, i);
            }

            for (int i = 0; i < 3_000_000; i++) {
                long readValue = longlist.get(i);
                if (readValue != i) {
                    System.err.println("Longs don't match for " + i + " got [" + readValue + "] should be [" + i + "]");
                }
            }

            // test off end
            longlist.put(13_000_123, 13_000_123);
            if (longlist.get(13_000_123) != 13_000_123) {
                System.err.println("Failed to save and get 13_000_123");
            }

            System.out.println("All done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
