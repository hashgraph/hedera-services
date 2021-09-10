package com.hedera.services.state.jasperdb.collections;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.hedera.services.state.jasperdb.utilities.HashTools.*;
import static com.hedera.services.state.jasperdb.JasperDbTestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.jasperdb.JasperDbTestUtils.hash;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MemoryIndexDiskKeyValueStoreTest {
    @Test
    public void createDataAndCheck() throws Exception {
        // let's store hashes as easy test class
        Path tempDir = Files.createTempDirectory("DataFileTest");
        MemoryIndexDiskKeyValueStore store = new MemoryIndexDiskKeyValueStore(tempDir,"MemoryIndexDiskKeyValueStoreTest",HASH_SIZE_BYTES, null);
        // write some batches of data, then check all the contents, we should end up with 3 files
        writeBatch(store,0,1000);
        checkRange(store,0,1000);
        writeBatch(store,1000,1500);
        checkRange(store,0,1500);
        writeBatch(store,1500,2000);
        checkRange(store,0,2000);
        // check number of files created
        assertEquals(3,Files.list(tempDir).count());
        // clean up and delete files
        deleteDirectoryAndContents(tempDir);
    }

    public void checkRange(MemoryIndexDiskKeyValueStore store,int start, int count) throws IOException {
        { // check hash data value only
            ByteBuffer buf = ByteBuffer.allocate(HASH_SIZE_BYTES);
            for (int i = start; i < (start + count); i++) {
                // read value only
                buf.clear();
                store.get(i, buf);
                // check read hash
                assertEquals(hash(i), byteBufferToHash(buf));
            }
        }
    }

    public void writeBatch(MemoryIndexDiskKeyValueStore store,int start, int count) throws IOException {
        store.startWriting();
        ByteBuffer buf = ByteBuffer.allocate(HASH_SIZE_BYTES);
        for (int i = start; i < (start+count); i++) {
            buf.clear();
            hashToByteBuffer(hash(i),buf);
            buf.flip();
            store.put(i,buf);
        }
        store.endWriting(0, Integer.MAX_VALUE);
    }
}
