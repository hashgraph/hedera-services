package com.hedera.services.state.merkle.v3.files;

import com.swirlds.common.crypto.DigestType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataFileTest {
    private static final int HASH_SIZE = 48+4;

    @Test
    public void createDataAndCheck() throws Exception {
        // get non-existent temp file
        Path tempFilePath = Files.createTempFile("DataFileTest",".data");
        Files.deleteIfExists(tempFilePath);
        // create data file
        DataFile dataFile = new DataFile(tempFilePath,1024,Long.BYTES,HASH_SIZE);
        // put in 1000 items
        ByteBuffer tempData = ByteBuffer.allocate(Integer.BYTES);
        List<Integer> storedOffsets = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // prep data buffer
            tempData.clear();
            tempData.putInt(i);
            tempData.flip();
            // store in file
            storedOffsets.add(dataFile.storeData(i,hash(i),tempData));
        }
        // now finish writing
        dataFile.finishWriting();
        // now read back all the data and check all data
        {
            ByteBuffer tempResult = ByteBuffer.allocate(HASH_SIZE + Long.BYTES + Integer.BYTES);
            for (int i = 0; i < 1000; i++) {
                int storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.HASH_KEY_VALUE);
                // check all the data
                tempResult.rewind();
                Hash readHash = Hash.fromByteBuffer(tempResult);
                assertEquals(hash(i), readHash); // hash
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getInt()); // value data
            }
        }
        // now read back all the data and check all data random order
        {
            ByteBuffer tempResult = ByteBuffer.allocate(HASH_SIZE + Long.BYTES + Integer.BYTES);
            List<Integer> randomOrderIndexes = IntStream.range(0,1000).boxed().collect(Collectors.toList());
            Collections.shuffle(randomOrderIndexes);
            for (int i : randomOrderIndexes) {
                int storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.HASH_KEY_VALUE);
                // check all the data
                tempResult.rewind();
                Hash readHash = Hash.fromByteBuffer(tempResult);
                assertEquals(hash(i), readHash); // hash
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getInt()); // value data
            }
        }
        // now read back hashes and check
        {
            ByteBuffer tempResult = ByteBuffer.allocate(HASH_SIZE);
            for (int i = 0; i < 1000; i++) {
                int storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.HASH);
                // check all the data
                tempResult.rewind();
                Hash readHash = Hash.fromByteBuffer(tempResult);
                assertEquals(hash(i), readHash); // hash
            }
        }
        // now read back keys and values and check
        {
            ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
            for (int i = 0; i < 1000; i++) {
                int storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.KEY_VALUE);
                // check all the data
                tempResult.rewind();
                assertEquals(i, tempResult.getLong()); // key
                assertEquals(i, tempResult.getInt()); // value data
            }
        }
        // now read back values and check
        {
            ByteBuffer tempResult = ByteBuffer.allocate(Integer.BYTES);
            for (int i = 0; i < 1000; i++) {
                int storedOffset = storedOffsets.get(i);
                tempResult.clear();
                // read
                dataFile.readData(tempResult, storedOffset, DataFile.DataToRead.VALUE);
                // check all the data
                tempResult.rewind();
                assertEquals(i, tempResult.getInt()); // value data
            }
        }
        // clean up and delete files
        Files.deleteIfExists(tempFilePath);
    }

    // =================================================================================================================
    // utils

    /**
     * Creates a hash containing a int repeated 6 times as longs
     *
     * @return byte array of 6 longs
     */
    public static Hash hash(int value) {
        byte b0 = (byte)(value >>> 24);
        byte b1 = (byte)(value >>> 16);
        byte b2 = (byte)(value >>> 8);
        byte b3 = (byte)value;
        return new TestHash(new byte[] {
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3
        });
    }

    public static final class TestHash extends Hash {
        public TestHash(byte[] bytes) {
            super(bytes, DigestType.SHA_384, true, false);
        }
    }


}
