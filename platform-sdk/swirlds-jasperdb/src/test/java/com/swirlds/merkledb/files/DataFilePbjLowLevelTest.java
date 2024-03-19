/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.merkledb.files;

import static com.swirlds.merkledb.files.DataFileCommon.FILE_EXTENSION;
import static com.swirlds.merkledb.files.DataFileCommon.createDataFilePath;
import static com.swirlds.merkledb.files.DataFileCompactor.INITIAL_COMPACTION_LEVEL;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.serialize.DataItemHeader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
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
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings("SameParameterValue")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataFilePbjLowLevelTest {

    /** Temporary directory provided by JUnit */
    @SuppressWarnings("unused")
    @TempDir
    static Path tempFileDir;

    private final MerkleDbConfig dbConfig = ConfigurationHolder.getConfigData(MerkleDbConfig.class);

    protected static final Random RANDOM = new Random(123456);
    protected static final Instant TEST_START = Instant.now();
    protected static final Map<FilesTestType, DataFileMetadata> dataFileMetadataMap = new HashMap<>();
    protected static final Map<FilesTestType, Path> dataFileMap = new HashMap<>();
    protected static final Map<FilesTestType, LongArrayList> listOfDataItemLocationsMap = new HashMap<>();
    private static final int DATA_FILE_INDEX = 123;

    // =================================================================================================================
    // Helper Methods

    /**
     * For tests, we want to have all different dta sizes, so we use this function to choose how
     * many times to repeat the data value long
     */
    private int getRepeatCountForKey(long key) {
        return (int) (key % 20L);
    }

    /** Create an example variable sized data item with lengths of data from 0 to 20. */
    private long[] getVariableSizeDataForI(int i) {
        int repeatCount = getRepeatCountForKey(i);
        long[] dataValue = new long[1 + repeatCount];
        dataValue[0] = i;
        for (int j = 1; j < dataValue.length; j++) {
            dataValue[j] = i + 10_000;
        }
        return dataValue;
    }

    /** Check a fixed or variable size data items data */
    private void checkItem(FilesTestType testType, int i, long[] dataItem) {
        switch (testType) {
            default:
            case fixed:
                assertEquals(i, dataItem[0], "unexpected data item[0]");
                assertEquals(i + 10_000, dataItem[1], "unexpected data item[1]");
                break;
            case variable:
                assertEquals(
                        Arrays.toString(getVariableSizeDataForI(i)),
                        Arrays.toString(dataItem),
                        "unexpected variable data");
                break;
        }
    }

    // =================================================================================================================
    // Tests

    @Order(2)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void createFile(FilesTestType testType) throws IOException {
        // open file and write data
        DataFileWriter<long[]> writer = new DataFileWriterPbj<>(
                "test_" + testType.name(),
                tempFileDir,
                DATA_FILE_INDEX,
                testType.dataItemSerializer,
                TEST_START,
                INITIAL_COMPACTION_LEVEL);
        LongArrayList listOfDataItemLocations = new LongArrayList(1000);
        for (int i = 0; i < 1000; i++) {
            long[] dataValue;
            switch (testType) {
                default:
                case fixed:
                    dataValue = new long[] {i, i + 10_000};
                    break;
                case variable:
                    dataValue = getVariableSizeDataForI(i);
                    break;
            }

            listOfDataItemLocations.add(writer.storeDataItem(dataValue));
        }
        writer.finishWriting();
        final var dataFileMetadata = writer.getMetadata();
        // tests
        assertTrue(Files.exists(writer.getPath()), "expected file does not exist");
        assertEquals(
                writer.getPath(),
                createDataFilePath("test_" + testType.name(), tempFileDir, DATA_FILE_INDEX, TEST_START, FILE_EXTENSION),
                "unexpected path for writer");
        // store for later tests
        dataFileMap.put(testType, writer.getPath());
        dataFileMetadataMap.put(testType, dataFileMetadata);
        listOfDataItemLocationsMap.put(testType, listOfDataItemLocations);
    }

    @Order(50)
    @Test
    void checkToStringOfDataItemHeader() {
        int size = RANDOM.nextInt(300) + 1;
        long key = RANDOM.nextLong();
        DataItemHeader dataItemHeader = new DataItemHeader(size, key);
        String expectedToString = "DataItemHeader[size=" + size + ",key=" + key + "]";
        assertEquals(expectedToString, dataItemHeader.toString(), "unexpected value of toString()");
    }

    @Order(100)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void checkMetadataOfWrittenFile(FilesTestType testType) {
        final var dataFileMetadata = dataFileMetadataMap.get(testType);
        // check metadata
        assertEquals(1000, dataFileMetadata.getDataItemCount(), "unexpected DataItemCount");
        assertEquals(TEST_START, dataFileMetadata.getCreationDate(), "unexpected creation date");
        assertEquals(DATA_FILE_INDEX, dataFileMetadata.getIndex(), "unexpected Index");
        assertEquals(
                testType.dataItemSerializer.getCurrentDataVersion(),
                dataFileMetadata.getSerializationVersion(),
                "unexpected Data Version");
        if (testType == FilesTestType.fixed) {
            String expectedToString = "DataFileMetadata["
                    + "itemsCount=1000,index=123,creationDate="
                    + TEST_START
                    + ","
                    + "serializationVersion=1]";
            assertEquals(expectedToString, dataFileMetadata.toString(), "unexpected toString() value");
        }
    }

    @Order(101)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void checkMetadataOfWrittenFileReadBack(FilesTestType testType) throws IOException {
        final var dataFileMetadata = new DataFileMetadata(dataFileMap.get(testType));
        // check metadata
        assertEquals(1000, dataFileMetadata.getDataItemCount(), "unexpected data item count");
        assertEquals(TEST_START, dataFileMetadata.getCreationDate(), "unexpected creation date");
        assertEquals(DATA_FILE_INDEX, dataFileMetadata.getIndex(), "unexpected Index value");
        assertEquals(
                testType.dataItemSerializer.getCurrentDataVersion(),
                dataFileMetadata.getSerializationVersion(),
                "unexpected data version");
    }

    @Order(200)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void readBackRawData(FilesTestType testType) throws IOException {
        final var dataFile = dataFileMap.get(testType);
        final int fileSize = (int) Files.size(dataFile);
        final var dataFileMetadata = dataFileMetadataMap.get(testType);
        final int headerSize = dataFileMetadata.metadataSizeInBytes();
        // read the whole file
        BufferedData buf = BufferedData.wrap(Files.readAllBytes(dataFile), headerSize, fileSize - headerSize);
        for (int i = 0; i < 1000; i++) {
            final int tag = buf.readVarInt(false);
            assertEquals(DataFileCommon.FIELD_DATAFILE_ITEMS.number(), tag >>> ProtoParserTools.TAG_FIELD_OFFSET);
            final int size = buf.readVarInt(false);
            switch (testType) {
                case fixed:
                    assertEquals(Long.BYTES * 2, size);
                    // check data
                    //                    assertEquals(i, longBuf.get(), "unexpected data value");
                    assertEquals(i, buf.readLong(), "unexpected data value");
                    //                    assertEquals(i + 10_000, longBuf.get(), "unexpected value from get()");
                    assertEquals(i + 10_000, buf.readLong(), "unexpected value from get()");
                    break;
                case variable:
                    int repeatCount = getRepeatCountForKey(i);
                    assertEquals(Long.BYTES + Long.BYTES + Long.BYTES * repeatCount, size); // size + key + data
                    int dataItemSize = (int) buf.readLong();
                    assertEquals(Long.BYTES + Long.BYTES + Long.BYTES * repeatCount, dataItemSize); // size + key + data
                    // read key
                    assertEquals(i, buf.readLong(), "unexpected data value #2");
                    for (int j = 0; j < repeatCount; j++) {
                        assertEquals(i + 10_000, buf.readLong(), "unexcted value from get() #2");
                    }
                    break;
                default:
                    buf.skip(size);
            }
        }
    }

    @Order(201)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void readBackWithReader(FilesTestType testType) throws IOException {
        final var dataFile = dataFileMap.get(testType);
        final var dataFileMetadata = dataFileMetadataMap.get(testType);
        final var listOfDataItemLocations = listOfDataItemLocationsMap.get(testType);
        DataFileReader<long[]> dataFileReader =
                new DataFileReaderPbj<>(dbConfig, dataFile, testType.dataItemSerializer, dataFileMetadata);
        // check by locations returned by write
        for (int i = 0; i < 1000; i++) {
            long[] dataItem = dataFileReader.readDataItem(listOfDataItemLocations.get(i));
            checkItem(testType, i, dataItem);
        }
        // check by location math
        if (testType == FilesTestType.fixed) {
            long offset = dataFileMetadata.metadataSizeInBytes();
            for (int i = 0; i < 1000; i++) {
                long[] dataItem = dataFileReader.readDataItem(DataFileCommon.dataLocation(DATA_FILE_INDEX, offset));
                assertEquals(i, dataItem[0], "unexpected dataItem[0]");
                assertEquals(i + 10_000, dataItem[1], "unexpected dataItem[1]");
                offset += ProtoWriterTools.sizeOfDelimited(DataFileCommon.FIELD_DATAFILE_ITEMS, Long.BYTES * 2);
            }
        }
        // check by random
        IntStream.range(0, 10_000).map(i -> RANDOM.nextInt(1000)).forEach(i -> {
            try {
                long[] dataItem = dataFileReader.readDataItem(listOfDataItemLocations.get(i));
                checkItem(testType, i, dataItem);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        // check by random parallel
        IntStream.range(0, 10_000).map(i -> RANDOM.nextInt(1000)).parallel().forEach(i -> {
            try {
                long[] dataItem = dataFileReader.readDataItem(listOfDataItemLocations.get(i));
                checkItem(testType, i, dataItem);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        // some additional asserts to increase DataFileReader's coverage.
        DataFileReader<long[]> secondReader = new DataFileReaderPbj<>(dbConfig, dataFile, testType.dataItemSerializer);
        DataFileIterator<long[]> firstIterator = dataFileReader.createIterator();
        DataFileIterator<long[]> secondIterator = secondReader.createIterator();
        assertEquals(firstIterator.getMetadata(), secondIterator.getMetadata(), "unexpected metadata");
        assertEquals(
                firstIterator.getMetadata().hashCode(),
                secondIterator.getMetadata().hashCode(),
                "unexpected hashcode");
        assertEquals(firstIterator.toString(), secondIterator.toString(), "unexpected toString() value #1");
        assertEquals(firstIterator.hashCode(), secondIterator.hashCode(), "unexpected hashCode() value #1");
        assertEquals(firstIterator, secondIterator, "unexpected iterator value");
        assertEquals(secondReader.getIndex(), dataFileReader.getIndex(), "unexpected Index value");
        assertEquals(secondReader.getPath(), dataFileReader.getPath(), "unexpected Path number");
        assertEquals(secondReader.getSize(), dataFileReader.getSize(), "unexpected reader size");
        assertEquals(secondReader, dataFileReader, "unexpected reader value");
        assertEquals(secondReader.hashCode(), dataFileReader.hashCode(), "unexpected hashCode() value #2");
        assertEquals(0, secondReader.compareTo(dataFileReader), "readers don't compare as expected");
        assertEquals(secondReader.toString(), dataFileReader.toString(), "unexpected toString() value #2");

        firstIterator.close();
        secondIterator.close();
        dataFileReader.close();
        secondReader.close();
    }

    @Order(300)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void readBackWithIterator(FilesTestType testType) throws IOException {
        final var dataFile = dataFileMap.get(testType);
        final var dataFileMetadata = dataFileMetadataMap.get(testType);
        final var listOfDataItemLocations = listOfDataItemLocationsMap.get(testType);
        DataFileIteratorPbj<long[]> fileIterator =
                new DataFileIteratorPbj<>(dbConfig, dataFile, dataFileMetadata, testType.dataItemSerializer);
        int i = 0;
        while (fileIterator.next()) {
            assertEquals(
                    listOfDataItemLocations.get(i),
                    fileIterator.getDataItemDataLocation(),
                    "unexpected data items data location");
            long[] dataItem = fileIterator.getDataItemData();
            assertEquals(i, dataItem[0], "unexpected data items key");
            checkItem(testType, i, dataItem);
            i++;
        }
    }

    @Order(400)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void copyFile(FilesTestType testType) throws IOException {
        DataFileWriter<long[]> newDataFileWriter = new DataFileWriterPbj<>(
                "test_" + testType.name(),
                tempFileDir,
                DATA_FILE_INDEX + 1,
                testType.dataItemSerializer,
                TEST_START.plus(1, ChronoUnit.SECONDS),
                INITIAL_COMPACTION_LEVEL);

        final var dataFile = dataFileMap.get(testType);
        final var dataFileMetadata = dataFileMetadataMap.get(testType);
        DataFileIteratorPbj<long[]> fileIterator =
                new DataFileIteratorPbj<>(dbConfig, dataFile, dataFileMetadata, testType.dataItemSerializer);
        final LongArrayList newDataLocations = new LongArrayList(1000);
        while (fileIterator.next()) {
            final long[] itemData = fileIterator.getDataItemData();
            newDataLocations.add(newDataFileWriter.storeDataItem(itemData));
        }
        newDataFileWriter.finishWriting();
        final var newDataFileMetadata = newDataFileWriter.getMetadata();
        // now read back and check
        DataFileReader<long[]> dataFileReader = new DataFileReaderPbj<>(
                dbConfig, newDataFileWriter.getPath(), testType.dataItemSerializer, newDataFileMetadata);
        // check by locations returned by write
        for (int i = 0; i < 1000; i++) {
            long[] dataItem = dataFileReader.readDataItem(newDataLocations.get(i));
            checkItem(testType, i, dataItem);
        }
    }

    @Order(500)
    @Test
    void dataFileOutputStreamTests() throws IOException {
        int capacity = 1000;
        DataFileOutputStream dataFileOutputStream = new DataFileOutputStream(capacity);
        dataFileOutputStream.write(42); // one byte
        dataFileOutputStream.writeBoolean(true); // a second byte
        dataFileOutputStream.writeByte(42); // a third byte
        dataFileOutputStream.writeBytes("hello"); // 5 additional bytes, total of 8
        dataFileOutputStream.writeChars("hi"); // 4 additional bytes, total of 12
        assertEquals(12, dataFileOutputStream.bytesWritten(), "unexpected # of bytes written #1");

        ByteArrayOutputStream myOutputStream = new ByteArrayOutputStream(capacity);
        dataFileOutputStream.writeTo(myOutputStream);
        byte[] expectedBytes = "*\u0001*hello\u0000h\u0000i".getBytes(StandardCharsets.UTF_8);
        String outputString = myOutputStream.toString(StandardCharsets.UTF_8);
        byte[] actualBytes = outputString.getBytes();
        assertArrayEquals(expectedBytes, actualBytes, "byte arrays do not match #1");

        ByteBuffer myByteBuffer = ByteBuffer.allocate(dataFileOutputStream.bytesWritten());
        dataFileOutputStream.writeTo(myByteBuffer);
        actualBytes = myByteBuffer.array();
        assertArrayEquals(expectedBytes, actualBytes, "byte arrays do not match #2");

        dataFileOutputStream.reset();
        myOutputStream.reset();
        myByteBuffer.clear();

        dataFileOutputStream.write(42); // one byte
        assertEquals(1, dataFileOutputStream.bytesWritten(), "unexpected # of bytes written #2");
        dataFileOutputStream.writeTo(myOutputStream);
        dataFileOutputStream.flush();
        outputString = myOutputStream.toString(StandardCharsets.UTF_8);
        assertEquals("*", outputString, "unexpected value of output stream after reset()");

        myByteBuffer = ByteBuffer.allocate(outputString.length());
        dataFileOutputStream.writeTo(myByteBuffer);
        actualBytes = myByteBuffer.array();
        expectedBytes = new byte[] {42};
        assertArrayEquals(expectedBytes, actualBytes, "byte arrays do not match #3");
    }
}
