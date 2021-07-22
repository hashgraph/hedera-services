package offheaphashes;

import com.swirlds.common.crypto.Hash;

import static fcmmap.FCVirtualMapTestUtils.hash;
import static fcmmap.FCVirtualMapTestUtils.toLongsString;

public class OffHeapHashStoreTest {

    public static void main(String[] args) {
        try {
            OffHeapHashStore hashStore = new OffHeapHashStore();
            for (int i = 0; i < 3_000_000; i++) {
                hashStore.saveHash(i, hash(i));
            }

            for (int i = 0; i < 3_000_000; i++) {
                Hash answerHash = hash(i);
                Hash readHash = hashStore.loadHash(i);
                if (readHash == null) {
                    System.err.println("Got null hash for " + i + " should be [" + toLongsString(answerHash) + "]");
                } else if (!answerHash.equals(readHash)) {
                    System.err.println("Hashes don't match for " + i + " got [" + toLongsString(readHash) + "] should be [" + toLongsString(answerHash) + "]");
                }
            }

            // test off end
            Hash bigHash = hash(3_000_123);
            hashStore.saveHash(3_000_123, bigHash);
            if (!bigHash.equals(hashStore.loadHash(3_000_123))) {
                System.err.println("Failed to save and get 3_000_123");
            }

            hashStore.printStats();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
