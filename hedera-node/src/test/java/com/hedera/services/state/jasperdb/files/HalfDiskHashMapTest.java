package com.hedera.services.state.jasperdb.files;

import com.hedera.services.state.jasperdb.ExampleFixedSizeLongKey;
import com.hedera.services.state.jasperdb.ExampleVariableSizeLongKey;
import com.hedera.services.state.jasperdb.files.hashmap.HalfDiskHashMap;
import com.hedera.services.state.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static com.hedera.services.state.jasperdb.JasperDbTestUtils.deleteDirectoryAndContents;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({"SameParameterValue", "unchecked"})
public class HalfDiskHashMapTest {
    // get non-existent temp dir
    private static final Path tempDirPath;
    static {
        try {
            tempDirPath =  Files.createTempDirectory("HalfDiskHashMapTest");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // =================================================================================================================
    // Helper Methods

    private static HalfDiskHashMap<VirtualLongKey> createNewTempMap(TestType testType, int count) throws IOException {
        // create map
        HalfDiskHashMap<VirtualLongKey> map = new HalfDiskHashMap<>(
                count,
                (KeySerializer<VirtualLongKey>) testType.keySerializer,
                tempDirPath.resolve(testType.name()),
                "HalfDiskHashMapTest");
        map.printStats();
        return map;
    }

    private static VirtualLongKey newVirtualLongKey(TestType testType, int i) {
        return testType == TestType.fixed ? new ExampleFixedSizeLongKey(i) : new ExampleVariableSizeLongKey(i);
    }

    private static void createSomeData(TestType testType, HalfDiskHashMap<VirtualLongKey> map, int start, int count, long dataMultiplier) throws IOException  {
        map.startWriting();
        for (int i = start; i < (start+count); i++) {
            map.put(newVirtualLongKey(testType,i),i*dataMultiplier);
        }
        map.debugDumpTransactionCache();
        long START = System.currentTimeMillis();
        map.endWriting();
        printTestUpdate(START, count,"Written");
    }

    private static void checkData(TestType testType, HalfDiskHashMap<VirtualLongKey> map, int start, int count, long dataMultiplier) throws IOException  {
        long START = System.currentTimeMillis();
        for (int i = start; i < (start+count); i++) {
            final var key = newVirtualLongKey(testType,i);
            long result = map.get(key,-1);
            System.out.println("key = " + key+"   result = " + result);
//            System.out.println("result = " + result);
            assertEquals(i*dataMultiplier,result, "Failed to read key="+newVirtualLongKey(testType,i)+" dataMultiplier="+dataMultiplier);
        }
        printTestUpdate(START, count,"Read");
    }

    private static void cleanup() {
        deleteDirectoryAndContents(tempDirPath);
    }

    // =================================================================================================================
    // Tests

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void createDataAndCheck(TestType testType) throws Exception {
        final int count = 10_000;
        // create map
        final HalfDiskHashMap<VirtualLongKey> map = createNewTempMap(testType, count);
        // create some data
        createSomeData(testType, map,1,count,1);
        // sequentially check data
        checkData(testType, map,1,count,1);
        // randomly check data
        Random random = new Random(1234);
        for (int j = 1; j < count; j++) {
            int i = 1+random.nextInt(count);
            long result = map.get(newVirtualLongKey(testType,i),0);
            assertEquals(i,result);
        }
        // cleanup
        cleanup();
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void multipleWriteBatches(TestType testType) throws Exception {
        // create map
        HalfDiskHashMap<VirtualLongKey> map = createNewTempMap(testType, 10_000);
        // create some data
        createSomeData(testType, map,1,1111,1);
        checkData(testType, map,1,1111,1);
        // create some more data
        createSomeData(testType, map,1111,3333,1);
        checkData(testType, map,1,3333,1);
        // create some more data
        createSomeData(testType, map,1111,10_000,1);
        checkData(testType, map,1,10_000,1);
        // cleanup
        cleanup();
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void updateData(TestType testType) throws Exception {
        // create map
        HalfDiskHashMap<VirtualLongKey> map = createNewTempMap(testType, 1000);
        // create some data
        createSomeData(testType, map,0,1000,1);
        checkData(testType, map,0,1000,1);
        // update some data
        createSomeData(testType, map,200,400,2);
        checkData(testType, map,0,200,1);
        checkData(testType, map,200,400,2);
        checkData(testType, map,600,400,1);
        // cleanup
        cleanup();
    }


    private static void printTestUpdate(long start, long count, String msg) {
        long took = System.currentTimeMillis() - start;
        double timeSeconds = (double)took/1000d;
        double perSecond = (double)count / timeSeconds;
        System.out.printf("%s : [%,d] at %,.0f per/sec, took %,.2f seconds\n",msg, count, perSecond, timeSeconds);
    }
}
