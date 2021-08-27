package com.hedera.services.state.jasperdb.files;

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.stream.IntStream;

import static com.hedera.services.state.jasperdb.HashTools.byteBufferToHash;
import static com.hedera.services.state.jasperdb.HashTools.hashToByteBuffer;
import static com.hedera.services.state.jasperdb.JasperDbTestUtils.hash;
import static com.hedera.services.state.jasperdb.files.DataFileCommon.KEY_SIZE;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("SameParameterValue")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataFileReaderCollectionFixedSizeMultiMergeDataTest extends DataFileReaderCollectionFixedSizeDataTest {

    @Test
    @Order(98)
    public void changeSomeData() throws Exception {
        fileCollection.startWriting();
        // put in 1000 items
        ByteBuffer tempData = ByteBuffer.allocate(DATA_ITEM_VALUE_SIZE);
        for (int i = 0; i < 50; i++) {
            int newI = i * 31;
            // prep data buffer
            tempData.clear();
            hashToByteBuffer(hash(newI), tempData);
            tempData.putInt(newI);
            tempData.flip();
            // store in file
            storedOffsets.set(i, fileCollection.storeData(i, tempData));
        }
        fileCollection.endWriting(0,100);
        // check we now have 11 files
        assertEquals(11,Files.list(tempFileDir).count());
    }

    @Test
    @Order(99)
    public void check1000BeforeMerge() throws Exception {
        check1000ImplNewData();
    }

    @Test
    @Order(100)
    public void merge() throws Exception {
        IntStream.range(0,5).parallel().forEach(thread -> {
            if (thread == 0) { // checking thread, keep reading and checking data all the time while we are merging
                while(!mergeComplete.get()) {
                    try {
                        check1000ImplNewData();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (thread == 1) { // move thread
                // merge 5 files
                try {
                    var allFiles = fileCollection.getAllFullyWrittenFiles(Integer.MAX_VALUE);
                    var filesToMerge = allFiles.subList(0,5);
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
                            }), filesToMerge);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mergeComplete.set(true);
            }
        });
        // check we 7 files left, as we merged 5 out of 11
        assertEquals(7,Files.list(tempFileDir).count());
    }

    @Test
    @Order(101)
    public void check1000AfterMerge() throws Exception {
        check1000ImplNewData();
    }

    protected static void check1000ImplNewData() throws Exception {
        // now read back all the data and check all data
        ByteBuffer tempResult = ByteBuffer.allocate(KEY_SIZE + DATA_ITEM_VALUE_SIZE);
        for (int i = 0; i < 1000; i++) {
            int newI = i < 50 ? i * 31 : i;
            long storedOffset = storedOffsets.get(i);
            tempResult.clear();
            // read
            fileCollection.readData(storedOffset,tempResult, DataFileReader.DataToRead.KEY_VALUE);
            // check all the data
            tempResult.rewind();
            assertEquals(i, tempResult.getLong()); // key
            Hash readHash = byteBufferToHash(tempResult);
            assertEquals(hash(newI), readHash); // hash
            assertEquals(newI, tempResult.getInt()); // value data
        }
    }
}
