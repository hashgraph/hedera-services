package com.hedera.services.state.jasperdb.collections;

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.Test;

import static com.hedera.services.state.jasperdb.JasperDbTestUtils.hash;
import static com.hedera.services.state.jasperdb.JasperDbTestUtils.toLongsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OffHeapHashListTest {

    @Test
    public void createDataAndCheck() {
        try {
            OffHeapHashList hashStore = new OffHeapHashList();
            for (int i = 0; i < 3_000_000; i++) {
                hashStore.put(i, hash(i));
            }

            for (int i = 0; i < 3_000_000; i++) {
                Hash answerHash = hash(i);
                Hash readHash = hashStore.get(i);
                assertNotNull(readHash,"Got null hash for " + i + " should be [" + toLongsString(answerHash) + "]");
                assertEquals(answerHash,readHash,"Hashes don't match for " + i + " got [" + toLongsString(readHash) + "] should be [" + toLongsString(answerHash) + "]");
            }

            // test off end
            Hash bigHash = hash(3_000_123);
            hashStore.put(3_000_123, bigHash);
            assertEquals(bigHash,hashStore.get(3_000_123),"Failed to save and get 3_000_123");

            System.out.println("hashStore = " + hashStore);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
