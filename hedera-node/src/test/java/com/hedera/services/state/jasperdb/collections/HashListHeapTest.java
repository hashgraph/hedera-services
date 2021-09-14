package com.hedera.services.state.jasperdb.collections;

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.hedera.services.state.jasperdb.JasperDbTestUtils.hash;
import static com.hedera.services.state.jasperdb.JasperDbTestUtils.toLongsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HashListHeapTest {
    public static HashList hashList;

    protected HashList createHashList() {
        return new HashListHeap();
    }

    @Test
    @Order(1)
    public void createDataAndCheck() {
        try {
            hashList = createHashList();
            for (int i = 0; i < 3_000_000; i++) {
                hashList.put(i, hash(i));
            }

            for (int i = 0; i < 3_000_000; i++) {
                Hash answerHash = hash(i);
                Hash readHash = hashList.get(i);
                assertNotNull(readHash,"Got null hash for " + i + " should be [" + toLongsString(answerHash) + "]");
                assertEquals(answerHash,readHash,"Hashes don't match for " + i + " got [" + toLongsString(readHash) + "] should be [" + toLongsString(answerHash) + "]");
            }

            // test off end
            Hash bigHash = hash(3_000_123);
            hashList.put(3_000_123, bigHash);
            assertEquals(bigHash, hashList.get(3_000_123),"Failed to save and get 3_000_123");

            System.out.println("hashStore = " + hashList);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
