package com.hedera.services.state.jasperdb;

import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import org.eclipse.collections.api.block.comparator.primitive.LongComparator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.v2.VFCDataSourceTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class VFCDataSourceJasperDBLongKeyTest {
    private static final Random RANDOM = new Random(1234);
    private static final Path STORE_PATH = Path.of("data-store");

    // =================================================================================================================
    // Tests

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 31, 128, 10_000, 1_000_000})
    public void createAndCheckInternalNodeHashes(int count) throws Exception {
        var dataSource = createDataSource(count);
        // create some node hashes
        dataSource.saveRecords(0,count,
                IntStream.range(0,count).mapToObj(this::createVirtualInternalRecord).collect(Collectors.toList()),
                Collections.emptyList()
        );
        // check all the node hashes
        IntStream.range(0,count).forEach(i -> assertEquals(hash(i), dataSource.loadInternalHash(i)));
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void testRandomHashUpdates() throws Exception {
        var dataSource = createDataSource(1000);
        // create some node hashes
        dataSource.saveRecords(0,1000,
                IntStream.range(0,1000).mapToObj(this::createVirtualInternalRecord).collect(Collectors.toList()),
                Collections.emptyList()
        );
        // create some *10 hashes
        int[] randomInts = shuffle(RANDOM,IntStream.range(0,1000).toArray());
        dataSource.saveRecords(0,1000,
                Arrays.stream(randomInts).mapToObj(i -> new VirtualInternalRecord(i,hash(i*10))).collect(Collectors.toList()),
                Collections.emptyList()
        );
        // check all the node hashes
        IntStream.range(0,1000).forEach(i -> assertEquals(hash(i*10), dataSource.loadInternalHash(i)));
        // close data source
        closeAndDelete(dataSource);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 31, 128, 10_000, 1_000_000})
    public void createAndCheckLeaves(int count) throws Exception {
        var dataSource = createDataSource(count);
        // create some leaves
        dataSource.saveRecords(0,count,
                Collections.emptyList(),
                IntStream.range(0,count).mapToObj(this::createVirtualLeafRecord).collect(Collectors.toList())
        );
        // check all the leaf data
        IntStream.range(0,count).forEach(i -> assertLeaf(dataSource,i,i));
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void updateLeaves() throws Exception {
        var dataSource = createDataSource(1000);
        // create some leaves
        dataSource.saveRecords(0,1000,
                Collections.emptyList(),
                IntStream.range(0,1000).mapToObj(this::createVirtualLeafRecord).collect(Collectors.toList())
        );
        // check all the leaf data
        IntStream.range(0,1000).forEach(i -> assertLeaf(dataSource,i,i));
        // update all to i+10,000 in a random order
        int[] randomInts = shuffle(RANDOM,IntStream.range(0,1000).toArray());
        dataSource.saveRecords(0,1000,
                Collections.emptyList(),
                Arrays
                        .stream(randomInts)
                        .mapToObj( i -> createVirtualLeafRecord(i, i,i+10_000))
                        .sorted(Comparator.comparingLong(record -> record.getPath()))
                        .collect(Collectors.toList())
        );
        assertEquals(createVirtualLeafRecord(100,100,100+10_000), createVirtualLeafRecord(100,100,100+10_000));
        // check all the leaf data
        IntStream.range(0,1000).forEach(i -> assertLeaf(dataSource,i,i,i+10_000));
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void moveLeaf() throws Exception {
        var dataSource = createDataSource(1000);
        // create some leaves
        dataSource.saveRecords(0,1000,
                Collections.emptyList(),
                IntStream.range(0,1000).mapToObj(this::createVirtualLeafRecord).collect(Collectors.toList())
        );
        // check 250 and 500
        assertLeaf(dataSource,250,250);
        assertLeaf(dataSource, 500,500);
        // move a leaf from 500 to 250, under new API there is no move as such so we just write 500 leaf at 250 path
        dataSource.saveRecords(0,1000,
                Collections.emptyList(),
                Collections.singletonList(new VirtualLeafRecord<>(250,hash(500),new LongVKeyImpl(500),new TestLeafData(500)))
        );
        // check 250 now has 500's data
        assertLeaf(dataSource, 700,700);
        assertEquals(createVirtualLeafRecord(500,500,500),dataSource.loadLeafRecord(500)); // TODO the old 500 path still has Leaf that is now 250, ok?
        assertLeaf(dataSource,250,500);
        // close data source
        closeAndDelete(dataSource);
    }
    
    // =================================================================================================================
    // Helper Methods

    @SuppressWarnings("rawtypes")
    private static void closeAndDelete(VFCDataSourceExceptionWrapper dataSource) {
        dataSource.close();
        deleteDirectoryAndContents(STORE_PATH);
    }

    private VirtualLeafRecord<LongVKeyImpl,TestLeafData> createVirtualLeafRecord(int i) {
        return createVirtualLeafRecord(i,i,i);
    }

    private VirtualLeafRecord<LongVKeyImpl,TestLeafData> createVirtualLeafRecord(long path, int i, int valueIndex) {
        return new VirtualLeafRecord<>(path,hash(i),new LongVKeyImpl(i), new TestLeafData(valueIndex));
    }

    private VirtualInternalRecord createVirtualInternalRecord(int i) {
        return new VirtualInternalRecord(i,hash(i));
    }

    private static VFCDataSourceExceptionWrapper<LongVKeyImpl,TestLeafData> createDataSource(int size) {
        return Assertions.assertDoesNotThrow(() ->
                new VFCDataSourceExceptionWrapper<>(
                        new VFCDataSourceJasperDB<>(
                                LongVKeyImpl.SIZE_BYTES, LongVKeyImpl::new,
                                TestLeafData.SIZE_BYTES, TestLeafData::new,
                                STORE_PATH,
                                size*10,
                                Long.MAX_VALUE)));
    }

    public void assertLeaf(VFCDataSourceExceptionWrapper<LongVKeyImpl,TestLeafData>  dataSource, long path, int i) {
        assertLeaf(dataSource,path,i,i);
    }

    public void assertLeaf(VFCDataSourceExceptionWrapper<LongVKeyImpl,TestLeafData>  dataSource, long path, int i, int valueIndex) {
        try {
            final var expectedRecord = createVirtualLeafRecord(path, i,valueIndex);
            // things that should have changed
            assertEqualsAndPrint(expectedRecord, dataSource.loadLeafRecord(new LongVKeyImpl(i)));
            assertEqualsAndPrint(expectedRecord, dataSource.loadLeafRecord(path));
            assertEquals(hash(i), dataSource.loadLeafHash(path));
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception should not have been thrown here!");
        }
    }

    @SuppressWarnings("rawtypes")
    public void assertEqualsAndPrint(VirtualLeafRecord recordA, VirtualLeafRecord recordB) {
        boolean equals = recordA.equals(recordB);
        if (!equals) {
            System.out.println("** assertEqualsAndPrint = " + equals);
            System  .out.println("   [Expected] VirtualLeafRecord{path=" + recordA.getPath() + ", key=" + recordA.getKey() + ", value=" + recordA.getValue() + ", hash="+recordA.getHash()+"}");
            System  .out.println("   [Actual]   VirtualLeafRecord{path=" + recordB.getPath() + ", key=" + recordB.getKey() + ", value=" + recordB.getValue() + ", hash="+recordB.getHash()+"}");
        }
        assertEquals(recordA,recordB);
    }
}
