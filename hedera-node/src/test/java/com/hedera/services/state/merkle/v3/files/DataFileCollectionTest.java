package com.hedera.services.state.merkle.v3.files;

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.v3.V3TestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.merkle.v3.V3TestUtils.hash;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataFileCollectionTest {
    private static final int HASH_SIZE = 48+4;
    private static final int BLOCK_SIZE = 128;
    private static Path tempFileDir;
    private static DataFileCollection fileCollection;
    private static final List<Long> storedOffsets = new CopyOnWriteArrayList<>();
    private static AtomicBoolean mergeComplete = new AtomicBoolean(false);

    @Test
    @Order(1)
    public void createDataFileCollection() throws Exception {
        // get non-existent temp file
        tempFileDir = Files.createTempDirectory("DataFileTest");
        deleteDirectoryAndContents(tempFileDir);
        // create collection
        fileCollection = new DataFileCollection(tempFileDir,"TestDataStore",BLOCK_SIZE);
    }

    @Test
    @Order(2)
    public void create10x100ItemFiles() throws Exception {
        int count = 0;
        for (int f = 0; f < 10; f++) {
            fileCollection.startWriting();
            // put in 1000 items
            ByteBuffer tempData = ByteBuffer.allocate(HASH_SIZE + Integer.BYTES);
            for (int i = count; i < count+100; i++) {
                // prep data buffer
                tempData.clear();
                Hash.toByteBuffer(hash(i), tempData);
                tempData.putInt(i);
                tempData.flip();
                // store in file
                storedOffsets.add(fileCollection.storeData(i, tempData));
            }
            fileCollection.endWriting(count,count+100);
            count += 100;
        }
        // check 10 files were created
        assertEquals(10,Files.list(tempFileDir).count());
    }

    @Test
    @Order(3)
    public void check1000() throws Exception {
        check1000Impl();
    }

    @Test
    @Order(50)
    public void closeAndReopen() throws Exception {
        fileCollection.close();
        fileCollection = new DataFileCollection(tempFileDir,"TestDataStore",BLOCK_SIZE);
    }

    @Test
    @Order(51)
    public void check1000AfterReopen() throws Exception {
        // now read back all the data and check all data
        ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES + HASH_SIZE + Integer.BYTES);
        for (int i = 0; i < 1000; i++) {
            long storedOffset = storedOffsets.get(i);
            tempResult.clear();
            // read
            fileCollection.readData(storedOffset,tempResult, DataFile.DataToRead.KEY_VALUE);
            // check all the data
            tempResult.rewind();
            assertEquals(i, tempResult.getLong()); // key
            Hash readHash = Hash.fromByteBuffer(tempResult);
            assertEquals(hash(i), readHash); // hash
            assertEquals(i, tempResult.getInt()); // value data
        }
    }

    @Test
    @Order(100)
    public void merge() throws Exception {
        IntStream.range(0,2).parallel().forEach(thread -> {
            if (thread == 0) { // checking thread, keep reading and checking data all the time while we are merging
                while(!mergeComplete.get()) {
                    try {
                        check1000Impl();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (thread == 1) { // move thread
                try {
                    fileCollection.mergeOldFiles(moves -> {
                        assertEquals(1000,moves.size());
                        for(long[] move: moves) {
                            int index = storedOffsets.indexOf(move[0]);
                            storedOffsets.set(index, move[1]);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mergeComplete.set(true);
            }
        });
        // check we only have 1 file left
        assertEquals(1,Files.list(tempFileDir).count());
    }

    @Test
    @Order(101)
    public void check1000AfterMerge() throws Exception {
        check1000Impl();
    }

    @Test
    @Order(1000)
    public void cleanup() throws Exception {
        fileCollection.close();
        // clean up and delete files
        deleteDirectoryAndContents(tempFileDir);
    }


    private static void check1000Impl() throws Exception {

        // now read back all the data and check all data
        ByteBuffer tempResult = ByteBuffer.allocate(Long.BYTES + HASH_SIZE + Integer.BYTES);
        for (int i = 0; i < 1000; i++) {
            long storedOffset = storedOffsets.get(i);
            tempResult.clear();
            // read
            fileCollection.readData(storedOffset,tempResult, DataFile.DataToRead.KEY_VALUE);
            // check all the data
            tempResult.rewind();
            assertEquals(i, tempResult.getLong()); // key
            Hash readHash = Hash.fromByteBuffer(tempResult);
            assertEquals(hash(i), readHash); // hash
            assertEquals(i, tempResult.getInt()); // value data
        }
    }
}
