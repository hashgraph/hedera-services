package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils;
import com.hedera.services.store.models.Id;
import com.swirlds.fcmap.FCVirtualRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.printDirectorySize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContractLeafStoreTest {
    private static final Random RANDOM = new Random(1234);
    public static final Path STORE_PATH = Path.of("data/contract-storage");

    static {
        System.out.println("STORE_PATH = " + STORE_PATH.toAbsolutePath());
    }

    /**
     * test basic data storage and retrieval for a single version
     */
    @Test
    public void createSomeDataAndReadBack() throws IOException {
        // delete old data
        FCVirtualMapTestUtils.deleteDirectoryAndContents(STORE_PATH);
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


    @Test
    public void randomOpTest() throws IOException {
        final int MAX_INDEX = 100_000;
        // delete old data
        FCVirtualMapTestUtils.deleteDirectoryAndContents(Path.of("data/contract-storage"));
        // create new data
        final int COUNT = 10_000;
        ContractLeafStore contractLeafStore = new ContractLeafStore(new Id(1,2,3));
        // run random ops
        runRandomOps(contractLeafStore, MAX_INDEX);
        // close
        contractLeafStore.release();
        // print directory size
        printDirectorySize(STORE_PATH);
    }

    public void runRandomOps(ContractLeafStore contractLeafStore, int MAX_INDEX) throws IOException {
        var currentStore = contractLeafStore;
        List<ContractLeafStore> stores = new ArrayList<>();
        stores.add(currentStore);
        ContractUint256 key,value;
        for (int i = 0; i < 100_000; i++) {
            long randomLong = RANDOM.nextInt(MAX_INDEX);
            ContractUint256 randomUint256 = new ContractUint256(BigInteger.valueOf(randomLong));
            switch(RANDOM.nextInt(11)) {
                case 0:
                    Long path = contractLeafStore.loadLeafPathByKey(randomUint256);
                    assertTrue(path == null || path == randomLong);
                    break;
                case 1:
                    key = contractLeafStore.loadLeafValueByKey(randomUint256);
                    assertTrue(key == null || key == randomUint256);
                    break;
                case 2:
                case 3:
                    FCVirtualRecord<ContractUint256,ContractUint256> record = contractLeafStore.loadLeafRecordByPath(i);
                    assertTrue(record == null || record.getKey() == randomUint256);
                    assertTrue(record == null ||  record.getValue() == randomUint256);
                    break;
                case 4:
                case 5:
                case 6:
                    ContractUint256 uint256 = new ContractUint256(BigInteger.valueOf(RANDOM.nextInt(MAX_INDEX)));
                    contractLeafStore.saveLeaf(uint256, i, uint256);
                    break;
                case 7:
                    contractLeafStore.deleteLeaf(randomUint256,randomLong);
                    break;
                case 8:
                    contractLeafStore.leafCount();
                    break;
                case 9: // fast copy
                    currentStore = currentStore.copy();
                    stores.add(currentStore);
                    break;
                case 10: // release old copy
                    if (RANDOM.nextDouble() > 0.75 && stores.size() > 1) {
                        ContractLeafStore oldStore = stores.get(RANDOM.nextInt(stores.size() - 1));
                        if (oldStore != currentStore) { // should not be true
                            oldStore.release();
                        }
                    }
                    break;
            }
        }
    }
}
