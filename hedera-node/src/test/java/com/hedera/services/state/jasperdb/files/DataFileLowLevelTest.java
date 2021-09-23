package com.hedera.services.state.jasperdb.files;

import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        long expectedFileSizeEstimate = (testType == TestType.fixed ? 24576L : 102400L); // TODO - explain the numbers
        assertEquals(expectedFileSizeEstimate, writer.getFileSizeEstimate());
        // store for later tests
        dataFileMap.put(testType,writer.getPath());
        dataFileMetadataMap.put(testType,dataFileMetadata);
        listOfDataItemLocationsMap.put(testType,listOfDataItemLocations);
    }

    @Order(50)
    @Test
    public void checkToStringOfDataItemHeader() {
        int size = RANDOM.nextInt(300) + 1;
        long key = RANDOM.nextLong();
        DataItemHeader dataItemHeader = new DataItemHeader(size, key);
        String expectedToString = "DataItemHeader{size=" + size + ", key=" + key + "}";
        assertEquals(expectedToString, dataItemHeader.toString());
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
        assertEquals(testType == TestType.variable, dataFileMetadata.hasVariableSizeData());
        assertEquals(DATA_FILE_INDEX,dataFileMetadata.getIndex());
        assertEquals(0,dataFileMetadata.getMinimumValidKey());
        assertEquals(1000,dataFileMetadata.getMaximumValidKey());
        assertFalse(dataFileMetadata.isMergeFile());
        assertEquals(testType.dataItemSerializer.getCurrentDataVersion(),dataFileMetadata.getSerializationVersion());
        assertEquals(DataFileCommon.FILE_FORMAT_VERSION,dataFileMetadata.getFileFormatVersion());
        if (testType == TestType.fixed) {
            String expectedToString = "DataFileMetadata{fileFormatVersion=1, dataItemValueSize=16, dataItemCount=1000, " +
                    "index=123, creationDate=" + TEST_START + ", minimumValidKey=0, maximumValidKey=1000, " +
                    "isMergeFile=false, serializationVersion=1}";
            assertEquals(expectedToString, dataFileMetadata.toString());
        }
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
        // some additional asserts to increase DataFileReader's coverage.
        DataFileReader<long[]> secondReader = new DataFileReader<>(dataFile,testType.dataItemSerializer);
        DataFileIterator firstIterator = dataFileReader.createIterator();
        DataFileIterator secondIterator = secondReader.createIterator();
        assertEquals(firstIterator.getMetadata(), secondIterator.getMetadata());
        assertEquals(firstIterator.getMetadata().hashCode(), secondIterator.getMetadata().hashCode());
        assertEquals(firstIterator.getDataFileCreationDate(), secondIterator.getDataFileCreationDate());
        assertEquals(firstIterator.toString(), secondIterator.toString());
        assertEquals(firstIterator.hashCode(), secondIterator.hashCode());
        assertEquals(firstIterator, secondIterator);
        assertEquals(secondReader.getIndex(), dataFileReader.getIndex());
        assertEquals(secondReader.getPath(), dataFileReader.getPath());
        assertEquals(secondReader.getSize(), dataFileReader.getSize());
        assertEquals(secondReader, dataFileReader);
        assertEquals(secondReader.hashCode(), dataFileReader.hashCode());
        assertEquals(0, secondReader.compareTo(dataFileReader));
        assertEquals(secondReader.toString(), dataFileReader.toString());

        firstIterator.close();
        secondIterator.close();
        dataFileReader.close();
        secondReader.close();
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

    @Order(500)
    @ParameterizedTest
    @EnumSource(TestType.class)
    public void dataFileOutputStreamTests(TestType testType) throws IOException {
        int capacity = 1000;
        DataFileOutputStream dataFileOutputStream = new DataFileOutputStream(capacity);
        dataFileOutputStream.write(42); // one byte
        dataFileOutputStream.writeBoolean(true); // a second byte
        dataFileOutputStream.writeByte(42); // a third byte
        dataFileOutputStream.writeBytes("hello"); // 5 additional bytes, total of 8
        dataFileOutputStream.writeChars("hi"); // 4 additional bytes, total of 12
        assertEquals(12, dataFileOutputStream.bytesWritten());

        ByteArrayOutputStream myOutputStream = new ByteArrayOutputStream(capacity);
        dataFileOutputStream.writeTo(myOutputStream);
        byte[] expectedBytes = "*\u0001*hello\u0000h\u0000i".getBytes(StandardCharsets.UTF_8);
        String outputString = myOutputStream.toString(StandardCharsets.UTF_8);
        byte[] actualBytes = outputString.getBytes();
        assertArrayEquals(expectedBytes, actualBytes);

        ByteBuffer myByteBuffer = ByteBuffer.allocate(dataFileOutputStream.bytesWritten());
        dataFileOutputStream.writeTo(myByteBuffer);
        actualBytes = myByteBuffer.array();
        assertArrayEquals(expectedBytes, actualBytes);

        dataFileOutputStream.reset();
        myOutputStream.reset();
        myByteBuffer.clear();

        dataFileOutputStream.write(42); // one byte
        assertEquals(1, dataFileOutputStream.bytesWritten());
        dataFileOutputStream.writeTo(myOutputStream);
        dataFileOutputStream.flush();
        outputString = myOutputStream.toString(StandardCharsets.UTF_8);
        assertEquals("*", outputString);

        myByteBuffer = ByteBuffer.allocate(outputString.length());
        dataFileOutputStream.writeTo(myByteBuffer);
        actualBytes = myByteBuffer.array();
        expectedBytes = new byte[]{42};
        assertArrayEquals(expectedBytes, actualBytes);
    }

    @AfterAll
    static void cleanup() {
        // clean up and delete files
        deleteDirectoryAndContents(tempFileDir);
    }
}
