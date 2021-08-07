package com.hedera.services.state.merkle.v3;

import com.hedera.services.state.merkle.virtual.ContractKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Random;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.v2.VFCDataSourceTestUtils.*;
import static com.hedera.services.state.merkle.v3.V3TestUtils.newContractKey;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class VFCDataSourceImplV3LegacyAPITest {
    private static final Random RANDOM = new Random(1234);
    private static final Path STORE_PATH = Path.of("data-store");

    @SuppressWarnings("rawtypes")
    private static void closeAndDelete(VFCDataSourceExceptionWrapper dataSource) {
        dataSource.close();
        deleteDirectoryAndContents(STORE_PATH);
    }
    
    // =================================================================================================================
    // Tests with Long Keys

    private static VFCDataSourceExceptionWrapper<LongVKeyImpl,TestLeafData> createLongKeyDataSource(int size) {
        return Assertions.assertDoesNotThrow(() ->
                new VFCDataSourceExceptionWrapper<>(
                    new VFCDataSourceImplV3<>(
                            LongVKeyImpl.SIZE_BYTES, LongVKeyImpl::new,
                            TestLeafData.SIZE_BYTES, TestLeafData::new,
                            STORE_PATH,
                            size
                    )));
    }

    public void assertLongKeyLeaf(VFCDataSourceExceptionWrapper<LongVKeyImpl,TestLeafData>  dataSource, long path, int i) {
        // things that should not have changed
        assertEquals(new LongVKeyImpl(i),       dataSource.loadLeafKey(path));
        assertEquals(path,                     dataSource.loadLeafPath(new LongVKeyImpl(i)));
        // things that should have changed
        assertEquals(new TestLeafData(i),   dataSource.loadLeafValue(new LongVKeyImpl(i)));
        assertEquals(new TestLeafData(i),   dataSource.loadLeafValue(path));
        assertEquals(hash(i),               dataSource.loadLeafHash(path));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 31, 128, 10_000, 1_000_000})
    public void longKey_createAndCheckNodeHashes(int count) {
        var dataSource = createLongKeyDataSource(count);
        // create some node hashes
        IntStream.range(0,count).forEach(i -> dataSource.saveInternal(i,hash(i)));
        // check all the node hashes
        IntStream.range(0,count).forEach(i -> assertEquals(hash(i), dataSource.loadInternalHash(i)));
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void longKey_testRandomHashUpdates() {
        var dataSource = createLongKeyDataSource(1000);
        // create some -1 hashes
        for (int i : shuffle(RANDOM,IntStream.range(0,1000).toArray())) dataSource.saveInternal(i,hash(i));
        // check all the node hashes
        IntStream.range(0,1000).forEach(i -> assertEquals(hash(i), dataSource.loadInternalHash(i)));
        // close data source
        closeAndDelete(dataSource);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 31, 128, 10_000, 1_000_000})
    public void longKey_createAndCheckLeaves(int count) {
        var dataSource = createLongKeyDataSource(count);
        // create some leaves
        dataSource.startTransaction();
        IntStream.range(0,count).forEach(i -> dataSource.addLeaf(i,new LongVKeyImpl(i),new TestLeafData(i),hash(i)));
        dataSource.commitTransaction(null);
        // check all the leaf data
        IntStream.range(0,count).forEach(i -> assertLongKeyLeaf(dataSource,i,i));
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void longKey_updateLeaves() {
        var dataSource = createLongKeyDataSource(1000);
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
    public void longKey_moveLeaf() {
        var dataSource = createLongKeyDataSource(1000);
        // create some leaves
        dataSource.startTransaction();
        IntStream.range(0,1000).forEach(i -> dataSource.addLeaf(i,new LongVKeyImpl(i),new TestLeafData(i),hash(i)));
        dataSource.commitTransaction(null);
        // check 250 and 500
        assertLongKeyLeaf(dataSource,250,250);
        assertLongKeyLeaf(dataSource, 500,500);
        // move a leaf from 500 to 250
        dataSource.startTransaction();
        dataSource.updateLeaf(500,250, new LongVKeyImpl(500), hash(500));
        dataSource.commitTransaction(null);
        // check 250 now has 500's data
        assertLongKeyLeaf(dataSource,250,500);
        // close data source
        closeAndDelete(dataSource);
    }

    // =================================================================================================================
    // Tests with ContractKey Keys

    private static VFCDataSourceExceptionWrapper<ContractKey,TestLeafData> createContractKeyDataSource(int size) {
        return Assertions.assertDoesNotThrow(() ->
                new VFCDataSourceExceptionWrapper<>(
                        new VFCDataSourceImplV3<>(
                                ContractKey.SERIALIZED_SIZE, ContractKey::new,
                                TestLeafData.SIZE_BYTES, TestLeafData::new,
                                STORE_PATH,
                                size
                        )));
    }

    public void assertContractKeyLeaf(VFCDataSourceExceptionWrapper<ContractKey,TestLeafData>  dataSource, long path, int i) {
        // things that should not have changed
        assertEquals(newContractKey(i),       dataSource.loadLeafKey(path));
        assertEquals(path,                     dataSource.loadLeafPath(newContractKey(i)));
        // things that should have changed
        assertEquals(new TestLeafData(i),   dataSource.loadLeafValue(newContractKey(i)));
        assertEquals(new TestLeafData(i),   dataSource.loadLeafValue(path));
        assertEquals(hash(i),               dataSource.loadLeafHash(path));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 31, 128, 10_000, 1_000_000})
    public void contractKey_createAndCheckNodeHashes(int count) {
        var dataSource = createContractKeyDataSource(count);
        // create some node hashes
        IntStream.range(0,count).forEach(i -> dataSource.saveInternal(i,hash(i)));
        // check all the node hashes
        IntStream.range(0,count).forEach(i -> assertEquals(hash(i), dataSource.loadInternalHash(i)));
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void contractKey_testRandomHashUpdates() {
        var dataSource = createContractKeyDataSource(1000);
        // create some hashes in random order
        for (int i : shuffle(RANDOM,IntStream.range(0,1000).toArray())) dataSource.saveInternal(i,hash(i));
        // check all the node hashes
        IntStream.range(0,1000).forEach(i -> assertEquals(hash(i), dataSource.loadInternalHash(i)));
        // close data source
        closeAndDelete(dataSource);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 31, 128, 10_000, 1_000_000})
    public void contractKey_createAndCheckLeaves(int count) {
        var dataSource = createContractKeyDataSource(count);
        // create some leaves
        dataSource.startTransaction();
        IntStream.range(0,count).forEach(i -> dataSource.addLeaf(i,newContractKey(i),new TestLeafData(i),hash(i)));
        dataSource.commitTransaction(null);
        // check all the leaf data
        IntStream.range(0,count).forEach(i -> assertContractKeyLeaf(dataSource,i,i));
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void contractKey_updateLeaves() {
        var dataSource = createContractKeyDataSource(1000);
        // create some leaves
        dataSource.startTransaction();
        IntStream.range(0,1000).forEach(i -> dataSource.addLeaf(i,newContractKey(i),new TestLeafData(i),hash(i)));
        // random update
        for (int i : shuffle(RANDOM,IntStream.range(0,1000).toArray())) dataSource.updateLeaf(i,newContractKey(i),new TestLeafData(i+10000),hash(i+10000));
        dataSource.commitTransaction(null);
        // check all the leaf data
        IntStream.range(0,1000).forEach(i -> {

            int j = i+10000;
            // things that should not have changed
            assertEquals(newContractKey(i),       dataSource.loadLeafKey(i));
            assertEquals(i,                     dataSource.loadLeafPath(newContractKey(i)));
            // things that should have changed
            assertEquals(new TestLeafData(j),   dataSource.loadLeafValue(newContractKey(i)));
            assertEquals(new TestLeafData(j),   dataSource.loadLeafValue(i));
            assertEquals(hash(j),               dataSource.loadLeafHash(i));
        });
        // close data source
        closeAndDelete(dataSource);
    }

    @Test
    public void contractKey_moveLeaf() {
        var dataSource = createContractKeyDataSource(1000);
        // create some leaves
        dataSource.startTransaction();
        IntStream.range(0,1000).forEach(i -> dataSource.addLeaf(i,newContractKey(i),new TestLeafData(i),hash(i)));
        dataSource.commitTransaction(null);
        // check 250 and 500
        assertContractKeyLeaf(dataSource,250,250);
        assertContractKeyLeaf(dataSource, 500,500);
        // move a leaf from 500 to 250
        dataSource.startTransaction();
        dataSource.updateLeaf(500,250, newContractKey(500), hash(500));
        dataSource.commitTransaction(null);
        // check 250 now has 500's data
        assertContractKeyLeaf(dataSource,250,500);
        // close data source
        closeAndDelete(dataSource);
    }
}
