package com.hedera.services.state.jasperdb.files;

import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.hedera.services.state.jasperdb.JasperDbTestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.jasperdb.files.DataFileCommon.FOOTER_SIZE;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("SameParameterValue")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataFileCollectionTest {
    protected static final Instant TEST_START = Instant.now();
    protected static Path tempFileDir;
    protected static final Map<TestType,DataFileCollection<long[]>> fileCollectionMap =
            new HashMap<>();
    protected static final Map<TestType,LongArrayList> storedOffsetsMap = new HashMap<>();
    protected static long fixedSizeDataFileSize;

    // =================================================================================================================
    // Helper Methods

    /**
     * For tests, we want to have all different data sizes, so we use this function to choose how many times to repeat
     * the data value long
     */
    private static int getRepeatCountForKey(long key) {
        return (int)(key % 20L);
    }

    /**
     * Create an example variable sized data item with lengths of data from 1 to 20.
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

    protected static void checkData(TestType testType, int fromIndex, int toIndexExclusive, int valueAddition)
                throws Exception {
        final var fileCollection = fileCollectionMap.get(testType);
        final var storedOffsets = storedOffsetsMap.get(testType);
        // now read back all the data and check all data
        for (int i = fromIndex; i < toIndexExclusive; i++) {
            long storedOffset = storedOffsets.get(i);
            // read
            final var dataItem = fileCollection.readDataItem(storedOffset);
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

    // =================================================================================================================
    // Tests

    @BeforeAll
    static void setup() throws IOException {
        tempFileDir = Files.createTempDirectory("DataFileReaderCollectionFixedSizeDataTest");
        // delete any old content there from previous tests runs
        deleteDirectoryAndContents(tempFileDir);
    }

    @Order(1)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void createDataFileCollection(TestType testType) throws Exception {
        final var fileCollection = new DataFileCollection<>(tempFileDir.resolve(testType.name()), "test",
                testType.dataItemSerializer,
                null);
        fileCollectionMap.put(testType,fileCollection);
        // create stored offsets list
        final var storedOffsets = new LongArrayList(1000);
        storedOffsetsMap.put(testType,storedOffsets);
        // create 10x 100 item files
        int count = 0;
        for (int f = 0; f < 10; f++) {
            fileCollection.startWriting();
            // put in 1000 items
            for (int i = count; i < count+100; i++) {
                long[] dataValue;
                switch(testType) {
                    default:
                    case fixed:
                        dataValue = new long[]{i,i+10_000};
                        break;
                    case variable:
                        dataValue = getVariableSizeDataForI(i, 10_000);
                        break;
                }
                // store in file
                storedOffsets.add(fileCollection.storeDataItem(dataValue));
            }
            fileCollection.endWriting(0,count+100);
            count += 100;
        }
        // check 10 files were created
        assertEquals(10,Files.list(tempFileDir.resolve(testType.name())).count());
    }

    @Order(2)
    @Test
    public void checkFileSizes() throws Exception {
        // we can only check for fixed size files easily
        final var testType = TestType.fixed;
        final long dataWritten = testType.dataItemSerializer.getSerializedSize() * 100L;
        final int paddingBytesNeeded = (int)(DataFileCommon.PAGE_SIZE - (dataWritten % DataFileCommon.PAGE_SIZE));
        fixedSizeDataFileSize = dataWritten + paddingBytesNeeded + FOOTER_SIZE;
        Files.list(tempFileDir.resolve(testType.name())).forEach(file -> {
            try {
                assertEquals(fixedSizeDataFileSize, Files.size(file));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Order(3)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void check1000(TestType testType) throws Exception {
        checkData(testType, 0, 1000, 10_000);
    }

    @Order(4)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void checkFilesStates(TestType testType) throws Exception {
        final var fileCollection = fileCollectionMap.get(testType);
        for (int f = 0; f < 10; f++) {
            final var dataFileReader = fileCollection.getDataFile(f);
            DataFileMetadata metadata = dataFileReader.getMetadata();
            assertFalse(metadata.isMergeFile());
            assertEquals(f, metadata.getIndex());
            assertTrue(metadata.getCreationDate().isAfter(TEST_START));
            assertTrue(metadata.getCreationDate().isBefore(Instant.now()));
            assertEquals(0, metadata.getMinimumValidKey());
            assertEquals((f+1)*100, metadata.getMaximumValidKey());
            assertEquals(100, metadata.getDataItemCount());
            assertEquals(0, dataFileReader.getSize() % DataFileCommon.PAGE_SIZE);
            if (testType == TestType.fixed) {
                assertEquals(fixedSizeDataFileSize, dataFileReader.getSize());
            }
        }
    }

    @Order(10)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void createDataFileCollectionWithLoadedDataCallback(TestType testType) throws Exception {
        final LoadedDataCallbackImpl loadedDataCallbackImpl = new LoadedDataCallbackImpl();
        final var fileCollection = new DataFileCollection<>(tempFileDir.resolve(testType.name()), "test",
                testType.dataItemSerializer,
                loadedDataCallbackImpl);
        fileCollectionMap.put(testType,fileCollection);
        // create stored offsets list
        final var storedOffsets = new LongArrayList(1000);
        storedOffsetsMap.put(testType,storedOffsets);
        // create 10x 100 item files
        int count = 0;
        for (int f = 0; f < 10; f++) {
            fileCollection.startWriting();
            // put in 1000 items
            for (int i = count; i < count+100; i++) {
                long[] dataValue;
                switch(testType) {
                    default:
                    case fixed:
                        dataValue = new long[]{i,i+10_000};
                        break;
                    case variable:
                        dataValue = getVariableSizeDataForI(i, 10_000);
                        break;
                }
                // store in file
                storedOffsets.add(fileCollection.storeDataItem(dataValue));
            }
            fileCollection.endWriting(0,count+100);
            count += 100;
        }
        // check that 10 additional files were created
        assertEquals(20,Files.list(tempFileDir.resolve(testType.name())).count());
        // examine loadedDataCallbackImpl content's map sizes
        assertEquals(1000,loadedDataCallbackImpl.dataLocationMap.size());
        assertEquals(1000,loadedDataCallbackImpl.dataValueMap.size());
    }

    @Order(50)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void closeAndReopen(TestType testType) throws Exception {
        var fileCollection = fileCollectionMap.get(testType);
        fileCollection.close();
        fileCollection = new DataFileCollection<>(tempFileDir.resolve(testType.name()), "test",
                testType.dataItemSerializer,
                null);
        fileCollectionMap.put(testType,fileCollection);
    }

    @Order(51)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void check1000AfterReopen(TestType testType) throws Exception {
        checkData(testType, 0, 1000, 10_000);
    }

    @Order(100)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void merge(TestType testType) throws Exception {
        final var fileCollection = fileCollectionMap.get(testType);
        final var storedOffsets = storedOffsetsMap.get(testType);
        final AtomicBoolean mergeComplete = new AtomicBoolean(false);
        IntStream.range(0,2).parallel().forEach(thread -> {
            if (thread == 0) { // checking thread, keep reading and checking data all the time while we are merging
                while(!mergeComplete.get()) {
                    try {
                        checkData(testType, 0, 1000, 10_000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (thread == 1) { // move thread
                try {
                    var filesToMerge = fileCollection.getAllFullyWrittenFiles(Integer.MAX_VALUE);
                    System.out.println("filesToMerge = " + filesToMerge.size());
                    fileCollection.mergeFiles(moves -> {
                        assertEquals(1000,moves.size());
                        moves.forEach((key, oldValue, newValue) -> {
                            System.out.printf("move from file %d item %d -> file %d item %d\n",
                                    DataFileCommon.fileIndexFromDataLocation(oldValue),
                                    DataFileCommon.byteOffsetFromDataLocation(oldValue),
                                    DataFileCommon.fileIndexFromDataLocation(newValue),
                                    DataFileCommon.byteOffsetFromDataLocation(newValue)
                            );
                            int index = storedOffsets.indexOf(oldValue);
                            storedOffsets.set(index, newValue);
                        });
                    }, filesToMerge);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mergeComplete.set(true);
            }
        });
        // check we only have 1 file left
        assertEquals(1,Files.list(tempFileDir.resolve(testType.name())).count());
    }

    @Order(101)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void check1000AfterMerge(TestType testType) throws Exception {
        checkData(testType, 0, 1000, 10_000);
    }


    @Order(200)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void changeSomeData(TestType testType) throws Exception {
        final var fileCollection = fileCollectionMap.get(testType);
        final var storedOffsets = storedOffsetsMap.get(testType);
        fileCollection.startWriting();
        // put in 1000 items
        for (int i = 0; i < 50; i++) {
            long[] dataValue;
            switch(testType) {
                default:
                case fixed:
                    dataValue = new long[]{i,i+100_000};
                    break;
                case variable:
                    dataValue = getVariableSizeDataForI(i, 100_000);
                    break;
            }
            int newI = i * 31;
            // store in file
            storedOffsets.set(i, fileCollection.storeDataItem(dataValue));
        }
        fileCollection.endWriting(0,100);
        // check we now have 2 files
        assertEquals(2,Files.list(tempFileDir.resolve(testType.name())).count());
    }

    @Order(201)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void check1000BeforeMerge(TestType testType) throws Exception {
        checkData(testType,0,50,100_000);
        checkData(testType,50,1000,10_000);
    }

    @Order(202)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void merge2(TestType testType) throws Exception {
        final var fileCollection = fileCollectionMap.get(testType);
        final var storedOffsets = storedOffsetsMap.get(testType);
        final AtomicBoolean mergeComplete = new AtomicBoolean(false);
        IntStream.range(0,2).parallel().forEach(thread -> {
            if (thread == 0) { // checking thread, keep reading and checking data all the time while we are merging
                while(!mergeComplete.get()) {
                    try {
                        checkData(testType,0,50,100_000);
                        checkData(testType,50,1000,10_000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (thread == 1) { // move thread
                // merge 2 files
                try {
                    var allFiles = fileCollection.getAllFullyWrittenFiles();
                    fileCollection.mergeFiles(
                            moves -> moves.forEach((key, oldValue, newValue) -> {
                                System.out.printf("move key %d from file %d item %d -> file %d item %d, updating = %b\n",
                                        key,
                                        DataFileCommon.fileIndexFromDataLocation(oldValue),
                                        DataFileCommon.byteOffsetFromDataLocation(oldValue),
                                        DataFileCommon.fileIndexFromDataLocation(newValue),
                                        DataFileCommon.byteOffsetFromDataLocation(newValue),
                                        storedOffsets.get((int)key) == oldValue
                                );
                                if (storedOffsets.get((int)key) == oldValue) storedOffsets.set((int)key, newValue);
                            }), allFiles);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mergeComplete.set(true);
            }
        });
        // check we 7 files left, as we merged 5 out of 11
        assertEquals(1,Files.list(tempFileDir.resolve(testType.name())).count());
    }

    @Order(203)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void check1000AfterMerge2(TestType testType) throws Exception {
        checkData(testType,0,50,100_000);
        checkData(testType,50,1000,10_000);
    }

    @Order(1000)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void close(TestType testType) throws Exception {
        fileCollectionMap.get(testType).close();
    }

    @AfterAll
    static void cleanup() {
        // clean up and delete files
        deleteDirectoryAndContents(tempFileDir);
    }

    public class LoadedDataCallbackImpl implements DataFileCollection.LoadedDataCallback {
        public Map<Long, Long> dataLocationMap = new HashMap<>();
        public Map<Long, ByteBuffer> dataValueMap = new HashMap<>();

        @Override
        public void newIndexEntry(long key, long dataLocation, ByteBuffer dataValue) {
            dataLocationMap.put(key, dataLocation);
            dataValueMap.put(key, dataValue);
        }
    }
}
