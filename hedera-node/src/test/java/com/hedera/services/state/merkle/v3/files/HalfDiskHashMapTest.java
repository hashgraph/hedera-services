package com.hedera.services.state.merkle.v3.files;

import com.hedera.services.state.merkle.virtual.ContractKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static com.hedera.services.state.merkle.v3.V3TestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.merkle.v3.V3TestUtils.newContractKey;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("SameParameterValue")
public class HalfDiskHashMapTest {

    private static HalfDiskHashMap<ContractKey> createNewTempMap(int count) throws IOException {
        // get non-existent temp dir
        Path tempDirPath = Files.createTempDirectory("HalfDiskHashMapTest");
        deleteDirectoryAndContents(tempDirPath);
        // create map
        HalfDiskHashMap<ContractKey> map = new HalfDiskHashMap<>(count,ContractKey.SERIALIZED_SIZE, ContractKey::new,tempDirPath,"HalfDiskHashMapTest");
        map.printStats();
        return map;
    }

    private static void createSomeData(HalfDiskHashMap<ContractKey> map, int start, int count, long dataMultiplier) throws IOException  {
        map.startWriting();
        for (int i = start; i < (start+count); i++) {
            map.put(newContractKey(i),i*dataMultiplier);
        }
//        map.debugDumpTransactionCache();
        long START = System.currentTimeMillis();
        map.endWriting();
        printTestUpdate(START, count,"Written");
    }

    private static void checkData(HalfDiskHashMap<ContractKey> map, int start, int count, long dataMultiplier) throws IOException  {
        long START = System.currentTimeMillis();
        for (int i = start; i < (start+count); i++) {
            long result = map.get(newContractKey(i),0);
            assertEquals(i*dataMultiplier,result);
        }
        printTestUpdate(START, count,"Read");
    }


    @ParameterizedTest
    @ValueSource(ints = {1, 10, 31, 128, 10_000, 1_000_000})
    public void createDataAndCheck(int count) throws Exception {
        // create map
        HalfDiskHashMap<ContractKey> map = createNewTempMap(count);
        // create some data
        createSomeData(map,1,count,1);
        // sequentially check data
        checkData(map,1,count,1);
        // randomly check data
        Random random = new Random(1234);
        for (int j = 1; j < count; j++) {
            int i = 1+random.nextInt(count);
            long result = map.get(newContractKey(i),0);
            assertEquals(i,result);
        }
    }

    @Test
    public void multipleWriteBatches() throws Exception {
        // create map
        HalfDiskHashMap<ContractKey> map = createNewTempMap(10_000);
        // create some data
        createSomeData(map,1,1111,1);
        checkData(map,1,1111,1);
        // create some more data
        createSomeData(map,1111,3333,1);
        checkData(map,1,3333,1);
        // create some more data
        createSomeData(map,1111,10_000,1);
        checkData(map,1,10_000,1);
    }

    @Test
    public void updateData() throws Exception {
        // create map
        HalfDiskHashMap<ContractKey> map = createNewTempMap(1000);
        // create some data
        createSomeData(map,0,1000,1);
        checkData(map,0,1000,1);
        // update some data
        createSomeData(map,200,400,2);
        checkData(map,0,200,1);
        checkData(map,200,400,2);
        checkData(map,600,400,1);
    }


    private static double printTestUpdate(long start, long count, String msg) {
        long took = System.currentTimeMillis() - start;
        double timeSeconds = (double)took/1000d;
        double perSecond = (double)count / timeSeconds;
        System.out.printf("%s : [%,d] at %,.0f per/sec, took %,.2f seconds\n",msg, count, perSecond, timeSeconds);
        return perSecond;
    }
}
