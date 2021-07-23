package com.hedera.services.state.merkle.v3.files;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static com.hedera.services.state.merkle.v3.V3TestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.merkle.v3.V3TestUtils.hash;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MemoryIndexDiskKeyValueStoreTest {
    private static final int HASH_SIZE = 48+4;
    @Test
    public void createDataAndCheck() throws Exception {
        // lets store hashes as easy test class
        Path tempDir = Files.createTempDirectory("DataFileTest");
        MemoryIndexDiskKeyValueStore store = new MemoryIndexDiskKeyValueStore(tempDir,"MemoryIndexDiskKeyValueStoreTest",1024);
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
        { // check key only
            ByteBuffer buf = ByteBuffer.allocate(Long.BYTES);
            for (int i = start; i < (start + count); i++) {
                // read value only
                buf.clear();
                store.get(i, buf, DataFile.DataToRead.KEY);
                // check read key
                assertEquals(i,buf.getLong());
            }
        }
        { // check hash data value only
            ByteBuffer buf = ByteBuffer.allocate(HASH_SIZE);
            for (int i = start; i < (start + count); i++) {
                // read value only
                buf.clear();
                store.get(i, buf, DataFile.DataToRead.VALUE);
                // check read hash
                assertEquals(hash(i), Hash.fromByteBuffer(buf));
            }
        }
        { // check key and value
            ByteBuffer buf = ByteBuffer.allocate(Long.BYTES+HASH_SIZE);
            for (int i = start; i < (start + count); i++) {
                // read value only
                buf.clear();
                store.get(i, buf, DataFile.DataToRead.KEY_VALUE);
                // check read key
                assertEquals(i,buf.getLong(),"Failed to read key for i="+i);
                // check read hash
                assertEquals(hash(i), Hash.fromByteBuffer(buf));
            }
        }
    }

    public void writeBatch(MemoryIndexDiskKeyValueStore store,int start, int count) throws IOException {
        store.startWriting();
        ByteBuffer buf = ByteBuffer.allocate(HASH_SIZE);
        for (int i = start; i < (start+count); i++) {
            buf.clear();
            Hash.toByteBuffer(hash(i),buf);
            buf.flip();
            store.put(i,buf);
        }
        store.endWriting();
    }
}
