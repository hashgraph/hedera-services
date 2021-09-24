package com.hedera.services.state.jasperdb.files;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.hedera.services.state.jasperdb.JasperDbTestUtils.deleteDirectoryAndContents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MemoryIndexDiskKeyValueStoreTest {

    // =================================================================================================================
    // Helper Methods

    /**
     * For tests, we want to have all different dta sizes, so we use this function to choose how many times to repeat
     * the data value long
     */
    private static int getRepeatCountForKey(long key) {
        return (int)(key % 20L);
    }

    /**
     * Create an example variable sized data item with lengths of data from 0 to 20.
     */
    private static long[] getVariableSizeDataForI(int i, int valueAddition) {
        int repeatCount = getRepeatCountForKey(i);
        long[] dataValue = new long[1+repeatCount];
        dataValue[0] = i;
        for (int j = 1; j < dataValue.length; j++) {
            dataValue[j] = i+valueAddition;
        }
        return dataValue;
    }

    private void checkRange(TestType testType, MemoryIndexDiskKeyValueStore<long[]> store,
                            int start, int count, int valueAddition) throws IOException {
        { // check hash data value only
            for (int i = start; i < (start + count); i++) {
                // read
                final var dataItem = store.get(i);
                assertNotNull(dataItem);
                switch(testType) {
                    default:
                    case fixed:
                        assertEquals(2, dataItem.length); // size
                        assertEquals(i,dataItem[0]); // key
                        assertEquals(i+valueAddition,dataItem[1]); // value
                        break;
                    case variable:
                        assertEquals(
                                Arrays.toString(getVariableSizeDataForI(i, valueAddition)),
                                Arrays.toString(dataItem));
                        break;
                }
            }
        }
    }

    private void writeBatch(TestType testType, MemoryIndexDiskKeyValueStore<long[]> store,
                            int start, int count, int valueAddition) throws IOException {
        store.startWriting();
        for (int i = start; i < (start+count); i++) {
            long[] dataValue;
            switch(testType) {
                default:
                case fixed:
                    dataValue = new long[]{i,i+valueAddition};
                    break;
                case variable:
                    dataValue = getVariableSizeDataForI(i, valueAddition);
                    break;
            }
            store.put(i,dataValue);
        }
        store.endWriting(0, Integer.MAX_VALUE);
    }

    // =================================================================================================================
    // Tests

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void createDataAndCheck(TestType testType) throws Exception {
        MemoryIndexDiskKeyValueStore.ENABLE_DEEP_VALIDATION = true;
        // let's store hashes as easy test class
        Path tempDir = Files.createTempDirectory("DataFileTest");
        // reuse the LoadedDataCallbackImpl simple implementation developed in DataFileCollectionTest
        DataFileCollection.LoadedDataCallback loadedDataCallback = new DataFileCollectionTest.LoadedDataCallbackImpl();
        MemoryIndexDiskKeyValueStore<long[]> store = new MemoryIndexDiskKeyValueStore<>(
                tempDir,"MemoryIndexDiskKeyValueStoreTest", testType.dataItemSerializer,
                loadedDataCallback);
        // write some batches of data, then check all the contents, we should end up with 3 data files.
        writeBatch(testType, store,0,1000,1234);
        checkRange(testType, store,0,1000,1234);
        writeBatch(testType, store,1000,1500,1234);
        checkRange(testType, store,0,1500,1234);
        writeBatch(testType, store,1500,2000,1234);
        checkRange(testType, store,0,2000,1234);
        // check number of files created
        assertEquals(3,Files.list(tempDir).count());
        // merge all files
        store.merge(dataFileReaders -> dataFileReaders);
        // check number of files after merge
        assertEquals(1,Files.list(tempDir).count());
        // check all data
        checkRange(testType, store,0,2000,1234);
        // change some data and check
        writeBatch(testType, store,1500,2000,8910);
        checkRange(testType, store,0,1500,1234);
        checkRange(testType, store,1500,2000,8910);
        // do one more write, so we have two files and all data has same valueAddition
        writeBatch(testType, store,0,1500,8910);
        // do a merge, read in parallel
        final AtomicBoolean mergeFinished = new AtomicBoolean(false);
        IntStream.range(0,2).parallel().forEach(i -> {
            if (i == 0) {
                while (!mergeFinished.get()) {
                    try {
                        checkRange(testType, store,0,2000,8910);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                try {
                    store.merge(dataFileReaders -> dataFileReaders);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mergeFinished.set(true);
            }
        });
        // test one writer and many readers in parallel
        IntStream.range(0,10).parallel().forEach(i -> {
            try {
                if (i == 0) {
                    writeBatch(testType, store,2000,50_000,56_000);
                } else {
                    Thread.sleep(i);
                    checkRange(testType, store,0,2000,8910);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        // check all data is correct after
        checkRange(testType, store,0,2000,8910);
        checkRange(testType, store,2000,48_000,56_000);
        // check number of files created
        assertEquals(2,Files.list(tempDir).count());

        // call get() on invalid keys .
        assertNull(store.get(-1));
        assertNull(store.get(60000));

        // clean up and delete files
        deleteDirectoryAndContents(tempDir);
        store.close();
    }
}
