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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.hedera.services.state.jasperdb.JasperDbTestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.jasperdb.files.DataFileCommon.DATA_ITEM_KEY_SIZE;
import static com.hedera.services.state.jasperdb.files.DataFileCommon.FOOTER_SIZE;
import static com.hedera.services.state.jasperdb.utilities.HashTools.HASH_SIZE_BYTES;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("SameParameterValue")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataFileReaderCollectionFixedSizeDataTest {
    protected static final Instant TEST_START = Instant.now();
    protected static final int DATA_ITEM_VALUE_SIZE = HASH_SIZE_BYTES + Integer.BYTES;
    protected static Path tempFileDir;
    protected static DataFileCollection<long[]> fileCollection;
    protected static List<Long> storedOffsets;
    protected static AtomicBoolean mergeComplete;

    @Test
    @Order(1)
    public void createDataFileCollection() throws Exception {
        storedOffsets = new CopyOnWriteArrayList<>();
        mergeComplete = new AtomicBoolean(false);
        // get non-existent temp file
        tempFileDir = Files.createTempDirectory("DataTest");
        System.out.println("tempFileDir.toAbsolutePath() = " + tempFileDir.toAbsolutePath());
        deleteDirectoryAndContents(tempFileDir);
        // create collection
        fileCollection = new DataFileCollection<>(tempFileDir, "TestDataStore",
                new ExampleFixedSizeDataSerializer(),
                null);
    }

    @Test
    @Order(2)
    public void create10x100ItemFiles() throws Exception {
        int count = 0;
        for (int f = 0; f < 10; f++) {
            fileCollection.startWriting();
            // put in 1000 items
            for (int i = count; i < count+100; i++) {
                // store in file
                storedOffsets.add(fileCollection.storeDataItem(new long[]{i,i}));
            }
            fileCollection.endWriting(0,count+100);
            count += 100;
        }
        // check 10 files were created
        assertEquals(10,Files.list(tempFileDir).count());
        Files.list(tempFileDir).forEach(file -> {
            try {
                assertEquals(calcFileSize(100), Files.size(file));
                System.out.println(file+" -- size="+Files.size(file));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    @Order(3)
    public void check1000() throws Exception {
        check1000Impl();
    }

    @Test
    @Order(4)
    public void checkFilesStates() throws Exception {
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
//            int wholePagesWritten = (1000* DATA_ITEM_VALUE_SIZE) / DataFileCommon.PAGE_SIZE;
            assertEquals(calcFileSize(100), dataFileReader.getSize());
        }
    }

    @Test
    @Order(50)
    public void closeAndReopen() throws Exception {
        fileCollection.close();
        fileCollection = new DataFileCollection<>(tempFileDir, "TestDataStore",
                new ExampleFixedSizeDataSerializer(),
                null);
    }

    @Test
    @Order(51)
    public void check1000AfterReopen() throws Exception {
        check1000Impl();
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
        assertEquals(1,Files.list(tempFileDir).count());
    }

    @Test
    @Order(101)
    public void check1000AfterMerge() throws Exception {
        check1000Impl();
    }


    @Test
    @Order(200)
    public void changeSomeData() throws Exception {
        fileCollection.startWriting();
        // put in 1000 items
        for (int i = 0; i < 50; i++) {
            int newI = i * 31;
            // store in file
            storedOffsets.set(i, fileCollection.storeDataItem(new long[]{i,newI}));
        }
        fileCollection.endWriting(0,100);
        // check we now have 11 files
        assertEquals(11,Files.list(tempFileDir).count());
    }

    @Test
    @Order(201)
    public void check1000BeforeMerge() throws Exception {
        check1000ImplNewData();
    }

    @Test
    @Order(202)
    public void merge2() throws Exception {
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
    @Order(103)
    public void check1000AfterMerge1() throws Exception {
        check1000ImplNewData();
    }

    @Test
    @Order(1000)
    public void cleanup() throws Exception {
        fileCollection.close();
        // clean up and delete files
        deleteDirectoryAndContents(tempFileDir);
    }

    protected static long calcFileSize(int numOfItems) {
        long dataWritten = (DATA_ITEM_KEY_SIZE + DATA_ITEM_VALUE_SIZE) * (long)numOfItems;
        int paddingBytesNeeded = (int)(DataFileCommon.PAGE_SIZE - (dataWritten % DataFileCommon.PAGE_SIZE));
        return dataWritten + paddingBytesNeeded + FOOTER_SIZE;
    }

    protected static void check1000Impl() throws Exception {
        // now read back all the data and check all data
        ByteBuffer tempResult = ByteBuffer.allocate(DATA_ITEM_KEY_SIZE + DATA_ITEM_VALUE_SIZE);
        for (int i = 0; i < 1000; i++) {
            long storedOffset = storedOffsets.get(i);
            tempResult.clear();
            // read
            final var dataItem = fileCollection.readDataItem(storedOffset);
            assertNotNull(dataItem);
            assertEquals(2, dataItem.length); // size
            assertEquals(i, dataItem[0]); // key
            assertEquals(i, dataItem[1]); // value
        }
    }

    protected static void check1000ImplNewData() throws Exception {
        // now read back all the data and check all data
        ByteBuffer tempResult = ByteBuffer.allocate(DATA_ITEM_KEY_SIZE + DATA_ITEM_VALUE_SIZE);
        for (int i = 0; i < 1000; i++) {
            int newI = i < 50 ? i * 31 : i;
            long storedOffset = storedOffsets.get(i);
            tempResult.clear();
            // read
            final var dataItem = fileCollection.readDataItem(storedOffset);
            assertNotNull(dataItem);
            assertEquals(2, dataItem.length); // size
            assertEquals(i, dataItem[0]); // key
            assertEquals(i*31, dataItem[1]); // value
        }
    }
}
