package com.hedera.services.state.merkle.v2;

import com.hedera.services.state.merkle.v2.persistance.LongIndexMemMap;
import com.hedera.services.state.merkle.v2.persistance.SlotStoreMemMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.v2.VFCDataSourceTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class VFCDataSourceTest {
    private static final Random RANDOM = new Random(1234);

    private static VFCDataSourceExceptionWrapper<LongVKey,TestLeafData> createDataSource() {
        try {
            int fileSize = 1024*1024*5; // 5Mb
            Path store = Path.of("data-store");
//            final var nodeStore = new SlotStoreInMemory(false, VFCDataSourceImpl.NODE_STORE_SLOTS_SIZE);
//            final var leafStore = new SlotStoreInMemory(true,Integer.BYTES + LongVKey.SIZE_BYTES + Integer.BYTES + TestLeafData.SIZE_BYTES);
//            final var index = new LongIndexInMemory<>();

            final var nodeStore = new SlotStoreMemMap(false, VFCDataSourceImpl.NODE_STORE_SLOTS_SIZE, fileSize,store,"nodes","dat",false);
            final var leafStore = new SlotStoreMemMap(true,Integer.BYTES + LongVKey.SIZE_BYTES + Integer.BYTES + TestLeafData.SIZE_BYTES,
                    fileSize,store,"leaves","dat",false);
            final var index = new LongIndexMemMap<LongVKey>(store, "leaf-index",512,4,LongVKey.SIZE_BYTES,4);
            return new VFCDataSourceExceptionWrapper<>(
                    new VFCDataSourceImpl<>(
                            LongVKey.SIZE_BYTES, LongVKey::new,
                            TestLeafData.SIZE_BYTES, TestLeafData::new,
                            nodeStore,leafStore,index
                    ));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Test
    public void createAndCheckNodeHashes() {
        var dataSource = createDataSource();
        // create some node hashes
        IntStream.range(0,1000).forEach(i -> dataSource.saveInternal(i,hash(i)));
        // check all the node hashes
        IntStream.range(0,1000).forEach(i -> assertEquals(hash(i), dataSource.loadHash(i)));
        // close data source
        dataSource.close();
    }

    @Test
    public void testRandomHashUpdates() {
        var dataSource = createDataSource();
        // create some -1 hashes
        for (int i : shuffle(RANDOM,IntStream.range(0,1000).toArray())) dataSource.saveInternal(i,hash(i));
        // check all the node hashes
        IntStream.range(0,1000).forEach(i -> assertEquals(hash(i), dataSource.loadHash(i)));
        // close data source
        dataSource.close();
    }

    @Test
    public void createAndCheckLeaves() {
        var dataSource = createDataSource();
        // create some leaves
        IntStream.range(0,1000).forEach(i -> dataSource.addLeaf(i,new LongVKey(i),new TestLeafData(i),hash(i)));
        // check all the leaf data
        IntStream.range(0,1000).forEach(i -> assertLeaf(dataSource,i,i));
        // close data source
        dataSource.close();
    }

    @Test
    public void updateLeaves() {
        var dataSource = createDataSource();
        // create some leaves
        IntStream.range(0,1000).forEach(i -> dataSource.addLeaf(i,new LongVKey(i),new TestLeafData(i),hash(i)));
        // random update
        for (int i : shuffle(RANDOM,IntStream.range(0,1000).toArray())) dataSource.updateLeaf(i,new TestLeafData(i+10000),hash(i+10000));
        // check all the leaf data
        IntStream.range(0,1000).forEach(i -> {

            int j = i+10000;
            // things that should not have changed
            assertEquals(new LongVKey(i),       dataSource.loadLeafKey(i));
            assertEquals(i,                     dataSource.loadLeafPath(new LongVKey(i)));
            // things that should have changed
            assertEquals(new TestLeafData(j),   dataSource.loadLeafValue(new LongVKey(i)));
            assertEquals(new TestLeafData(j),   dataSource.loadLeafValue(i));
            assertEquals(hash(j),               dataSource.loadHash(i));
        });
        // close data source
        dataSource.close();
    }

    @Test
    public void moveLeaf() {
        var dataSource = createDataSource();
        // create some leaves
        IntStream.range(0,1000).forEach(i -> dataSource.addLeaf(i,new LongVKey(i),new TestLeafData(i),hash(i)));
        // check 250 and 500
        assertLeaf(dataSource,250,250);
        assertLeaf(dataSource, 500,500);
        // move a leaf from 500 to 250
        dataSource.updateLeaf(500,250,new LongVKey(500));
        // check 250 now has 500's data
        assertLeaf(dataSource,250,500);
        // close data source
        dataSource.close();
    }

    public void assertLeaf(VFCDataSourceExceptionWrapper<LongVKey,TestLeafData>  dataSource, long path, int i) {
        // things that should not have changed
        assertEquals(new LongVKey(i),       dataSource.loadLeafKey(path));
        assertEquals(path,                     dataSource.loadLeafPath(new LongVKey(i)));
        // things that should have changed
        assertEquals(new TestLeafData(i),   dataSource.loadLeafValue(new LongVKey(i)));
        assertEquals(new TestLeafData(i),   dataSource.loadLeafValue(path));
        assertEquals(hash(i),               dataSource.loadHash(path));
    }
}
