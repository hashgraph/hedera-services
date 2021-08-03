package com.hedera.services.state.merkle.v3.files;

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.v3.V3TestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.merkle.v3.V3TestUtils.hash;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataFileTest {
    private static final int HASH_SIZE = 48+4;
    private static final int BLOCK_SIZE = 128;
    private static final int INDEX = 42;
    private static final long TEST_START = System.currentTimeMillis();
    private static Path tempFileDir;
    private static DataFile dataFile;
    private static List<Long> storedOffsets;

    @Test
    @Order(1)
    public void createDataFile() throws Exception {
        // get non-existent temp file
        tempFileDir = Files.createTempDirectory("DataFileTest");
        deleteDirectoryAndContents(tempFileDir);
        Files.createDirectories(tempFileDir);
        // create data file
        dataFile = new DataFile("TestFile", tempFileDir, INDEX, BLOCK_SIZE, false);
        assertFalse(dataFile.isReadOnly());
    }

    @Test
    @Order(2)
    public void put1000() throws Exception {
        // put in 1000 items
        ByteBuffer tempData = ByteBuffer.allocate(HASH_SIZE + Integer.BYTES);
        storedOffsets = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // prep data buffer
            tempData.clear();
            Hash.toByteBuffer(hash(i), tempData);
            tempData.putInt(i);
            tempData.flip();
//            System.out.println(i+" --- "+toIntsString(tempData));
            // store in file
            storedOffsets.add(dataFile.storeData(i, tempData));
        }
        // now finish writing
        dataFile.finishWriting(0, 1000);
//        hexDump(System.out,tempFilePath);
    }

    @Test
    @Order(3)
    public void checkState() throws Exception {
        assertFalse(dataFile.isMergeFile());
        assertTrue(dataFile.isReadOnly());
        assertEquals(INDEX, dataFile.getIndex());
        assertTrue(dataFile.getDate().getTime() > TEST_START);
        assertTrue(dataFile.getDate().getTime() < System.currentTimeMillis());
        assertEquals(0, dataFile.getMinimumValidKey());
        assertEquals(1000, dataFile.getMaximumValidKey());
        assertEquals(1000, dataFile.getDataItemCount());
        assertTrue(dataFile.getSize() % DataFile.PAGE_SIZE == 0);
        int wholePagesWritten = (1000*BLOCK_SIZE) / DataFile.PAGE_SIZE;
        assertEquals(((wholePagesWritten+1)*DataFile.PAGE_SIZE)+DataFile.FOOTER_SIZE, dataFile.getSize());
    }

    @Test
    @Order(4)
    public void check1000() throws Exception {
        // now read back all the data and check all data
        ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES + HASH_SIZE + Integer.BYTES);
        for (int i = 0; i < 1000; i++) {
            long storedOffset = storedOffsets.get(i);
            tempResult.clear();
            // read
            dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.KEY_VALUE);
            // check all the data
//                System.out.println(i+" *** "+toIntsString(tempResult));
            tempResult.rewind();
            assertEquals(i, tempResult.getLong()); // key
            Hash readHash = Hash.fromByteBuffer(tempResult);
            assertEquals(hash(i), readHash); // hash
            assertEquals(i, tempResult.getInt()); // value data
        }
    }

    @Test
    @Order(5)
    public void check1000RandomOrder() throws Exception {
        // now read back all the data and check all data random order
        ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES + HASH_SIZE + Integer.BYTES);
        List<Integer> randomOrderIndexes = IntStream.range(0,1000).boxed().collect(Collectors.toList());
        Collections.shuffle(randomOrderIndexes);
        for (int i : randomOrderIndexes) {
            long storedOffset = storedOffsets.get(i);
            tempResult.clear();
            // read
            dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.KEY_VALUE);
            // check all the data
            tempResult.rewind();
            assertEquals(i, tempResult.getLong()); // key
            Hash readHash = Hash.fromByteBuffer(tempResult);
            assertEquals(hash(i), readHash); // hash
            assertEquals(i, tempResult.getInt()); // value data
        }
    }

    @Test
    @Order(6)
    public void check1000KeysSaved() throws Exception {
        // now read back keys and check
        ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES);
        for (int i = 0; i < 1000; i++) {
            long storedOffset = storedOffsets.get(i);
            tempResult.clear();
            // read
            dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.KEY);
            // check all the data
            tempResult.rewind();
            assertEquals(i, tempResult.getLong()); // key
        }
    }

    @Test
    @Order(7)
    public void check1000ValuesSaved() throws Exception {
        // now read back values and check
        ByteBuffer tempResult = ByteBuffer.allocate(HASH_SIZE+Integer.BYTES);
        for (int i = 0; i < 1000; i++) {
            long storedOffset = storedOffsets.get(i);
            tempResult.clear();
            // read
            dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.VALUE);
            // check all the data
            tempResult.rewind();
            Hash readHash = Hash.fromByteBuffer(tempResult);
            assertEquals(hash(i), readHash); // hash
            assertEquals(i, tempResult.getInt()); // value data
        }
    }

    @Test
    @Order(8)
    public void check1000WithIterator() throws Exception {
        var dataIter = dataFile.createIterator();
        // now read back all the data and check all data
        int count = 0;
        while (dataIter.next()) {
            assertEquals(count,dataIter.getBlocksKey());

            long dataLocation = dataIter.getBlocksDataLocation();
            int startBlockOffset = (int)(dataLocation & 0x00000000ffffffffL);
            assertEquals(count,startBlockOffset);

            // read
            ByteBuffer blockBuffer = dataIter.getBlockData();
            blockBuffer.position(Integer.BYTES); // jump over value size
            // check all the data
            assertEquals(count, blockBuffer.getLong()); // key
            Hash readHash = Hash.fromByteBuffer(blockBuffer);
            assertEquals(hash(count), readHash); // hash
            assertEquals(count, blockBuffer.getInt()); // value data
            count ++;
        }
        assertEquals(1000,count);
        dataIter.close();
    }

    @Test
    @Order(50)
    public void closeAndReopen() throws Exception {
        dataFile.close();
        Path dataFilePath = dataFile.getPath();
        dataFile = new DataFile(dataFilePath);
    }

    @Test
    @Order(51)
    public void checkStateAfterReopen() throws Exception {
        assertFalse(dataFile.isMergeFile());
        assertTrue(dataFile.isReadOnly());
        assertEquals(INDEX, dataFile.getIndex());
        assertTrue(dataFile.getDate().getTime() > TEST_START);
        assertTrue(dataFile.getDate().getTime() < System.currentTimeMillis());
        assertEquals(0, dataFile.getMinimumValidKey());
        assertEquals(1000, dataFile.getMaximumValidKey());
        assertEquals(1000, dataFile.getDataItemCount());
        assertTrue(dataFile.getSize() % DataFile.PAGE_SIZE == 0);
        int wholePagesWritten = (1000*BLOCK_SIZE) / DataFile.PAGE_SIZE;
        assertEquals(((wholePagesWritten+1)*DataFile.PAGE_SIZE)+DataFile.FOOTER_SIZE, dataFile.getSize());
    }

    @Test
    @Order(52)
    public void check1000AfterReopen() throws Exception {
        // now read back all the data and check all data
        ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES + HASH_SIZE + Integer.BYTES);
        for (int i = 0; i < 1000; i++) {
            long storedOffset = storedOffsets.get(i);
            tempResult.clear();
            // read
            dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.KEY_VALUE);
            // check all the data
//                System.out.println(i+" *** "+toIntsString(tempResult));
            tempResult.rewind();
            assertEquals(i, tempResult.getLong()); // key
            Hash readHash = Hash.fromByteBuffer(tempResult);
            assertEquals(hash(i), readHash); // hash
            assertEquals(i, tempResult.getInt()); // value data
        }
    }

    @Test
    @Order(100)
    public void cleanup() throws Exception {
        dataFile.close();
        // clean up and delete files
        deleteDirectoryAndContents(tempFileDir);
    }

}


/*
package com.hedera.services.state.merkle.v3.files;

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.v3.V3TestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.merkle.v3.V3TestUtils.hash;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataFileTest {
    private static final int HASH_SIZE = 48+4;

    @Test
    public void createDataAndCheckLongKey() throws Exception {
        // get non-existent temp file
        Path tempFileDir = Files.createTempDirectory("DataFileTest");
        deleteDirectoryAndContents(tempFileDir);
        Files.createDirectories(tempFileDir);
        // create data file
        DataFile dataFile = new DataFile("TestFile",tempFileDir,0,128,false);
        // put in 1000 items
        ByteBuffer tempData = ByteBuffer.allocate(HASH_SIZE+Integer.BYTES);
        List<Long> storedOffsets = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // prep data buffer
            tempData.clear();
            Hash.toByteBuffer(hash(i),tempData);
            tempData.putInt(i);
            tempData.flip();
//            System.out.println(i+" --- "+toIntsString(tempData));
            // store in file
            storedOffsets.add(dataFile.storeData(i,tempData));
        }
        // now finish writing
        dataFile.finishWriting(0,1000);
//        hexDump(System.out,tempFilePath);
        // now read back all the data and check all data
        {
            ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES + HASH_SIZE + Integer.BYTES);
            for (int i = 0; i < 1000; i++) {
                long storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.KEY_VALUE);
                // check all the data
//                System.out.println(i+" *** "+toIntsString(tempResult));
                tempResult.rewind();
                assertEquals(i, tempResult.getLong()); // key
                Hash readHash = Hash.fromByteBuffer(tempResult);
                assertEquals(hash(i), readHash); // hash
                assertEquals(i, tempResult.getInt()); // value data
            }
        }
        // now read back all the data and check all data random order
        {
            ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES + HASH_SIZE + Integer.BYTES);
            List<Integer> randomOrderIndexes = IntStream.range(0,1000).boxed().collect(Collectors.toList());
            Collections.shuffle(randomOrderIndexes);
            for (int i : randomOrderIndexes) {
                long storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.KEY_VALUE);
                // check all the data
                tempResult.rewind();
                assertEquals(i, tempResult.getLong()); // key
                Hash readHash = Hash.fromByteBuffer(tempResult);
                assertEquals(hash(i), readHash); // hash
                assertEquals(i, tempResult.getInt()); // value data
            }
        }
        // now read back keys and check
        {
            ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES);
            for (int i = 0; i < 1000; i++) {
                long storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.KEY);
                // check all the data
                tempResult.rewind();
                assertEquals(i, tempResult.getLong()); // key
            }
        }
        // now read back values and check
        {
            ByteBuffer tempResult = ByteBuffer.allocate(HASH_SIZE+Integer.BYTES);
            for (int i = 0; i < 1000; i++) {
                long storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.VALUE);
                // check all the data
                tempResult.rewind();
                Hash readHash = Hash.fromByteBuffer(tempResult);
                assertEquals(hash(i), readHash); // hash
                assertEquals(i, tempResult.getInt()); // value data
            }
        }
        // clean up and delete files
        deleteDirectoryAndContents(tempFileDir);
    }

    // =================================================================================================================
    // utils

}


 */