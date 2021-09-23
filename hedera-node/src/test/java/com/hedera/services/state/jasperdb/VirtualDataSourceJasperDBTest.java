package com.hedera.services.state.jasperdb;

import com.hedera.services.state.jasperdb.files.TestType;
import com.hedera.services.state.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.state.jasperdb.JasperDbTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("unchecked")
public class VirtualDataSourceJasperDBTest {
    private static final Random RANDOM = new Random(1234);
    private static final Path STORE_PATH = Path.of("data-store");

    // =================================================================================================================
    // Tests

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void createAndCheckInternalNodeHashes(TestType testType) throws IOException {
        int count = 10_000;
        var dataSource = createDataSource(testType,count);
        // create some node hashes
        dataSource.saveRecords(0,count,
                IntStream.range(0,count).mapToObj(this::createVirtualInternalRecord),
                Stream.empty()
        );
        // check all the node hashes
        IntStream.range(0,count).forEach(i -> {
            try {
                assertEquals(hash(i), dataSource.loadInternalRecord(i).getHash());
            } catch (IOException e) { throw new RuntimeException(e);}
        });
        // close data source
        closeAndDelete(dataSource);
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void testRandomHashUpdates(TestType testType) throws IOException {
        var dataSource = createDataSource(testType,1000);
        // create some node hashes
        dataSource.saveRecords(0,1000,
                IntStream.range(0,1000).mapToObj(this::createVirtualInternalRecord),
                Stream.empty()
        );
        // create some *10 hashes
        int[] randomInts = shuffle(RANDOM,IntStream.range(0,1000).toArray());
        dataSource.saveRecords(0,1000,
                Arrays.stream(randomInts).mapToObj(i -> new VirtualInternalRecord(i,hash(i*10))),
                Stream.empty()
        );
        // check all the node hashes
        IntStream.range(0,1000).forEach(i -> {
            try {
                assertEquals(hash(i*10), dataSource.loadInternalRecord(i).getHash());
            } catch (IOException e) { throw new RuntimeException(e);}
        });
        // close data source
        closeAndDelete(dataSource);
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void createAndCheckLeaves(TestType testType) throws IOException {
        int count = 10_000;
        var dataSource = createDataSource(testType, count);
        // create some leaves
        dataSource.saveRecords(0,count,
                Stream.empty(),
                IntStream.range(0,count).mapToObj(i -> createVirtualLeafRecord(testType,i))
        );
        // check all the leaf data
        IntStream.range(0,count).forEach(i -> assertLeaf(testType, dataSource,i,i));
        // close data source
        closeAndDelete(dataSource);
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void updateLeaves(TestType testType) throws IOException {
        var dataSource = createDataSource(testType,1000);
        // create some leaves
        dataSource.saveRecords(0,1000,
                Stream.empty(),
                IntStream.range(0,1000).mapToObj(i -> createVirtualLeafRecord(testType,i))
        );
        // check all the leaf data
        IntStream.range(0,1000).forEach(i -> assertLeaf(testType, dataSource,i,i));
        // update all to i+10,000 in a random order
        int[] randomInts = shuffle(RANDOM,IntStream.range(0,1000).toArray());
        dataSource.saveRecords(0,1000,
                Stream.empty(),
                Arrays
                        .stream(randomInts)
                        .mapToObj( i -> createVirtualLeafRecord(testType, i, i,i+10_000))
                        .sorted(Comparator.comparingLong(VirtualRecord::getPath))

        );
        assertEquals(createVirtualLeafRecord(testType, 100,100,100+10_000),
                createVirtualLeafRecord(testType, 100,100,100+10_000));
        // check all the leaf data
        IntStream.range(0,1000).forEach(i -> assertLeaf(testType, dataSource,i,i,i+10_000));
        // close data source
        closeAndDelete(dataSource);
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void moveLeaf(TestType testType) throws IOException {
        var dataSource = createDataSource(testType, 1000);
        // create some leaves
        dataSource.saveRecords(0,1000,
                Stream.empty(),
                IntStream.range(0,1000).mapToObj(i -> createVirtualLeafRecord(testType,i))
        );
        // check 250 and 500
        assertLeaf(testType, dataSource,250,250);
        assertLeaf(testType, dataSource, 500,500);
        // move a leaf from 500 to 250, under new API there is no move as such, so we just write 500 leaf at 250 path
        VirtualLongKey key500 = testType == TestType.fixed ? new ExampleFixedSizeLongKey(500) : new ExampleVariableSizeLongKey(500);
        dataSource.saveRecords(0,1000,
                Stream.empty(),
                Stream.of(new VirtualLeafRecord<>(250,hash(500),key500,new ExampleFixedSizeVirtualValue(500)))
        );
        // check 250 now has 500's data
        assertLeaf(testType, dataSource, 700,700);
        assertEquals(createVirtualLeafRecord(testType, 500,500,500),dataSource.loadLeafRecord(500)); // TODO the old 500 path still has Leaf that is now 250, ok?
        assertLeaf(testType, dataSource,250,500);
        // close data source
        closeAndDelete(dataSource);
    }

    // =================================================================================================================
    // Helper Methods

    private static VirtualDataSourceJasperDB<VirtualLongKey, ExampleFixedSizeVirtualValue> createDataSource(
            TestType testType,int size) throws IOException {
        final var keySerializer = testType.keySerializer;
        final Supplier<VirtualLongKey> keyConstructor = testType == TestType.fixed ?
                ExampleFixedSizeLongKey::new : ExampleVariableSizeLongKey::new;
        final VirtualLeafRecordSerializer<VirtualLongKey,ExampleFixedSizeVirtualValue> virtualLeafRecordSerializer =
                new VirtualLeafRecordSerializer<>(
                        1, DigestType.SHA_384,
                        1,keySerializer.getSerializedSize(),keyConstructor,
                        1,ExampleFixedSizeVirtualValue.SIZE_BYTES,ExampleFixedSizeVirtualValue::new,
                        false);
        final VirtualInternalRecordSerializer virtualInternalRecordSerializer = new VirtualInternalRecordSerializer();
        // clean folder first if it has old data in it
        deleteDirectoryAndContents(STORE_PATH);
        return new VirtualDataSourceJasperDB<>(
                virtualLeafRecordSerializer,
                virtualInternalRecordSerializer,
                (KeySerializer<VirtualLongKey>) testType.keySerializer,
                STORE_PATH,
                size*10L,
                true,
                Long.MAX_VALUE);
    }

    @SuppressWarnings("rawtypes")
    private static void closeAndDelete(VirtualDataSourceJasperDB dataSource) throws IOException {
        dataSource.close();
        deleteDirectoryAndContents(STORE_PATH);
    }

    private VirtualLeafRecord<VirtualLongKey, ExampleFixedSizeVirtualValue> createVirtualLeafRecord(
            TestType testType, int i) {
        return createVirtualLeafRecord(testType,i,i,i);
    }

    private VirtualLeafRecord<VirtualLongKey, ExampleFixedSizeVirtualValue> createVirtualLeafRecord(
            TestType testType, long path, int i, int valueIndex) {
        VirtualLongKey key = testType == TestType.fixed ?
                new ExampleFixedSizeLongKey(i) :
                new ExampleVariableSizeLongKey(i);
        return new VirtualLeafRecord<>(
                path,
                hash(i),
                key,
                new ExampleFixedSizeVirtualValue(valueIndex));
    }

    private VirtualInternalRecord createVirtualInternalRecord(int i) {
        return new VirtualInternalRecord(i,hash(i));
    }

    public void assertLeaf(TestType testType,
                           VirtualDataSourceJasperDB<VirtualLongKey, ExampleFixedSizeVirtualValue>  dataSource,
                           long path, int i) {
        assertLeaf(testType, dataSource,path,i,i);
    }

    public void assertLeaf(TestType testType,
                           VirtualDataSourceJasperDB<VirtualLongKey, ExampleFixedSizeVirtualValue>  dataSource,
                           long path, int i, int valueIndex) {
        try {
            final var expectedRecord =
                    createVirtualLeafRecord(testType, path, i,valueIndex);
            VirtualLongKey key = testType == TestType.fixed ?
                    new ExampleFixedSizeLongKey(i) :
                    new ExampleVariableSizeLongKey(i);
            // things that should have changed
            assertEqualsAndPrint(expectedRecord, dataSource.loadLeafRecord(key));
            assertEqualsAndPrint(expectedRecord, dataSource.loadLeafRecord(path));
            assertEquals(hash(i), dataSource.loadLeafHash(path));
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception should not have been thrown here!");
        }
    }

    @SuppressWarnings("rawtypes")
    public void assertEqualsAndPrint(VirtualLeafRecord recordA, VirtualLeafRecord recordB) {
        assertEquals(recordA.toString(),recordB.toString());
    }



}
