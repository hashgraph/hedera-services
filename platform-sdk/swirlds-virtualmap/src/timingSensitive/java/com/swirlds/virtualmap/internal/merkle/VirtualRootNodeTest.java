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

package com.swirlds.virtualmap.internal.merkle;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.test.fixtures.RandomUtils.nextInt;
import static com.swirlds.virtualmap.test.fixtures.VirtualMapTestUtils.CONFIGURATION;
import static com.swirlds.virtualmap.test.fixtures.VirtualMapTestUtils.createRoot;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.io.utility.LegacyTemporaryFileBuilder;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.config.VirtualMapConfig_;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.datasource.VirtualLeafBytes;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode.ClassVersion;
import com.swirlds.virtualmap.test.fixtures.DummyVirtualStateAccessor;
import com.swirlds.virtualmap.test.fixtures.InMemoryBuilder;
import com.swirlds.virtualmap.test.fixtures.InMemoryDataSource;
import com.swirlds.virtualmap.test.fixtures.TestKey;
import com.swirlds.virtualmap.test.fixtures.TestValue;
import com.swirlds.virtualmap.test.fixtures.TestValueCodec;
import com.swirlds.virtualmap.test.fixtures.VirtualTestBase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("ALL")
class VirtualRootNodeTest extends VirtualTestBase {

    @TempDir
    private Path tempDir;

    void testEnableVirtualRootFlush() throws ExecutionException, InterruptedException {
        VirtualRootNode fcm0 = createRoot();
        fcm0.postInit(new DummyVirtualStateAccessor());
        assertFalse(fcm0.shouldBeFlushed(), "map should not yet be flushed");

        VirtualRootNode fcm1 = fcm0.copy();
        fcm1.postInit(new DummyVirtualStateAccessor());
        assertFalse(fcm1.shouldBeFlushed(), "map should not yet be flushed");

        VirtualRootNode fcm2 = fcm1.copy();
        fcm2.postInit(new DummyVirtualStateAccessor());
        assertFalse(fcm1.shouldBeFlushed(), "map should not yet be flushed");

        VirtualRootNode fcm3 = fcm2.copy();
        fcm3.postInit(new DummyVirtualStateAccessor());
        fcm3.enableFlush();
        assertTrue(fcm3.shouldBeFlushed(), "map should now be flushed");

        fcm0.release();
        fcm1.release();
        fcm2.release();
        fcm3.release();
    }

    @Test
    @DisplayName("A new map with a datasource with a root hash reveals it")
    void mapWithExistingHashedDataHasNonNullRootHash() throws ExecutionException, InterruptedException {
        // The builder I will use with this map is unique in that each call to "build" returns THE SAME DATASOURCE.
        final InMemoryDataSource ds = new InMemoryDataSource("mapWithExistingHashedDataHasNonNullRootHash");
        final VirtualDataSourceBuilder builder = new InMemoryBuilder();

        final VirtualRootNode fcm = new VirtualRootNode(builder, CONFIGURATION.getConfigData(VirtualMapConfig.class));
        fcm.postInit(new DummyVirtualStateAccessor());
        fcm.enableFlush();
        fcm.put(A_KEY, APPLE, TestValueCodec.INSTANCE);

        final VirtualRootNode copy = fcm.copy();
        copy.postInit(fcm.getState());

        fcm.getHash();
        final Hash expectedHash = fcm.getChild(0).getHash();
        fcm.release();
        fcm.waitUntilFlushed();

        final VirtualRootNode fcm2 = new VirtualRootNode(builder, CONFIGURATION.getConfigData(VirtualMapConfig.class));
        fcm2.postInit(copy.getState());
        assertNotNull(fcm2.getChild(0), "child should not be null");
        assertEquals(expectedHash, fcm2.getChild(0).getHash(), "hash should match expected");

        copy.release();
        fcm2.release();
    }

    @Test
    @DisplayName("Remove only element")
    void removeOnlyElement() throws ExecutionException, InterruptedException {

        final VirtualRootNode fcm = createRoot();
        fcm.enableFlush();
        fcm.put(A_KEY, APPLE, TestValueCodec.INSTANCE);

        final VirtualRootNode copy = fcm.copy();
        copy.postInit(fcm.getState());
        fcm.release();
        fcm.waitUntilFlushed();

        final TestValue removed = copy.remove(A_KEY, TestValueCodec.INSTANCE);
        assertEquals(APPLE, removed, "Wrong value");

        // FUTURE WORK validate hashing works as expected

        copy.release();
    }

    @Test
    @DisplayName("Remove element twice")
    void removeElementTwice() throws ExecutionException, InterruptedException {
        final VirtualRootNode fcm = createRoot();
        fcm.enableFlush();
        fcm.put(A_KEY, APPLE, TestValueCodec.INSTANCE);
        fcm.put(B_KEY, BANANA, TestValueCodec.INSTANCE);
        fcm.put(C_KEY, CHERRY, TestValueCodec.INSTANCE);

        final VirtualRootNode copy = fcm.copy();
        copy.postInit(fcm.getState());
        fcm.release();
        fcm.waitUntilFlushed();

        final TestValue removed = copy.remove(B_KEY, TestValueCodec.INSTANCE);
        final TestValue removed2 = copy.remove(B_KEY, TestValueCodec.INSTANCE);
        assertEquals(BANANA, removed, "Wrong value");
        assertNull(removed2, "Expected null");
        copy.release();
    }

    @Test
    @DisplayName("Remove elements in reverse order")
    void removeInReverseOrder() throws ExecutionException, InterruptedException {
        final VirtualRootNode fcm = createRoot();
        fcm.enableFlush();
        fcm.put(A_KEY, APPLE, TestValueCodec.INSTANCE);
        fcm.put(B_KEY, BANANA, TestValueCodec.INSTANCE);
        fcm.put(C_KEY, CHERRY, TestValueCodec.INSTANCE);
        fcm.put(D_KEY, DATE, TestValueCodec.INSTANCE);
        fcm.put(E_KEY, EGGPLANT, TestValueCodec.INSTANCE);
        fcm.put(F_KEY, FIG, TestValueCodec.INSTANCE);
        fcm.put(G_KEY, GRAPE, TestValueCodec.INSTANCE);

        final VirtualRootNode copy = fcm.copy();
        copy.postInit(fcm.getState());
        fcm.release();
        fcm.waitUntilFlushed();

        assertEquals(GRAPE, copy.remove(G_KEY, TestValueCodec.INSTANCE), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY, E_KEY, C_KEY, F_KEY, B_KEY, D_KEY);
        assertEquals(FIG, copy.remove(F_KEY, TestValueCodec.INSTANCE), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY, E_KEY, C_KEY, B_KEY, D_KEY);
        assertEquals(EGGPLANT, copy.remove(E_KEY, TestValueCodec.INSTANCE), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY, C_KEY, B_KEY, D_KEY);
        assertEquals(DATE, copy.remove(D_KEY, TestValueCodec.INSTANCE), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY, C_KEY, B_KEY);
        assertEquals(CHERRY, copy.remove(C_KEY, TestValueCodec.INSTANCE), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY, B_KEY);
        assertEquals(BANANA, copy.remove(B_KEY, TestValueCodec.INSTANCE), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY);
        assertEquals(APPLE, copy.remove(A_KEY, TestValueCodec.INSTANCE), "Wrong value");

        // FUTURE WORK validate hashing works as expected

        copy.release();
    }

    /**
     * This test deserializes a VirtualRootNode that was serialized with version 2 of the serialization format.
     * This node contains 100 entries, but only 88 of them are valid. The other 12 are deleted.
     */
    @Test
    void testSerializeDeserialize() throws IOException {
        String fileName = "rootNode.bin";
        serializeRoot(fileName);
        deserializeRootNodeAndVerify(
                new FileInputStream(tempDir.resolve(fileName).toFile()), ClassVersion.CURRENT_VERSION);
    }

    private void deserializeRootNodeAndVerify(InputStream resourceAsStream, int version) throws IOException {
        final VirtualRootNode root = createRoot();

        try (SerializableDataInputStream input = new SerializableDataInputStream(resourceAsStream)) {
            root.deserialize(input, tempDir, version);
            root.postInit(new DummyVirtualStateAccessor());
            final VirtualNodeCache cache = root.getCache();
            for (int i = 0; i < 100; i++) {
                final Bytes key = TestKey.longToKey(i);
                if (version >= ClassVersion.VERSION_3_NO_NODE_CACHE) {
                    // Cache must be empty, all values must be in the data source
                    assertNull(cache.lookupLeafByKey(key));
                }
                if (i % 7 != 0) {
                    assertEquals(new TestValue(i).toBytes(), root.getBytes(key));
                    assertEquals(new TestValue(i), root.get(key, TestValueCodec.INSTANCE));
                } else {
                    assertNull(root.get(TestKey.longToKey(i), null));
                }
            }
            root.release();
        }
    }

    private void serializeRoot(String fileName) throws IOException {
        try (FileOutputStream fileOutputStream =
                        new FileOutputStream(tempDir.resolve(fileName).toFile());
                SerializableDataOutputStream out = new SerializableDataOutputStream(fileOutputStream)) {
            VirtualRootNode testKeyTestValueVirtualRootNode = prepareRootForSerialization();
            testKeyTestValueVirtualRootNode.serialize(out, tempDir);
            fileOutputStream.flush();
            testKeyTestValueVirtualRootNode.release();
        }
    }

    private static VirtualRootNode prepareRootForSerialization() {
        final VirtualRootNode root = createRoot();
        root.enableFlush();

        Set<Bytes> keysToRemove = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            root.put(TestKey.longToKey(i), new TestValue(i), TestValueCodec.INSTANCE);
            if (i % 7 == 0) {
                keysToRemove.add(TestKey.longToKey(i));
            }
        }

        for (Bytes key : keysToRemove) {
            root.remove(key, null);
        }
        root.computeHash();
        root.setImmutable(true);
        return root;
    }

    /**
     * This is a preliminary example of how to move data from one VirtualMap
     * to another.
     *
     * @throws InterruptedException
     * 		if the thread is interrupted during sleep
     */
    @Test
    @Tags({@Tag("VMAP-013")})
    void moveDataAcrossMaps() throws InterruptedException {
        final int totalSize = 1_000_000;
        final VirtualRootNode root1 = createRoot();
        for (int index = 0; index < totalSize; index++) {
            final Bytes key = TestKey.longToKey(index);
            final TestValue value = new TestValue(index);
            root1.put(key, value, TestValueCodec.INSTANCE);
        }

        final VirtualRootNode root2 = createRoot();
        final long firstLeafPath = root1.getState().getFirstLeafPath();
        final long lastLeafPath = root1.getState().getLastLeafPath();
        for (long index = firstLeafPath; index <= lastLeafPath; index++) {
            final VirtualLeafBytes leaf = root1.getRecords().findLeafRecord(index);
            final Bytes key = leaf.keyBytes().replicate();
            final Bytes value = leaf.valueBytes().replicate();
            root2.putBytes(key, value);
        }

        for (int index = 0; index < totalSize; index++) {
            final Bytes key = TestKey.longToKey(index);
            root1.remove(key, null);
        }

        assertTrue(root1.isEmpty(), "All elements have been removed");
        root1.release();
        TimeUnit.MILLISECONDS.sleep(100);
        System.gc();
        assertEquals(totalSize, root2.size(), "New map still has all data");
        for (int index = 0; index < totalSize; index++) {
            final Bytes key = TestKey.longToKey(index);
            final TestValue expectedValue = new TestValue(index);
            final TestValue value = root2.get(key, TestValueCodec.INSTANCE);
            assertEquals(expectedValue, value, "Values have the same content");
        }
    }

    @Test
    @DisplayName("Snapshot Test")
    void snapshotTest() throws IOException {
        final List<Path> paths = new LinkedList<>();
        paths.add(Path.of("asdf"));
        for (final Path destination : paths) {
            final VirtualMap original = new VirtualMap("test", new InMemoryBuilder(), CONFIGURATION);
            final VirtualMap copy = original.copy();

            final VirtualRootNode root = original.getChild(1);
            root.getHash(); // forces copy to become hashed
            root.getPipeline().pausePipelineAndRun("snapshot", () -> {
                root.snapshot(destination);
                return null;
            });
            assertTrue(root.isDetached(), "root should be detached");

            original.release();
            copy.release();
        }
    }

    @Test
    @DisplayName("Snapshot and restore")
    void snapshotAndRestore() throws IOException {
        final VirtualDataSourceBuilder dsBuilder = new InMemoryBuilder();
        final List<VirtualMap> copies = new LinkedList<>();
        final VirtualMap copy0 = new VirtualMap("test", dsBuilder, CONFIGURATION);
        copies.add(copy0);
        for (int i = 1; i <= 10; i++) {
            final VirtualMap prevCopy = copies.get(i - 1);
            final VirtualMap copy = prevCopy.copy();
            // i-th copy contains TestKey(i)
            copy.put(TestKey.longToKey(i), new TestValue(i + 100), TestValueCodec.INSTANCE);
            copies.add(copy);
        }
        for (VirtualMap copy : copies) {
            // Force virtual map / root node hashing
            copy.getRight().getHash();
        }
        // Take a snapshot of copy 5
        final VirtualMap copy5 = copies.get(5);
        final Path snapshotPath =
                LegacyTemporaryFileBuilder.buildTemporaryDirectory("snapshotAndRestore", CONFIGURATION);
        try (final ByteArrayOutputStream bout = new ByteArrayOutputStream();
                final SerializableDataOutputStream out = new SerializableDataOutputStream(bout)) {
            copy5.serialize(out, snapshotPath);
            try (final ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
                    final SerializableDataInputStream in = new SerializableDataInputStream(bin)) {
                final VirtualMap restored = new VirtualMap(CONFIGURATION);
                restored.deserialize(in, snapshotPath, copy0.getVersion());
                // All keys 1 to 5 should be in the snapshot
                for (int i = 1; i < 6; i++) {
                    final Bytes key = TestKey.longToKey(i);
                    assertTrue(restored.containsKey(key), "Key " + i + " not found");
                    assertEquals(new TestValue(i + 100), restored.get(key, TestValueCodec.INSTANCE));
                }
                // All keys 6 to 10 should not be there
                for (int i = 6; i < 10; i++) {
                    final Bytes key = TestKey.longToKey(i);
                    assertFalse(restored.containsKey(key), "Key " + i + " found");
                    assertNull(restored.get(key, TestValueCodec.INSTANCE));
                }
            }
        } finally {
            copies.forEach(VirtualMap::release);
        }
    }

    @Test
    @DisplayName("Detach Test")
    void detachTest() throws IOException {
        final VirtualMap original = new VirtualMap("test", new InMemoryBuilder(), CONFIGURATION);
        final VirtualMap copy = original.copy();

        final VirtualRootNode root = original.getChild(1);
        root.getHash(); // forces copy to become hashed
        final RecordAccessor detachedCopy = root.getPipeline().pausePipelineAndRun("copy", root::detach);
        assertTrue(root.isDetached(), "root should be detached");
        assertNotNull(detachedCopy);

        original.release();
        copy.release();
        detachedCopy.getDataSource().close();
    }

    @Test
    @DisplayName("Default flush threshold not zero")
    void defaultFlushThresholdTest() {
        final VirtualMapConfig config =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(VirtualMapConfig.class);
        VirtualRootNode root = createRoot();
        assertEquals(config.copyFlushThreshold(), root.getFlushThreshold());
        root.release();
    }

    @Test
    @DisplayName("Flush interval is inherited by copies")
    void flushIntervalInheritedTest() {
        final long threshold = 12345678L;
        final VirtualMapConfig config =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(VirtualMapConfig.class);

        final int flushInterval = config.flushInterval();
        VirtualRootNode root = createRoot();
        root.setFlushThreshold(threshold);
        for (int i = 0; i <= flushInterval; i++) {
            assertEquals(threshold, root.getFlushThreshold());
            VirtualRootNode copy = root.copy();
            copy.postInit(root.getState());
            root.release();
            root = copy;
        }
        root.release();
    }

    @Test
    @DisplayName("Zero flush threshold enables round based flushes")
    void zeroFlushThresholdTest() {
        final VirtualMapConfig config =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(VirtualMapConfig.class);
        final int flushInterval = config.flushInterval();
        VirtualRootNode root = createRoot();
        root.setFlushThreshold(0);
        assertFalse(root.shouldBeFlushed()); // the very first copy is never flushed
        for (int i = 0; i < flushInterval; i++) {
            VirtualRootNode copy = root.copy();
            copy.postInit(root.getState());
            root.release();
            root = copy;
        }
        assertTrue(root.shouldBeFlushed());
        root.release();
    }

    @Test
    @DisplayName("Default zero flush threshold")
    void defaultZeroFlushThresholdTest() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(VirtualMapConfig_.COPY_FLUSH_THRESHOLD, "0")
                .getOrCreateConfig();

        VirtualRootNode root = createRoot(configuration);
        assertEquals(0, root.getFlushThreshold());
        final int flushInterval =
                configuration.getConfigData(VirtualMapConfig.class).flushInterval();
        for (int i = 0; i < flushInterval; i++) {
            VirtualRootNode copy = root.copy();
            copy.postInit(root.getState());
            root.release();
            root = copy;
        }
        assertTrue(root.shouldBeFlushed());
        root.setFlushThreshold(12345678L);
        assertTrue(root.shouldBeFlushed());
        for (int i = 0; i < flushInterval; i++) {
            VirtualRootNode copy = root.copy();
            copy.postInit(root.getState());
            root.release();
            root = copy;
        }
        assertFalse(root.shouldBeFlushed()); // should still have a custom flush threshold
        root.release();
    }

    @Test
    void inMemoryAddRemoveNoFlushTest() throws InterruptedException {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(VirtualMapConfig_.COPY_FLUSH_THRESHOLD, 1_000_000)
                .getOrCreateConfig();

        VirtualRootNode root =
                new VirtualRootNode(new InMemoryBuilder(), configuration.getConfigData(VirtualMapConfig.class));

        final VirtualRootNode copy0 = root;
        VirtualMapState state = new VirtualMapState("label");
        copy0.postInit(new VirtualStateAccessorImpl(state));
        for (int i = 0; i < 100; i++) {
            final Bytes key = TestKey.longToKey(i);
            final TestValue value = new TestValue(1000000 + i);
            root.put(key, value, TestValueCodec.INSTANCE);
        }

        // Here is the test: in every copy, add 100 elements. In the same copy, delete all elements
        // added in the previous copy. Every copy will contain no more than 200 elements, therefore
        // its effective size will be small, so none of the copies should be flushed to disk
        final int nCopies = 10000;
        final VirtualRootNode[] copies = new VirtualRootNode[nCopies];
        copies[0] = root;
        for (int copyNo = 1; copyNo < nCopies; copyNo++) {
            final VirtualRootNode copy = root.copy();
            copies[copyNo] = copy;
            state = state.copy();
            copy.postInit(new VirtualStateAccessorImpl(state));
            root.release();
            root = copy;
            for (int i = 0; i < 100; i++) {
                final int toAdd = copyNo * 100 + i;
                final Bytes keyToAdd = TestKey.longToKey(toAdd);
                final TestValue value = new TestValue(1000000 + toAdd);
                root.put(keyToAdd, value, TestValueCodec.INSTANCE);
                final int toRemove = (copyNo - 1) * 100 + i;
                final Bytes keytoRemove = TestKey.longToKey(toRemove);
                root.remove(keytoRemove, TestValueCodec.INSTANCE);
            }
        }

        // The last two copies should not be checked: the last one is mutable, the one before is not
        // mergeable until its next copy is immutable
        for (int i = 0; i < nCopies - 2; i++) {
            // Copies must be merged, not flushed
            assertEventuallyTrue(copies[i]::isMerged, Duration.ofSeconds(16), "copy " + i + " should be merged");
        }

        final var lcopy = root.copy();
        lcopy.postInit(new VirtualStateAccessorImpl(state));
        root.enableFlush();
        root.release();
        root.waitUntilFlushed();
        root = lcopy;

        // Values from copies 0 to nCopies - 2 should not be there (removed)
        for (int copyNo = 0; copyNo < nCopies - 2; copyNo++) {
            for (int i = 0; i < 100; i++) {
                final int toCheck = copyNo * 100 + i;
                final Bytes keyToCheck = TestKey.longToKey(toCheck);
                final TestValue value = root.get(keyToCheck, TestValueCodec.INSTANCE);
                assertNull(value);
                final VirtualLeafBytes leafRec = root.getCache().lookupLeafByKey(keyToCheck);
                assertNull(leafRec);
            }
        }
    }

    @Test
    void inMemoryAddRemoveSomeFlushesTest() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(VirtualMapConfig_.COPY_FLUSH_THRESHOLD, 1_000_000)
                .getOrCreateConfig();

        VirtualRootNode root =
                new VirtualRootNode(new InMemoryBuilder(), configuration.getConfigData(VirtualMapConfig.class));

        final int nCopies = 1000;
        final VirtualRootNode[] copies = new VirtualRootNode[nCopies];

        final VirtualRootNode copy0 = root;
        copies[0] = copy0;
        VirtualMapState state = new VirtualMapState("label");
        copy0.postInit(new VirtualStateAccessorImpl(state));
        for (int i = 0; i < 100; i++) {
            final Bytes key = TestKey.longToKey(i);
            final TestValue value = new TestValue(1000000 + i);
            root.put(key, value, TestValueCodec.INSTANCE);
        }

        final VirtualRootNode copy1 = root.copy();
        copies[1] = copy1;
        state = state.copy();
        copy1.postInit(new VirtualStateAccessorImpl(state));
        root.release();
        root = copy1;
        for (int i = 100; i < 200; i++) {
            final Bytes key = TestKey.longToKey(i);
            final TestValue value = new TestValue(1000000 + i);
            root.put(key, value, TestValueCodec.INSTANCE);
        }

        // Here is the test: in every copy, add 100 elements. In the same copy, delete all elements
        // added in the previous copy. In the same copy, re-add the same elements that were added
        // two copies ago. It will cause copies to grow in size, so eventually some copies must be
        // flushed
        for (int copyNo = 2; copyNo < nCopies; copyNo++) {
            final VirtualRootNode copy = root.copy();
            copies[copyNo] = copy;
            state = state.copy();
            copy.postInit(new VirtualStateAccessorImpl(state));
            root.release();
            root = copy;
            for (int i = 0; i < 100; i++) {
                // Add
                final int toAdd = copyNo * 100 + i;
                final Bytes keyToAdd = TestKey.longToKey(toAdd);
                final TestValue value = new TestValue(1000000 + toAdd);
                root.put(keyToAdd, value, TestValueCodec.INSTANCE);
                // Remove
                final int toRemove = (copyNo - 1) * 100 + i;
                final Bytes keytoRemove = TestKey.longToKey(toRemove);
                root.remove(keytoRemove, TestValueCodec.INSTANCE);
                // Re-add
                final int toReAdd = (copyNo - 2) * 100 + i;
                final Bytes keytoReAdd = TestKey.longToKey(toReAdd);
                final TestValue valueToReAdd = new TestValue(1000000 + toReAdd);
                root.put(keytoReAdd, valueToReAdd, TestValueCodec.INSTANCE);
            }
        }

        // The last two copies should not be checked: the last one is mutable, the one before is not
        // mergeable until its next copy is immutable
        int merged = 0;
        int flushed = 0;
        for (int i = 0; i < nCopies - 2; i++) {
            final VirtualRootNode copy = copies[i];
            // Copies must be merged, not flushed
            assertEventuallyTrue(
                    () -> copy.isMerged() || copy.isFlushed(),
                    Duration.ofSeconds(8),
                    "copy " + i + " should be merged or flushed");
            if (copy.isMerged()) {
                merged++;
            }
            if (copy.isFlushed()) {
                flushed++;
            }
        }
        assertTrue(merged > 0, "At least one copy must be merged");
        assertTrue(flushed > 0, "At least one copy must be flushed");
        assertTrue(merged > flushed, "More copies must be merged than flushed");

        // All values from copies 0 to nCopies - 2 should be available (re-added)
        for (int copyNo = 0; copyNo < nCopies - 2; copyNo++) {
            for (int i = 0; i < 100; i++) {
                final int toCheck = copyNo * 100 + i;
                final Bytes keyToCheck = TestKey.longToKey(toCheck);
                final TestValue value = root.get(keyToCheck, TestValueCodec.INSTANCE);
                assertNotNull(value);
                final int expected = 1000000 + toCheck;
                assertEquals("Value " + expected, value.getValue());
            }
        }
        // Values from copy nCopies - 2 should not be there (removed)
        for (int i = 0; i < 100; i++) {
            final int toCheck = (nCopies - 2) * 100 + i;
            final Bytes keyToCheck = TestKey.longToKey(toCheck);
            final TestValue value = root.get(keyToCheck, TestValueCodec.INSTANCE);
            assertNull(value);
        }
        // Values from copy nCopies - 1 should be there (added)
        for (int i = 0; i < 100; i++) {
            final int toCheck = (nCopies - 1) * 100 + i;
            final Bytes keyToCheck = TestKey.longToKey(toCheck);
            final TestValue value = root.get(keyToCheck, TestValueCodec.INSTANCE);
            assertNotNull(value);
            final int expected = 1000000 + toCheck;
            assertEquals("Value " + expected, value.getValue());
        }

        root.release();
    }

    @Test
    void inMemoryUpdateNoFlushTest() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(VirtualMapConfig_.COPY_FLUSH_THRESHOLD, 1_000_000)
                .getOrCreateConfig();

        VirtualRootNode root =
                new VirtualRootNode(new InMemoryBuilder(), configuration.getConfigData(VirtualMapConfig.class));
        VirtualMapState state = new VirtualMapState("label");
        root.postInit(new VirtualStateAccessorImpl(state));

        // Here is the test: add/update 1000 elements in every copy. Number of mutations in the
        // node cache will grow, but total number of entities in the map will not. Without in-memory
        // maps, it would result in some flushes, and with in-memory support, all copies should be
        // GC'ed and then merged
        final int nCopies = 1000;
        final VirtualRootNode[] copies = new VirtualRootNode[nCopies];
        copies[0] = root;
        for (int copyNo = 1; copyNo < nCopies; copyNo++) {
            final VirtualRootNode copy = root.copy();
            copies[copyNo] = copy;
            state = state.copy();
            copy.postInit(new VirtualStateAccessorImpl(state));
            root.release();
            root = copy;
            for (int i = 0; i < 1000; i++) {
                final Bytes keyToAdd = TestKey.longToKey(i);
                final TestValue value = new TestValue(1000000 + i);
                root.put(keyToAdd, value, TestValueCodec.INSTANCE);
            }
        }

        // The last two copies should not be checked: the last one is mutable, the one before is not
        // mergeable until its next copy is immutable
        for (int i = 0; i < nCopies - 2; i++) {
            // Copies must be merged, not flushed
            assertEventuallyTrue(copies[i]::isMerged, Duration.ofSeconds(16), "copy " + i + " should be merged");
        }
    }

    @Test
    @DisplayName("Copy of a root node with terminated pipeline")
    void copyOfRootNodeWithTerminatedPipeline() {
        VirtualRootNode root = createRoot();
        root.getPipeline().terminate();
        assertThrows(IllegalStateException.class, () -> root.copy());
    }

    @Test
    @DisplayName("Calculate hashes for persisted leaf nodes")
    void testFullRehash() throws InterruptedException {
        final VirtualRootNode root = prepareRootForFullRehash();

        root.fullLeafRehashIfNecessary();

        // make sure that the elements have hashes
        IntStream.range(1, 101).forEach(index -> {
            assertNotNull(root.getRecords().findHash(index));
        });
    }

    @Test
    @DisplayName("Root node should be hashed after full leaves rehash")
    void testHashedAfterFullRehash() {
        final VirtualRootNode root = prepareRootForFullRehash();
        root.fullLeafRehashIfNecessary();

        assertTrue(root.isHashed());
    }

    @Test
    @DisplayName("Fail to do full rehash because of save failure")
    void testFullRehash_failOnSave() throws InterruptedException {
        final VirtualRootNode root = prepareRootForFullRehash();
        ((InMemoryDataSource) root.getDataSource()).setFailureOnSave(true);

        assertThrows(MerkleSynchronizationException.class, () -> root.fullLeafRehashIfNecessary());
    }

    @Test
    @DisplayName("Fail to do full rehash because of load failure")
    void testFullRehash_failOnLeafLookup() throws InterruptedException {
        final VirtualRootNode root = prepareRootForFullRehash();
        ((InMemoryDataSource) root.getDataSource()).setFailureOnLeafRecordLookup(true);

        assertThrows(MerkleSynchronizationException.class, () -> root.fullLeafRehashIfNecessary());
    }

    @Test
    @DisplayName("Fail to do full rehash because of hash lookup failure")
    void testFullRehash_failOnHashLookup() throws InterruptedException {
        final VirtualRootNode root = prepareRootForFullRehash();
        ((InMemoryDataSource) root.getDataSource()).setFailureOnHashLookup(true);

        assertThrows(UncheckedIOException.class, () -> root.fullLeafRehashIfNecessary());
    }

    private static VirtualRootNode prepareRootForFullRehash() {
        final VirtualRootNode root = createRoot();
        root.enableFlush();

        // add 100 elements
        IntStream.range(1, 101).forEach(index -> {
            root.put(TestKey.longToKey(index), new TestValue(nextInt()), TestValueCodec.INSTANCE);
        });

        // make sure that the elements have no hashes
        IntStream.range(1, 101).forEach(index -> {
            assertNull(root.getRecords().findHash(index));
        });

        // prepare the root for h full leaf rehash
        root.setImmutable(true);
        root.getCache().seal();
        root.flush();

        return root;
    }

    @Test
    void getVersion() {
        assertEquals(4, createRoot().getVersion());
    }

    @Test
    void postInitNoOpIfLearnerTreeViewIsSet() {
        VirtualRootNode root = createRoot();
        VirtualRootNode anotherRoot = createRoot();
        anotherRoot.computeHash();
        root.setupWithOriginalNode(anotherRoot);
        assertDoesNotThrow(() -> root.postInit(null));
    }
}
