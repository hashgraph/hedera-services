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

import static com.hedera.services.state.merkle.v3.V3TestUtils.hash;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataFileTest {
    private static final int HASH_SIZE = 48+4;

    @Test
    public void createDataAndCheckLongKey() throws Exception {
        // get non-existent temp file
        Path tempFilePath = Files.createTempFile("DataFileTest",".data");
        Files.deleteIfExists(tempFilePath);
        // create data file
        DataFile dataFile = new DataFile(tempFilePath,128,Long.BYTES);
        // put in 1000 items
        ByteBuffer tempData = ByteBuffer.allocate(HASH_SIZE+Integer.BYTES);
        List<Integer> storedOffsets = new ArrayList<>();
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
        dataFile.finishWriting();
//        hexDump(System.out,tempFilePath);
        // now read back all the data and check all data
        {
            ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES + HASH_SIZE + Integer.BYTES);
            for (int i = 0; i < 1000; i++) {
                int storedOffset = storedOffsets.get(i);
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
                int storedOffset = storedOffsets.get(i);
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
                int storedOffset = storedOffsets.get(i);
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
                int storedOffset = storedOffsets.get(i);
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
        Files.deleteIfExists(tempFilePath);
    }

    @Test
    public void createDataAndCheckCustomKey() throws Exception {
        // get non-existent temp file
        Path tempFilePath = Files.createTempFile("DataFileTest",".data");
        Files.deleteIfExists(tempFilePath);
        // create data file
        int keySize = Long.BYTES*5;
        DataFile dataFile = new DataFile(tempFilePath,1024,keySize);
        // put in 1000 items
        ByteBuffer tempKey = ByteBuffer.allocate(keySize);
        ByteBuffer tempData = ByteBuffer.allocate(HASH_SIZE+Integer.BYTES);
        List<Integer> storedOffsets = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // prep key buffer
            tempKey.clear();
            tempKey.putLong(i);
            tempKey.putLong(i);
            tempKey.putLong(i);
            tempKey.putLong(i);
            tempKey.putLong(i);
            tempKey.flip();
            // prep data buffer
            tempData.clear();
            Hash.toByteBuffer(hash(i),tempData);
            tempData.putInt(i);
            tempData.flip();
            // store in file
            storedOffsets.add(dataFile.storeData(tempKey,tempData));
        }
        // now finish writing
        dataFile.finishWriting();
        // now read back all the data and check all data
        {
            ByteBuffer tempResult = ByteBuffer.allocate(keySize + HASH_SIZE + Integer.BYTES);
            for (int i = 0; i < 1000; i++) {
                int storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.KEY_VALUE);
                // check all the data
                tempResult.rewind();
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
                Hash readHash = Hash.fromByteBuffer(tempResult);
                assertEquals(hash(i), readHash); // hash
                assertEquals(i, tempResult.getInt()); // value data
            }
        }
        // now read back all the data and check all data random order
        {
            ByteBuffer tempResult = ByteBuffer.allocate(keySize + HASH_SIZE + Integer.BYTES);
            List<Integer> randomOrderIndexes = IntStream.range(0,1000).boxed().collect(Collectors.toList());
            Collections.shuffle(randomOrderIndexes);
            for (int i : randomOrderIndexes) {
                int storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.KEY_VALUE);
                // check all the data
                tempResult.rewind();
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
                Hash readHash = Hash.fromByteBuffer(tempResult);
                assertEquals(hash(i), readHash); // hash
                assertEquals(i, tempResult.getInt()); // value data
            }
        }
        // now read back keys and check
        {
            ByteBuffer tempResult = ByteBuffer.allocate(keySize);
            for (int i = 0; i < 1000; i++) {
                int storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.KEY);
                // check all the data
                tempResult.rewind();
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getLong()); // key
            }
        }
        // now read back values and check
        {
            ByteBuffer tempResult = ByteBuffer.allocate(HASH_SIZE+Integer.BYTES);
            for (int i = 0; i < 1000; i++) {
                int storedOffset = storedOffsets.get(i);
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
        Files.deleteIfExists(tempFilePath);
    }

    // =================================================================================================================
    // utils

}
