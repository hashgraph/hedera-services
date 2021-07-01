package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils;
import com.hedera.services.store.models.Id;
import com.swirlds.fcmap.FCVirtualRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContractLeafStoreTest {
    private static final Random RANDOM = new Random(1234);

    /**
     * test basic data storage and retrieval for a single version
     */
    @Test
    public void createSomeDataAndReadBack() throws IOException {
        // delete old data
        FCVirtualMapTestUtils.deleteDirectoryAndContents(Path.of("data/contract-storage"));
        // create new data
        final int COUNT = 10_000;
        ContractLeafStore contractLeafStore = new ContractLeafStore(new Id(1,2,3));

        // create some data for a number of accounts
        for (int i = 0; i < COUNT; i++) {
            ContractUint256 uint256 = new ContractUint256(BigInteger.valueOf(i));
            contractLeafStore.saveLeaf(uint256, i, uint256);
        }
        // check leaf count
        assertEquals(COUNT,contractLeafStore.leafCount());
        // read back and check that data
        for (int i = 0; i < COUNT; i++) {
            System.out.println("i = " + i);
            ContractUint256 uint256 = new ContractUint256(BigInteger.valueOf(i));
            long path = contractLeafStore.loadLeafPathByKey(uint256);
            assertEquals(i, path);
            ContractUint256 key = contractLeafStore.loadLeafValueByKey(uint256);
            assertEquals(uint256, key);
            FCVirtualRecord<ContractUint256,ContractUint256> record = contractLeafStore.loadLeafRecordByPath(i);
            assertEquals(uint256, record.getKey());
            assertEquals(uint256, record.getValue());
        }

        // read back random and check that data
        for (int j = 0; j < COUNT; j++) {
            int i = RANDOM.nextInt(COUNT);
            ContractUint256 uint256 = new ContractUint256(BigInteger.valueOf(i));
            long path = contractLeafStore.loadLeafPathByKey(uint256);
            assertEquals(i, path);
            ContractUint256 key = contractLeafStore.loadLeafValueByKey(uint256);
            assertEquals(uint256, key);
            FCVirtualRecord<ContractUint256,ContractUint256> record = contractLeafStore.loadLeafRecordByPath(i);
            assertEquals(uint256, record.getKey());
            assertEquals(uint256, record.getValue());
        }
        // close
        contractLeafStore.release();
    }
}
