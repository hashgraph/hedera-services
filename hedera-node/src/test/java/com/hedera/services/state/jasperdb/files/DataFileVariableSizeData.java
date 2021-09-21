package com.hedera.services.state.jasperdb.files;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.state.jasperdb.JasperDbTestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.jasperdb.files.DataFileCommon.VARIABLE_DATA_SIZE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DataFiles support both fixed size and variable size data, this unit test checks it with variable size data
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataFileVariableSizeData {
//    private static final int INDEX = 42;
//    private static final int MAX_DATA_SIZE = Integer.BYTES * 1001;
//    private static final Instant TEST_START = Instant.now();
//    private static Path tempFileDir;
//    private static DataFileReader dataFile;
//    private static DataFileWriter fileWriter;
//    private static List<Long> storedOffsets;
//
//    public DataFileReaderFactory factoryForReaderToTest() {
//        return new DataFileReaderFactory() {
//            @Override
//            public DataFileReader newDataFileReader(Path path) throws IOException {
//                return new DataFileReaderThreadLocal(path);
//            }
//
//            @Override
//            public DataFileReader newDataFileReader(Path path, DataFileMetadata metadata) throws IOException {
//                return new DataFileReaderThreadLocal(path,metadata);
//            }
//        };
//    }
//
//    /**
//     * Check a data value, each data value is i x Integers of i
//     */
//    private void checkDataValue(ByteBuffer buf, int i) {
//        final int dataIntCount = i+1;
//        for (int d = 0; d < dataIntCount; d++) {
//            assertEquals(i,buf.getInt());
//        }
//    }
//
//    @Test
//    @Order(1)
//    public void createDataFile() throws Exception {
//        // get non-existent temp file
//        tempFileDir = Files.createTempDirectory("DataFileTest");
//        deleteDirectoryAndContents(tempFileDir);
//        Files.createDirectories(tempFileDir);
//        // create data file
//        fileWriter = new DataFileWriter("TestFile", tempFileDir, INDEX, VARIABLE_DATA_SIZE, Instant.now(), false);
//    }
//
//    @Test
//    @Order(2)
//    public void put1000() throws Exception {
//        // put in 1000 items
//        ByteBuffer tempData = ByteBuffer.allocate(MAX_DATA_SIZE);
//        storedOffsets = new ArrayList<>();
//        for (int i = 0; i < 1000; i++) {
//            final int dataIntCount = i+1;
//            // prep data buffer
//            tempData.clear();
//            tempData.limit(Integer.BYTES * dataIntCount);
//            for (int d = 0; d < dataIntCount; d++) {
//                tempData.putInt(i);
//            }
//            tempData.flip();
//            // store in file
//            storedOffsets.add(fileWriter.storeData(i, tempData));
//        }
//        // now finish writing
//        DataFileMetadata metadata = fileWriter.finishWriting(0, 1000,new int[]{1});
//        // check state
//        assertFalse(metadata.isMergeFile());
//        assertEquals(INDEX, metadata.getIndex());
//        assertEquals(VARIABLE_DATA_SIZE, metadata.getDataItemValueSize());
//        assertTrue(metadata.getCreationDate().isAfter(TEST_START));
//        assertTrue(metadata.getCreationDate().isBefore(Instant.now()));
//        assertEquals(0, metadata.getMinimumValidKey());
//        assertEquals(1000, metadata.getMaximumValidKey());
//        assertEquals(1000, metadata.getDataItemCount());
//        assertEquals(0, fileWriter.getFileSizeEstimate() % DataFileCommon.PAGE_SIZE);
//        assertEquals(0, Files.size(fileWriter.getPath()) % DataFileCommon.PAGE_SIZE);
//
//        // open for reading
//        dataFile = factoryForReaderToTest().newDataFileReader(fileWriter.getPath(),metadata);
////        hexDump(System.out,fileWriter.getPath());
//    }
//
//
//    @Test
//    @Order(4)
//    public void check1000() throws Exception {
//        // now read back all the data and check all data
//        ByteBuffer tempResult = ByteBuffer.allocate(DATA_ITEM_KEY_SIZE + MAX_DATA_SIZE);
//        for (int i = 0; i < 1000; i++) {
//            long storedOffset = storedOffsets.get(i);
//            tempResult.clear();
//            // read
//            dataFile.readData(tempResult, storedOffset, DataFileReader.DataToRead.KEY_VALUE);
//            // check all the data
//            tempResult.rewind();
//            assertEquals(i, tempResult.getLong()); // key
//            // value data
//            checkDataValue(tempResult,i);
//        }
//    }
//
//    @Test
//    @Order(5)
//    public void check1000RandomOrder() throws Exception {
//        // now read back all the data and check all data random order
//        ByteBuffer tempResult = ByteBuffer.allocate(DATA_ITEM_KEY_SIZE + MAX_DATA_SIZE);
//        List<Integer> randomOrderIndexes = IntStream.range(0,1000).boxed().collect(Collectors.toList());
//        Collections.shuffle(randomOrderIndexes);
//        for (int i : randomOrderIndexes) {
//            long storedOffset = storedOffsets.get(i);
//            tempResult.clear();
//            // read
//            dataFile.readData(tempResult, storedOffset, DataFileReader.DataToRead.KEY_VALUE);
//            // check all the data
//            tempResult.rewind();
//            assertEquals(i, tempResult.getLong()); // key
//            checkDataValue(tempResult,i); // value
//        }
//    }
//    @Test
//    @Order(5)
//    public void check1000RandomMultiThreadedRead() throws Exception {
//        // now read back all the data and check all data random order
//        List<Integer> randomOrderIndexes = IntStream.range(0,1000).boxed().collect(Collectors.toList());
//        Collections.shuffle(randomOrderIndexes);
//        randomOrderIndexes.stream().parallel().forEach(i -> {
//            ByteBuffer tempResult = ByteBuffer.allocate(DATA_ITEM_KEY_SIZE + MAX_DATA_SIZE);
//            try {
//                long storedOffset = storedOffsets.get(i);
//                tempResult.clear();
//                // read
//                dataFile.readData(tempResult, storedOffset, DataFileReader.DataToRead.KEY_VALUE);
//                // check all the data
//                tempResult.rewind();
//                assertEquals(i.longValue(), tempResult.getLong()); // key
//                checkDataValue(tempResult,i); // value
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    @Order(6)
//    public void check1000KeysSaved() throws Exception {
//        // now read back keys and check
//        ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES);
//        for (int i = 0; i < 1000; i++) {
//            long storedOffset = storedOffsets.get(i);
//            tempResult.clear();
//            // read
//            dataFile.readData(tempResult, storedOffset, DataFileReader.DataToRead.KEY);
//            // check all the data
//            tempResult.rewind();
//            assertEquals(i, tempResult.getLong()); // key
//        }
//    }
//
//    @Test
//    @Order(7)
//    public void check1000ValuesSaved() throws Exception {
//        // now read back values and check
//        ByteBuffer tempResult = ByteBuffer.allocate(MAX_DATA_SIZE);
//        for (int i = 0; i < 1000; i++) {
//            long storedOffset = storedOffsets.get(i);
//            tempResult.clear();
//            // read
//            dataFile.readData(tempResult, storedOffset, DataFileReader.DataToRead.VALUE);
//            // check all the data
//            tempResult.rewind();
//            checkDataValue(tempResult,i); // value
//        }
//    }
//
//    @Test
//    @Order(8)
//    public void check1000WithIterator() throws Exception {
//        var dataIter = dataFile.createIterator();
//        // now read back all the data and check all data
//        int count = 0;
//        long expectedOffset = 0;
//        while (dataIter.next()) {
//            assertEquals(count,dataIter.getDataItemsKey());
//
//            long dataLocation = dataIter.getDataItemsDataLocation();
//            long startByteOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
//            assertEquals(expectedOffset,startByteOffset);
//
//            // read
//            ByteBuffer blockBuffer = dataIter.getDataItemData();
//            // check all the data
//            int dataValueSize = blockBuffer.getInt();
//            expectedOffset += Integer.BYTES + DATA_ITEM_KEY_SIZE + dataValueSize;
//            assertEquals((count+1)*Integer.BYTES, dataValueSize); // data value size
//            assertEquals(count, blockBuffer.getLong()); // key
//            checkDataValue(blockBuffer,count); // value
//            count ++;
//        }
//        assertEquals(1000,count);
//        dataIter.close();
//    }
//
//    @Test
//    @Order(50)
//    public void closeAndReopen() throws Exception {
//        dataFile.close();
//        Path dataFilePath = dataFile.getPath();
//        dataFile = factoryForReaderToTest().newDataFileReader(dataFilePath);
//    }
//
//    @Test
//    @Order(51)
//    public void checkStateAfterReopen() throws Exception {
//        DataFileMetadata metadata = dataFile.getMetadata();
//        assertFalse(metadata.isMergeFile());
//        assertEquals(INDEX, metadata.getIndex());
//        assertTrue(metadata.getCreationDate().isAfter(TEST_START));
//        assertTrue(metadata.getCreationDate().isBefore(Instant.now()));
//        assertEquals(0, metadata.getMinimumValidKey());
//        assertEquals(1000, metadata.getMaximumValidKey());
//        assertEquals(1000, metadata.getDataItemCount());
//        assertEquals(0, dataFile.getSize() % DataFileCommon.PAGE_SIZE);
//    }
//
//    @Test
//    @Order(52)
//    public void check1000AfterReopen() throws Exception {
//        // now read back all the data and check all data
//        ByteBuffer tempResult = ByteBuffer.allocate(DATA_ITEM_KEY_SIZE + MAX_DATA_SIZE);
//        for (int i = 0; i < 1000; i++) {
//            long storedOffset = storedOffsets.get(i);
//            tempResult.clear();
//            // read
//            dataFile.readData(tempResult, storedOffset, DataFileReader.DataToRead.KEY_VALUE);
//            // check all the data
////                System.out.println(i+" *** "+toIntsString(tempResult));
//            tempResult.rewind();
//            assertEquals(i, tempResult.getLong()); // key
//            checkDataValue(tempResult,i); // value
//        }
//    }
//
//    @Test
//    @Order(100)
//    public void cleanup() throws Exception {
//        dataFile.close();
//        // clean up and delete files
//        deleteDirectoryAndContents(tempFileDir);
//    }

}