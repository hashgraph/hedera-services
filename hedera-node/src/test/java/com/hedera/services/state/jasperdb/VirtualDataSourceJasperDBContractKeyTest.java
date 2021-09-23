package com.hedera.services.state.jasperdb;

import com.hedera.services.state.jasperdb.files.DataFileCommon;
import com.hedera.services.state.merkle.virtual.ContractKey;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.state.jasperdb.JasperDbTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VirtualDataSourceJasperDBContractKeyTest {
    private static final Random RANDOM = new Random(1234);
    private static final Path STORE_PATH = Path.of("data-store");
//
//    // =================================================================================================================
//    // Tests
//
//    @ParameterizedTest
//    @ValueSource(ints = {1, 10, 31, 128, 10_000, 1_000_000})
//    @Order(1)
//    public void createAndCheckInternalNodeHashes(int count) {
//        var dataSource = createDataSource(count);
//        // create some node hashes
//        dataSource.saveRecords(0,count,
//                IntStream.range(0,count).mapToObj(this::createVirtualInternalRecord),
//                Stream.empty()
//        );
//        // check all the node hashes
//        IntStream.range(0,count).forEach(i -> assertEquals(hash(i), dataSource.loadInternalRecord(i).getHash()));
//        // close data source
//        closeAndDelete(dataSource);
//    }
//
//    @Test
//    @Order(2)
//    public void testRandomHashUpdates() {
//        var dataSource = createDataSource(1000);
//        // create some node hashes
//        dataSource.saveRecords(0,1000,
//                IntStream.range(0,1000).mapToObj(this::createVirtualInternalRecord),
//                Stream.empty()
//        );
//        // create some *10 hashes
//        int[] randomInts = shuffle(RANDOM,IntStream.range(0,1000).toArray());
//        dataSource.saveRecords(0,1000,
//                Arrays.stream(randomInts).mapToObj(i -> new VirtualInternalRecord(i,hash(i*10))),
//                Stream.empty()
//        );
//        // check all the node hashes
//        IntStream.range(0,1000).forEach(i -> assertEquals(hash(i*10), dataSource.loadInternalRecord(i).getHash()));
//        // close data source
//        closeAndDelete(dataSource);
//    }
//
//    @ParameterizedTest
//    @ValueSource(ints = {1, 10, 31, 128, 10_000, 1_000_000})
//    @Order(3)
//    public void createAndCheckLeaves(int count) {
//        var dataSource = createDataSource(count);
//        // create some leaves
//        dataSource.saveRecords(0,count,
//                Stream.empty(),
//                IntStream.range(0,count).mapToObj(this::createVirtualLeafRecord)
//        );
//        // check all the leaf data
//        IntStream.range(0,count).forEach(i -> assertLeaf(dataSource,i,i));
//        // close data source
//        closeAndDelete(dataSource);
//    }
//
//    @Test
//    @Order(4)
//    public void updateLeaves() {
//        var dataSource = createDataSource(1000);
//        // create some leaves
//        dataSource.saveRecords(0,1000,
//                Stream.empty(),
//                IntStream.range(0,1000).mapToObj(this::createVirtualLeafRecord)
//        );
//        // check all the leaf data
//        IntStream.range(0,1000).forEach(i -> assertLeaf(dataSource,i,i));
//        // update all to i+10,000 in a random order
//        int[] randomInts = shuffle(RANDOM,IntStream.range(0,1000).toArray());
//        dataSource.saveRecords(0,1000,
//                Stream.empty(),
//                Arrays
//                        .stream(randomInts)
//                        .mapToObj(i -> createVirtualLeafRecord(i, i,i+10_000))
//                        .sorted(Comparator.comparingLong(VirtualRecord::getPath))
//        );
//        assertEquals(createVirtualLeafRecord(100,100,100+10_000), createVirtualLeafRecord(100,100,100+10_000));
//        // check all the leaf data
//        IntStream.range(0,1000).forEach(i -> assertLeaf(dataSource,i,i,i+10_000));
//        // close data source
//        closeAndDelete(dataSource);
//    }
//
//    @Test
//    @Order(5)
//    public void moveLeaf() {
//        var dataSource = createDataSource(1000);
//        // create some leaves
//        dataSource.saveRecords(0,1000,
//                Stream.empty(),
//                IntStream.range(0,1000).mapToObj(this::createVirtualLeafRecord)
//        );
//        // check 250 and 500
//        assertLeaf(dataSource,250,250);
//        assertLeaf(dataSource, 500,500);
//        // move a leaf from 500 to 250, under new API there is no move as such so we just write 500 leaf at 250 path
//        dataSource.saveRecords(0,1000,
//                Stream.empty(),
//                Stream.of(new VirtualLeafRecord<>(250,hash(500),newContractKey(500),new TestLeafData(500)))
//        );
//        // check 250 now has 500's data
//        assertLeaf(dataSource, 700,700);
//        assertEquals(createVirtualLeafRecord(500,500,500),dataSource.loadLeafRecord(500)); // TODO the old 500 path still has Leaf that is now 250, ok?
//        assertLeaf(dataSource,250,500);
//        // close data source
//        closeAndDelete(dataSource);
//    }
//
//    // =================================================================================================================
//    // Helper Methods
//
//    @SuppressWarnings("rawtypes")
//    private static void closeAndDelete(VFCDataSourceExceptionWrapper dataSource) {
//        dataSource.close();
//        deleteDirectoryAndContents(STORE_PATH);
//    }
//
//    private VirtualLeafRecord<ContractKey,TestLeafData> createVirtualLeafRecord(int i) {
//        return createVirtualLeafRecord(i,i,i);
//    }
//
//    private VirtualLeafRecord<ContractKey,TestLeafData> createVirtualLeafRecord(long path, int i, int valueIndex) {
//        return new VirtualLeafRecord<>(path,hash(i),newContractKey(i), new TestLeafData(valueIndex));
//    }
//
//    private VirtualInternalRecord createVirtualInternalRecord(int i) {
//        return new VirtualInternalRecord(i,hash(i));
//    }
//
//    private static VFCDataSourceExceptionWrapper<ContractKey,TestLeafData> createDataSource(int size) {
//        return Assertions.assertDoesNotThrow(() ->
//                new VFCDataSourceExceptionWrapper<>(
//                        new VirtualDataSourceJasperDB<>(
//                                DataFileCommon.VARIABLE_DATA_SIZE,
//                                ContractKey.ESTIMATED_AVERAGE_SIZE,
//                                ContractKey.MAX_SIZE,
//                                ContractKey::new,
//                                ContractKey::readKeySize,
//                                TestLeafData.SIZE_BYTES, TestLeafData::new,
//                                STORE_PATH,
//                                size*10L,
//                                false,
//                                Long.MAX_VALUE
//                        )));
//    }
//
//    public void assertLeaf(VFCDataSourceExceptionWrapper<ContractKey,TestLeafData>  dataSource, long path, int i) {
//        assertLeaf(dataSource,path,i,i);
//    }
//
//    public void assertLeaf(VFCDataSourceExceptionWrapper<ContractKey, TestLeafData>  dataSource, long path, int i, int valueIndex) {
//        try {
//            final var expectedRecord = createVirtualLeafRecord(path, i,valueIndex);
//            // things that should have changed
//            assertEqualsAndPrint(expectedRecord, dataSource.loadLeafRecord(newContractKey(i)));
//            assertEqualsAndPrint(expectedRecord, dataSource.loadLeafRecord(path));
//            assertEquals(hash(i), dataSource.loadLeafHash(path));
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail("Exception should not have been thrown here!");
//        }
//    }
//
//    @SuppressWarnings("rawtypes")
//    public void assertEqualsAndPrint(VirtualLeafRecord recordA, VirtualLeafRecord recordB) {
//        assertNotNull(recordA);
//        assertNotNull(recordB);
//        boolean equals = recordA.equals(recordB);
//        if (!equals) {
//            System  .out.println("   [Expected] VirtualLeafRecord{path=" + recordA.getPath() + ", key=" + recordA.getKey() + ", value=" + recordA.getValue() + ", hash="+recordA.getHash()+"}");
//            System  .out.println("   [Actual]   VirtualLeafRecord{path=" + recordB.getPath() + ", key=" + recordB.getKey() + ", value=" + recordB.getValue() + ", hash="+recordB.getHash()+"}");
//        }
//        assertEquals(recordA,recordB);
//    }
}
