package com.hedera.services.state.jasperdb.files;

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.hedera.services.state.jasperdb.HashTools.*;
import static com.hedera.services.state.jasperdb.JasperDbTestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.jasperdb.JasperDbTestUtils.hash;
import static com.hedera.services.state.jasperdb.files.DataFileCommon.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("SameParameterValue")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataFileIteratorTest {
    protected static final Instant TEST_START = Instant.now();
    protected static Path tempFileDir;
    protected static List<Long> storedOffsetsVariable;
    protected static List<Long> storedOffsetsFixed;

    protected static DataFileMetadata variableSizesFileMetadata;
    protected static DataFileMetadata fixedSizesFileMetadata;
    protected static Path variableSizesFilePath;
    protected static Path fixedSizesFilePath;

    public DataFileIterator createIterator(Path path, DataFileMetadata metadata) {
        return new DataFileIteratorSingleBuffered(path, metadata);
    }

    @Test
    @Order(1)
    public void createData() throws Exception {
        System.out.println("DataFileIteratorTest.createData");
        storedOffsetsVariable = new CopyOnWriteArrayList<>();
        storedOffsetsFixed = new CopyOnWriteArrayList<>();
        // get non-existent temp file
        tempFileDir = Files.createTempDirectory("DataTest");
        System.out.println("tempFileDir.toAbsolutePath() = " + tempFileDir.toAbsolutePath());
        deleteDirectoryAndContents(tempFileDir);
        Files.createDirectories(tempFileDir);
        // create variableSize data
        DataFileWriter writer = new DataFileWriter("DataFileIteratorTestVariable",tempFileDir,1,-1,
                Instant.now(),false);
        variableSizesFilePath = writer.getPath();
        for(int i = 0; i <= 20; i++) {
            byte[] dataBytes = Long.toString((long)Math.pow(10,i)).getBytes(StandardCharsets.UTF_8);
            storedOffsetsVariable.add(
                    writer.storeData(i, ByteBuffer.wrap(dataBytes)));
        }
        variableSizesFileMetadata = writer.finishWriting(0, 20);
        // create fixedSize data
        writer = new DataFileWriter("DataFileIteratorTestFixed",tempFileDir,1,8,
                Instant.now(),false);
        fixedSizesFilePath = writer.getPath();
        for(int i = 0; i <= 20; i++) {
            byte[] dataBytes = String.format("%8d", i).getBytes(StandardCharsets.UTF_8);
            assert dataBytes.length == 8;
            storedOffsetsFixed.add(
                    writer.storeData(i, ByteBuffer.wrap(dataBytes)));
        }
        fixedSizesFileMetadata = writer.finishWriting(0, 20);

    }

    @Test
    @Order(2)
    public void checkVariableSizeData() throws Exception {
        var iter = createIterator(variableSizesFilePath, variableSizesFileMetadata);
        int i = 0;
        while(iter.next()) {
            // check key
            assertEquals(iter.getDataItemsKey(),i);
            // check value
            byte[] dataBytes = Long.toString((long)Math.pow(10,i)).getBytes(StandardCharsets.UTF_8);
            var buf  = iter.getDataItemData();
            System.out.println("buf.remaining() = " + buf.remaining());
            assertEquals(dataBytes.length, buf.remaining() - KEY_SIZE - SIZE_OF_DATA_ITEM_SIZE_IN_FILE);
            byte[] valueBytes = new byte[dataBytes.length];
            buf.position(buf.position()+KEY_SIZE+SIZE_OF_DATA_ITEM_SIZE_IN_FILE);
            buf.get(valueBytes);
            assertEqualsByteArrays(dataBytes,valueBytes);
            // check location
            assertEquals(storedOffsetsVariable.get(i),iter.getDataItemsDataLocation());
            // next i
            i++;
        }
    }

    @Test
    @Order(3)
    public void checkFixedSizeData() throws Exception {
        var iter = createIterator(fixedSizesFilePath, fixedSizesFileMetadata);
        int i = 0;
        while(iter.next()) {
            // check key
            assertEquals(iter.getDataItemsKey(),i);
            // check value
            byte[] dataBytes = String.format("%8d", i).getBytes(StandardCharsets.UTF_8);
            var buf  = iter.getDataItemData();
            assertEquals(dataBytes.length, buf.remaining() - KEY_SIZE);
            byte[] valueBytes = new byte[dataBytes.length];
            buf.position(buf.position()+KEY_SIZE);
            buf.get(valueBytes);
            assertEqualsByteArrays(dataBytes,valueBytes);
            // check location
            assertEquals(storedOffsetsFixed.get(i),iter.getDataItemsDataLocation());
            // next i
            i++;
        }
    }

    public static void assertEqualsByteArrays(byte[] expected, byte[] value) {
        assertEquals(bytesToHex(expected), bytesToHex(value));
    }

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }
}
