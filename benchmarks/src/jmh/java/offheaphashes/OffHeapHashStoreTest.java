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
            hashStore.printStats();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
