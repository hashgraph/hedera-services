package com.hedera.services.state.merkle.v3;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Random;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.v2.VFCDataSourceTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class VFCDataSourceImplV3Test {
    private static final Random RANDOM = new Random(1234);
    private static final Path STORE_PATH = Path.of("data-store");

    private static VFCDataSourceExceptionWrapper<LongVKeyImpl,TestLeafData> createDataSource() {
        return Assertions.assertDoesNotThrow(() ->
                new VFCDataSourceExceptionWrapper<>(
                    new VFCDataSourceImplV3<>(
                            LongVKeyImpl.SIZE_BYTES, LongVKeyImpl::new,
                            TestLeafData.SIZE_BYTES, TestLeafData::new,
                            STORE_PATH
                    )));
    }

    private static void closeAndDelete(VFCDataSourceExceptionWrapper<LongVKeyImpl,TestLeafData> dataSource) {
        dataSource.close();
        deleteDirectoryAndContents(STORE_PATH);
    }

    @Test
    public void createAndCheckNodeHashes() {
        var dataSource = createDataSource();
        // create some node hashes
        IntStream.range(0,1000).forEach(i -> dataSource.saveInternal(i,hash(i)));
        // check all the node hashes
        IntStream.range(0,1000).forEach(i -> assertEquals(hash(i), dataSource.loadInternalHash(i)));
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void testRandomHashUpdates() {
        var dataSource = createDataSource();
        // create some -1 hashes
        for (int i : shuffle(RANDOM,IntStream.range(0,1000).toArray())) dataSource.saveInternal(i,hash(i));
        // check all the node hashes
        IntStream.range(0,1000).forEach(i -> assertEquals(hash(i), dataSource.loadInternalHash(i)));
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void createAndCheckLeaves() {
        var dataSource = createDataSource();
        // create some leaves
        dataSource.startTransaction();
        IntStream.range(0,1000).forEach(i -> dataSource.addLeaf(i,new LongVKeyImpl(i),new TestLeafData(i),hash(i)));
        dataSource.commitTransaction(null);
        // check all the leaf data
        IntStream.range(0,1000).forEach(i -> assertLeaf(dataSource,i,i));
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void updateLeaves() {
        var dataSource = createDataSource();
        // create some leaves
        dataSource.startTransaction();
        IntStream.range(0,1000).forEach(i -> dataSource.addLeaf(i,new LongVKeyImpl(i),new TestLeafData(i),hash(i)));
        // random update
        for (int i : shuffle(RANDOM,IntStream.range(0,1000).toArray())) dataSource.updateLeaf(i,new LongVKeyImpl(i),new TestLeafData(i+10000),hash(i+10000));
        dataSource.commitTransaction(null);
        // check all the leaf data
        IntStream.range(0,1000).forEach(i -> {

            int j = i+10000;
            // things that should not have changed
            assertEquals(new LongVKeyImpl(i),       dataSource.loadLeafKey(i));
            assertEquals(i,                     dataSource.loadLeafPath(new LongVKeyImpl(i)));
            // things that should have changed
            assertEquals(new TestLeafData(j),   dataSource.loadLeafValue(new LongVKeyImpl(i)));
            assertEquals(new TestLeafData(j),   dataSource.loadLeafValue(i));
            assertEquals(hash(j),               dataSource.loadLeafHash(i));
        });
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void moveLeaf() {
        var dataSource = createDataSource();
        // create some leaves
        dataSource.startTransaction();
        IntStream.range(0,1000).forEach(i -> dataSource.addLeaf(i,new LongVKeyImpl(i),new TestLeafData(i),hash(i)));
        dataSource.commitTransaction(null);
        // check 250 and 500
        assertLeaf(dataSource,250,250);
        assertLeaf(dataSource, 500,500);
        // move a leaf from 500 to 250
        dataSource.startTransaction();
        dataSource.updateLeaf(500,250, new LongVKeyImpl(500), hash(500));
        dataSource.commitTransaction(null);
        // check 250 now has 500's data
        assertLeaf(dataSource,250,500);
        // close data source
        closeAndDelete(dataSource);
    }

    public void assertLeaf(VFCDataSourceExceptionWrapper<LongVKeyImpl,TestLeafData>  dataSource, long path, int i) {
        // things that should not have changed
        assertEquals(new LongVKeyImpl(i),       dataSource.loadLeafKey(path));
        assertEquals(path,                     dataSource.loadLeafPath(new LongVKeyImpl(i)));
        // things that should have changed
        assertEquals(new TestLeafData(i),   dataSource.loadLeafValue(new LongVKeyImpl(i)));
        assertEquals(new TestLeafData(i),   dataSource.loadLeafValue(path));
        assertEquals(hash(i),               dataSource.loadLeafHash(path));
    }
}
