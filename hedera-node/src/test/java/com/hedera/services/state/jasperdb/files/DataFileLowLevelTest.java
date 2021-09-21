package com.hedera.services.state.jasperdb.files;

import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import static com.hedera.services.state.jasperdb.JasperDbTestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.jasperdb.files.DataFileCommon.createDataFilePath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("SameParameterValue")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataFileLowLevelTest {
    protected static final Random RANDOM = new Random(123456);
    protected static final Instant TEST_START = Instant.now();
    public static final int DATA_FILE_INDEX = 123;
    protected static Path tempFileDir;
    protected static final Map<TestType,DataFileMetadata> dataFileMetadataMap = new HashMap<>();
    protected static final Map<TestType,Path> dataFileMap = new HashMap<>();
    protected static final Map<TestType,LongArrayList> listOfDataItemLocationsMap = new HashMap<>();

    // =================================================================================================================
    // Helper Methods

    /**
     * For tests, we want to have all different dta sizes, so we use this function to choose how many times to repeat
     * the data value long
     */
    private int getRepeatCountForKey(long key) {
        return (int)(key % 20L);
    }

    /**
     * Create an example variable sized data item with lengths of data from 0 to 20.
     */
    private long[] getVariableSizeDataForI(int i) {
        int repeatCount = getRepeatCountForKey(i);
        long[] dataValue = new long[1+repeatCount];
        dataValue[0] = i;
        for (int j = 1; j < dataValue.length; j++) {
            dataValue[j] = i+10_000;
        }
        return dataValue;
    }

    /**
     * Check a fixed or variable size data items data
     */
    private void checkItem(TestType testType, int i, long[] dataItem) {
        switch(testType) {
            default:
            case fixed:
                assertEquals(i,dataItem[0]);
                assertEquals(i+10_000,dataItem[1]);
                break;
            case variable:
                assertEquals(
                        Arrays.toString(getVariableSizeDataForI(i)),
                        Arrays.toString(dataItem));
                break;
        }
    }

    // =================================================================================================================
    // Tests

    @BeforeAll
    static void setup() throws IOException {
        tempFileDir = Files.createTempDirectory("DataFileLowLevelTest");
    }

    @Order(2)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void createFile(TestType testType) throws IOException {
        // open file and write data
        DataFileWriter<long[]> writer = new DataFileWriter<>("test_"+testType.name(),tempFileDir, DATA_FILE_INDEX,
                testType.dataItemSerializer,TEST_START,false);
        LongArrayList listOfDataItemLocations = new LongArrayList(1000); 
        for (int i = 0; i < 1000; i++) {
            long[] dataValue;
            switch(testType) {
                default:
                case fixed:
                    dataValue = new long[]{i,i+10_000};
                    break;
                case variable:
                    dataValue = getVariableSizeDataForI(i);
                    break;
            }

            listOfDataItemLocations.add(writer.storeDataItem(dataValue));
        }
        final var dataFileMetadata = writer.finishWriting(0,1000);
        // tests
        assertTrue(Files.exists(writer.getPath()));
        assertEquals(writer.getPath(),createDataFilePath("test_"+testType.name(), tempFileDir, DATA_FILE_INDEX, TEST_START));
        // store for later tests
        dataFileMap.put(testType,writer.getPath());
        dataFileMetadataMap.put(testType,dataFileMetadata);
        listOfDataItemLocationsMap.put(testType,listOfDataItemLocations);
    }

    @Order(100)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void checkMetadataOfWrittenFile(TestType testType){
        final var dataFileMetadata = dataFileMetadataMap.get(testType);
        // check metadata
        assertEquals(1000,dataFileMetadata.getDataItemCount());
        assertEquals(TEST_START,dataFileMetadata.getCreationDate());
        assertEquals(testType.dataItemSerializer.getSerializedSize(),dataFileMetadata.getDataItemValueSize());
        assertEquals(DATA_FILE_INDEX,dataFileMetadata.getIndex());
        assertEquals(0,dataFileMetadata.getMinimumValidKey());
        assertEquals(1000,dataFileMetadata.getMaximumValidKey());
        assertEquals(testType.dataItemSerializer.getCurrentDataVersion(),dataFileMetadata.getSerializationVersion());
        assertEquals(DataFileCommon.FILE_FORMAT_VERSION,dataFileMetadata.getFileFormatVersion());
    }

    @Order(101)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void checkMetadataOfWrittenFileReadBack(TestType testType) throws IOException {
        final var dataFileMetadata = new DataFileMetadata(dataFileMap.get(testType));
        // check metadata
        assertEquals(1000,dataFileMetadata.getDataItemCount());
        assertEquals(TEST_START,dataFileMetadata.getCreationDate());
        assertEquals(testType.dataItemSerializer.getSerializedSize(),dataFileMetadata.getDataItemValueSize());
        assertEquals(DATA_FILE_INDEX,dataFileMetadata.getIndex());
        assertEquals(0,dataFileMetadata.getMinimumValidKey());
        assertEquals(1000,dataFileMetadata.getMaximumValidKey());
        assertEquals(testType.dataItemSerializer.getCurrentDataVersion(),dataFileMetadata.getSerializationVersion());
        assertEquals(DataFileCommon.FILE_FORMAT_VERSION,dataFileMetadata.getFileFormatVersion());
    }

    @Order(200)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void readBackRawData(TestType testType) throws IOException {
        final var dataFile = dataFileMap.get(testType);
        // read the whole file
        ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(dataFile));
        LongBuffer longBuf = buf.asLongBuffer();
        switch(testType) {
            case fixed:
                // check data
                for (int i = 0; i < 1000; i++) {
                    assertEquals(i,longBuf.get());
                    assertEquals(i+10_000,longBuf.get());
                }
                break;
            case variable:
                for (int i = 0; i < 1000; i++) {
                    int repeatCount = getRepeatCountForKey(i);
                    // read size
                    int size = (int) longBuf.get();
                    // TODO be nice to assert size
                    // read key
                    assertEquals(i,longBuf.get());
                    for (int j = 0; j < repeatCount; j++) {
                        assertEquals(i+10_000,longBuf.get());
                    }
                }
                break;
        }
    }

    @Order(201)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void readBackWithReader(TestType testType) throws IOException {
        final var dataFile = dataFileMap.get(testType);
        final var dataFileMetadata = dataFileMetadataMap.get(testType);
        final var listOfDataItemLocations = listOfDataItemLocationsMap.get(testType);
        DataFileReader<long[]> dataFileReader = new DataFileReader<>(dataFile,testType.dataItemSerializer, dataFileMetadata);
        // check by locations returned by write
        for (int i = 0; i < 1000; i++) {
            long[] dataItem = dataFileReader.readDataItem(listOfDataItemLocations.get(i));
            checkItem(testType,i,dataItem);
        }
        // check by location math
        if (testType == TestType.fixed) {
            long offset = 0;
            for (int i = 0; i < 1000; i++) {
                long[] dataItem = dataFileReader.readDataItem(DataFileCommon.dataLocation(DATA_FILE_INDEX, offset));
                assertEquals(i, dataItem[0]);
                assertEquals(i + 10_000, dataItem[1]);
                offset += testType.dataItemSerializer.getSerializedSize();
            }
        }
        // check by random
        IntStream.range(0,10_000)
                .map(i -> RANDOM.nextInt(1000))
                .forEach(i -> {
                    try {
                        long[] dataItem = dataFileReader.readDataItem(listOfDataItemLocations.get(i));
                        checkItem(testType,i,dataItem);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        // check by random parallel
        IntStream.range(0,10_000)
                .map(i -> RANDOM.nextInt(1000))
                .parallel()
                .forEach(i -> {
                    try {
                        long[] dataItem = dataFileReader.readDataItem(listOfDataItemLocations.get(i));
                        checkItem(testType,i,dataItem);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        dataFileReader.close();
    }

    @Order(300)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void readBackWithIterator(TestType testType) throws IOException {
        final var dataFile = dataFileMap.get(testType);
        final var dataFileMetadata = dataFileMetadataMap.get(testType);
        final var listOfDataItemLocations = listOfDataItemLocationsMap.get(testType);
        DataFileIterator fileIterator = new DataFileIterator(dataFile,dataFileMetadata,testType.dataItemSerializer);
        int i = 0;
        while (fileIterator.next()) {
            assertEquals(listOfDataItemLocations.get(i), fileIterator.getDataItemsDataLocation());
            assertEquals(i,fileIterator.getDataItemsKey());
            ByteBuffer data = fileIterator.getDataItemData();
            long[] dataItem = testType.dataItemSerializer.deserialize(data,dataFileMetadata.getSerializationVersion());
            checkItem(testType,i,dataItem);
            i++;
        }
    }

    @Order(400)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void copyFile(TestType testType) throws IOException {
        DataFileWriter<long[]> newDataFileWriter = new DataFileWriter<>("test_"+testType.name(),tempFileDir,
                DATA_FILE_INDEX+1, testType.dataItemSerializer,
                TEST_START.plus(1, ChronoUnit.SECONDS),false);

        final var dataFile = dataFileMap.get(testType);
        final var dataFileMetadata = dataFileMetadataMap.get(testType);
        DataFileIterator fileIterator = new DataFileIterator(dataFile,dataFileMetadata,testType.dataItemSerializer);
        final LongArrayList newDataLocations = new LongArrayList(1000);
        while (fileIterator.next()) {
            final ByteBuffer itemData = fileIterator.getDataItemData();
            newDataLocations.add(newDataFileWriter.writeCopiedDataItem(dataFileMetadata.getSerializationVersion(),itemData));
        }
        final var newDataFileMetadata = newDataFileWriter.finishWriting(0,1000);
        // now read back and check
        DataFileReader<long[]> dataFileReader = new DataFileReader<>(newDataFileWriter.getPath(),
                testType.dataItemSerializer, newDataFileMetadata);
        // check by locations returned by write
        for (int i = 0; i < 1000; i++) {
            long[] dataItem = dataFileReader.readDataItem(newDataLocations.get(i));
            checkItem(testType,i,dataItem);
        }
    }

    @AfterAll
    static void cleanup() {
        // clean up and delete files
        deleteDirectoryAndContents(tempFileDir);
    }
}
