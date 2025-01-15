/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.merkledb;

import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.CONFIGURATION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.io.utility.LegacyTemporaryFileBuilder;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeVirtualValue;
import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeVirtualValueSerializer;
import com.swirlds.merkledb.test.fixtures.ExampleLongKeyFixedSize;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.config.VirtualMapConfig_;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import com.swirlds.virtualmap.internal.merkle.VirtualInternalNode;
import com.swirlds.virtualmap.internal.merkle.VirtualLeafNode;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import com.swirlds.virtualmap.serialize.KeySerializer;
import com.swirlds.virtualmap.serialize.ValueSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("VirtualMap Serialization Test")
class VirtualMapSerializationTests {

    public static final KeySerializer<ExampleLongKeyFixedSize> KEY_SERIALIZER =
            new ExampleLongKeyFixedSize.Serializer();

    public static final ValueSerializer<ExampleFixedSizeVirtualValue> VALUE_SERIALIZER =
            new ExampleFixedSizeVirtualValueSerializer();

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.merkledb");
        registry.registerConstructables("com.swirlds.virtualmap");
        registry.registerConstructables("com.swirlds.common");
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(
                        MerkleDbDataSourceBuilder.class, () -> new MerkleDbDataSourceBuilder(CONFIGURATION)));
        registry.registerConstructable(new ClassConstructorPair(VirtualMap.class, () -> new VirtualMap(CONFIGURATION)));
        registry.registerConstructable(new ClassConstructorPair(
                VirtualNodeCache.class,
                () -> new VirtualNodeCache(CONFIGURATION.getConfigData(VirtualMapConfig.class))));
    }

    /**
     * Create a new virtual map data source builder.
     */
    public static MerkleDbDataSourceBuilder constructBuilder() throws IOException {
        return constructBuilder(CONFIGURATION);
    }

    public static MerkleDbDataSourceBuilder constructBuilder(final Configuration configuration) throws IOException {
        // The tests below create maps with identical names. They would conflict with each other in the default
        // MerkleDb instance, so let's use a new database location for every map
        final Path defaultVirtualMapPath =
                LegacyTemporaryFileBuilder.buildTemporaryFile("merkledb-source", configuration);
        MerkleDb.setDefaultPath(defaultVirtualMapPath);
        final MerkleDbTableConfig tableConfig =
                new MerkleDbTableConfig((short) 1, DigestType.SHA_384, 1234, Long.MAX_VALUE);
        return new MerkleDbDataSourceBuilder(tableConfig, configuration);
    }

    /**
     * Validate that two maps contain the same data.
     */
    private void assertMapsAreEqual(
            final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> originalMap,
            final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> deserializedMap) {

        assertEquals(originalMap.size(), deserializedMap.size(), "size should match");

        MerkleCryptoFactory.getInstance().digestTreeSync(originalMap);
        MerkleCryptoFactory.getInstance().digestTreeSync(deserializedMap);

        final Map<MerkleRoute, Hash> hashes = new HashMap<>();

        originalMap.forEachNode((final MerkleNode node) -> {
            if (node instanceof VirtualLeafNode) {
                final VirtualLeafNode<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> leaf = node.cast();

                final ExampleLongKeyFixedSize key = leaf.getKey();
                final ExampleFixedSizeVirtualValue value = leaf.getValue();

                assertEquals(value, deserializedMap.get(key), "expected values to match");
            }

            if (node instanceof VirtualLeafNode || node instanceof VirtualInternalNode) {
                assertNotNull(node.getHash(), "hash should not be null");
                assertFalse(hashes.containsKey(node.getRoute()), "no two routes should match");
                hashes.put(node.getRoute(), node.getHash());
            }
        });

        deserializedMap.forEachNode((final MerkleNode node) -> {
            if (node instanceof VirtualLeafNode || node instanceof VirtualInternalNode) {
                assertTrue(hashes.containsKey(node.getRoute()), "route should exist in both trees");
                assertEquals(hashes.get(node.getRoute()), node.getHash(), "hash for each node should match");
            }
        });

        assertEquals(originalMap.getHash(), deserializedMap.getHash(), "hash should match");
    }

    /**
     * Add a number of randomized entries to the map.
     *
     * @param map
     * 		the map to update
     * @param count
     * 		the number of entries to add or update
     * @param updateCount
     * 		the number of entries to update. If zero then all entries are added.
     * @param seed
     * 		the seed to use
     */
    private void addRandomEntries(
            final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map,
            final int count,
            final int updateCount,
            final long seed) {

        final Random random = new Random(seed);
        final int offset = (int) Math.max(0, map.size() - updateCount);

        for (int i = 0; i < count; i++) {
            final int v = random.nextInt();

            final ExampleLongKeyFixedSize key = new ExampleLongKeyFixedSize(i + offset);
            final ExampleFixedSizeVirtualValue value = new ExampleFixedSizeVirtualValue(v);

            map.put(key, value);
        }
    }

    /**
     * Create a map and fill it with random key/value pairs.
     */
    @SuppressWarnings("SameParameterValue")
    private VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> generateRandomMap(
            final long seed, final int count, final String name) throws IOException {
        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map =
                new VirtualMap<>(name, KEY_SERIALIZER, VALUE_SERIALIZER, constructBuilder(), CONFIGURATION);
        addRandomEntries(map, count, 0, seed);
        return map;
    }

    @Test
    @DisplayName("Serialize Data Source Builder")
    void serializeDataSourceBuilder() throws IOException {
        final VirtualDataSourceBuilder builder = constructBuilder();

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

        out.writeSerializable(builder, true);

        final SerializableDataInputStream in =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        final VirtualDataSourceBuilder deserializedBuilder = in.readSerializable();

        assertEquals(builder, deserializedBuilder, "expected deserialized builder to match the original");
    }

    /**
     * Make sure the comparison utility function works as expected.
     */
    @Test
    @DisplayName("Map Comparison Test")
    void mapComparisonTest() throws IOException, InterruptedException {
        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map0 =
                generateRandomMap(0, 1_000, "test");

        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map1 =
                generateRandomMap(0, 1_000, "test");

        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map2 =
                generateRandomMap(1234, 1_000, "test");

        assertMapsAreEqual(map0, map0);
        assertMapsAreEqual(map0, map1);
        assertMapsAreEqual(map1, map1);
        assertMapsAreEqual(map1, map0);
        assertMapsAreEqual(map2, map2);
        assertThrows(AssertionError.class, () -> assertMapsAreEqual(map0, map2), "maps should not be equal");
        assertThrows(AssertionError.class, () -> assertMapsAreEqual(map1, map2), "maps should not be equal");
        assertThrows(AssertionError.class, () -> assertMapsAreEqual(map2, map0), "maps should not be equal");
        assertThrows(AssertionError.class, () -> assertMapsAreEqual(map2, map1), "maps should not be equal");

        map0.release();
        map1.release();
        map2.release();

        MILLISECONDS.sleep(100); // Hack. Release methods may not have finished their work yet.

        // doubly make sure dbs are closed, so we can delete temp files
        try {
            map0.getDataSource().close();
            map1.getDataSource().close();
            map2.getDataSource().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Test serialization of a map. Does not release any resources created by caller.
     */
    @SuppressWarnings("resource")
    private void testMapSerialization(final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map)
            throws IOException {

        final Path savedStateDirectory =
                LegacyTemporaryFileBuilder.buildTemporaryDirectory("saved-state", CONFIGURATION);

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final MerkleDataOutputStream out = new MerkleDataOutputStream(byteOut);

        // Make sure the map is hashed
        MerkleCryptoFactory.getInstance().digestTreeSync(map);

        out.writeMerkleTree(savedStateDirectory, map);
        out.flush();

        try (final Stream<Path> filesInDirectory = Files.list(savedStateDirectory)) {
            List list = filesInDirectory.toList();
            assertNotNull(list, "saved state directory is not a valid directory");
            assertTrue(list.size() > 0, "there should be a non-zero number of files created");
        }
        // Change default MerkleDb path, so data sources are restored into a different DB instance
        final Path restoredDbDirectory =
                LegacyTemporaryFileBuilder.buildTemporaryDirectory("merkledb-restored", CONFIGURATION);
        MerkleDb.setDefaultPath(restoredDbDirectory);

        final MerkleDataInputStream in = new MerkleDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> deserializedMap =
                in.readMerkleTree(savedStateDirectory, Integer.MAX_VALUE);

        assertMapsAreEqual(map, deserializedMap);

        deserializedMap.release();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 10, 100, 1000, 1023, 1024, 1025})
    @DisplayName("Serialize Unflushed Data")
    void serializeUnflushedData(final int count) throws IOException, InterruptedException {

        final long seed = new Random().nextLong();
        System.out.println("seed = " + seed);

        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map =
                generateRandomMap(seed, count, "test");

        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> copy = map.copy();

        testMapSerialization(map);

        final VirtualRootNode<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> root =
                map.getChild(1).cast();

        assertFalse(root.isFlushed(), "for this test, the root is expected not to be flushed");

        map.release();
        copy.release();

        MILLISECONDS.sleep(100); // Hack. Release methods may not have finished their work yet.
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 10, 100, 1000, 1023, 1024, 1025})
    @DisplayName("Serialize Only Flushed Data")
    void serializeOnlyFlushedData(final int count) throws InterruptedException, IOException {
        final long seed = new Random().nextLong();
        System.out.println("seed = " + seed);

        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map =
                generateRandomMap(seed, count, "test");
        final VirtualRootNode<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> root =
                map.getChild(1).cast();
        root.enableFlush();

        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> serializedCopy = map.copy();
        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> mutableCopy = serializedCopy.copy();
        map.release();
        root.waitUntilFlushed();

        testMapSerialization(serializedCopy);

        assertTrue(root.isFlushed(), "for this test, the root is expected to be flushed");

        serializedCopy.release();
        mutableCopy.release();

        MILLISECONDS.sleep(100); // Hack. Release methods may not have finished their work yet.
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 10, 100, 1000, 1023, 1024, 1025})
    @DisplayName("Serialize Flushed And Unflushed Data")
    void serializeFlushedAndUnflushedData(final int count) throws InterruptedException, IOException {
        final long seed = new Random().nextLong();
        System.out.println("seed = " + seed);

        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map =
                generateRandomMap(seed, count, "test");
        final VirtualRootNode<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> root =
                map.getChild(1).cast();
        root.enableFlush();

        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> copy0 = map.copy();
        addRandomEntries(copy0, count, count / 2, seed * 2 + 1);
        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> copy1 = copy0.copy();
        map.release();
        root.waitUntilFlushed();
        System.out.println("map size: " + map.size() + ", copy0 size: " + copy0.size());
        testMapSerialization(copy0);

        final VirtualRootNode<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> root0 =
                copy0.getChild(1).cast();

        assertTrue(root.isFlushed(), "for this test, the root is expected to be flushed");
        assertFalse(root0.isFlushed(), "for this test, the root0 is expected to not be flushed");

        copy0.release();
        copy1.release();

        MILLISECONDS.sleep(100); // Hack. Release methods may not have finished their work yet.
    }

    @Test
    void inMemoryModeSerde() throws IOException {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(VirtualMapConfig_.COPY_FLUSH_THRESHOLD, 1_000_000)
                .getOrCreateConfig();

        VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map = new VirtualMap<>(
                "inMemoryModeSerde", KEY_SERIALIZER, VALUE_SERIALIZER, constructBuilder(configuration), configuration);

        // Copy 0
        for (int i = 0; i < 100; i++) {
            final ExampleLongKeyFixedSize key = new ExampleLongKeyFixedSize(i);
            final ExampleFixedSizeVirtualValue value = new ExampleFixedSizeVirtualValue(1000000 + i);
            map.put(key, value);
        }

        // Copy 1
        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> copy1 = map.copy();
        map.release();
        map = copy1;
        for (int i = 100; i < 200; i++) {
            final ExampleLongKeyFixedSize key = new ExampleLongKeyFixedSize(i);
            final ExampleFixedSizeVirtualValue value = new ExampleFixedSizeVirtualValue(1000000 + i);
            map.put(key, value);
        }
        // Add more entries to copy 1 to force it to flush
        for (int i = 100000; i < 120000; i++) {
            final ExampleLongKeyFixedSize key = new ExampleLongKeyFixedSize(i);
            final ExampleFixedSizeVirtualValue value = new ExampleFixedSizeVirtualValue(1000000 + i);
            map.put(key, value);
        }

        final int nCopies = 100;
        for (int copyNo = 2; copyNo < nCopies; copyNo++) {
            final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> copy = map.copy();
            map.release();
            map = copy;
            for (int i = 0; i < 100; i++) {
                final int toAdd = copyNo * 100 + i;
                final ExampleLongKeyFixedSize keyToAdd = new ExampleLongKeyFixedSize(toAdd);
                final ExampleFixedSizeVirtualValue value = new ExampleFixedSizeVirtualValue(1000000 + toAdd);
                map.put(keyToAdd, value);
                final int toRemove = (copyNo - 2) * 100 + i + 75;
                final ExampleLongKeyFixedSize keytoRemove = new ExampleLongKeyFixedSize(toRemove);
                final ExampleFixedSizeVirtualValue removed = map.remove(keytoRemove);
                assertNotNull(removed);
            }
        }

        // Final copy
        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> copyF = map.copy();
        map.release();
        map = copyF;

        // And one more to make sure copyF is immutable and can be serialized
        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> copyOneMore = map.copy();

        final Hash originalHash = MerkleCryptoFactory.getInstance().digestTreeSync(copyF);

        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final Path tmp = LegacyTemporaryFileBuilder.buildTemporaryDirectory("inMemoryModeSerde", configuration);
        try (final SerializableDataOutputStream out = new SerializableDataOutputStream(bout)) {
            copyF.serialize(out, tmp);
        }

        MerkleDb.resetDefaultInstancePath();

        final ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
        map = new VirtualMap<>(configuration);
        try (final SerializableDataInputStream in = new SerializableDataInputStream(bin)) {
            map.deserialize(in, tmp, 3);
        }

        final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> copyAfter = map.copy();

        final Hash restoredHash = MerkleCryptoFactory.getInstance().digestTreeSync(map);
        assertEquals(originalHash, restoredHash);

        copyOneMore.release();
        copyAfter.release();
    }
}
