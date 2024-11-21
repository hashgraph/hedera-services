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

import static com.swirlds.common.test.fixtures.RandomUtils.nextInt;
import static com.swirlds.virtualmap.test.fixtures.VirtualMapTestUtils.createRoot;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.config.VirtualMapConfig_;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.datasource.VirtualLeafBytes;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode.ClassVersion;
import com.swirlds.virtualmap.test.fixtures.DummyVirtualStateAccessor;
import com.swirlds.virtualmap.test.fixtures.InMemoryBuilder;
import com.swirlds.virtualmap.test.fixtures.InMemoryDataSource;
import com.swirlds.virtualmap.test.fixtures.TestKey;
import com.swirlds.virtualmap.test.fixtures.TestValue;
import com.swirlds.virtualmap.test.fixtures.VirtualTestBase;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
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

        final VirtualRootNode fcm = new VirtualRootNode(builder);
        fcm.postInit(new DummyVirtualStateAccessor());
        fcm.enableFlush();
        fcm.put(A_KEY, APPLE);

        final VirtualRootNode copy = fcm.copy();
        copy.postInit(fcm.getState());

        fcm.getHash();
        final Hash expectedHash = fcm.getChild(0).getHash();
        fcm.release();
        fcm.waitUntilFlushed();

        final VirtualRootNode fcm2 = new VirtualRootNode(builder);
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
        fcm.put(A_KEY, APPLE);

        final VirtualRootNode copy = fcm.copy();
        copy.postInit(fcm.getState());
        fcm.release();
        fcm.waitUntilFlushed();

        final Bytes removed = copy.remove(A_KEY);
        assertEquals(APPLE, removed, "Wrong value");

        // FUTURE WORK validate hashing works as expected

        copy.release();
    }

    @Test
    @DisplayName("Remove element twice")
    void removeElementTwice() throws ExecutionException, InterruptedException {
        final VirtualRootNode fcm = createRoot();
        fcm.enableFlush();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        fcm.put(C_KEY, CHERRY);

        final VirtualRootNode copy = fcm.copy();
        copy.postInit(fcm.getState());
        fcm.release();
        fcm.waitUntilFlushed();

        final Bytes removed = copy.remove(B_KEY);
        final Bytes removed2 = copy.remove(B_KEY);
        assertEquals(BANANA, removed, "Wrong value");
        assertNull(removed2, "Expected null");
        copy.release();
    }

    @Test
    @DisplayName("Remove elements in reverse order")
    void removeInReverseOrder() throws ExecutionException, InterruptedException {
        final VirtualRootNode fcm = createRoot();
        fcm.enableFlush();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        fcm.put(C_KEY, CHERRY);
        fcm.put(D_KEY, DATE);
        fcm.put(E_KEY, EGGPLANT);
        fcm.put(F_KEY, FIG);
        fcm.put(G_KEY, GRAPE);

        final VirtualRootNode copy = fcm.copy();
        copy.postInit(fcm.getState());
        fcm.release();
        fcm.waitUntilFlushed();

        assertEquals(GRAPE, copy.remove(G_KEY), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY, E_KEY, C_KEY, F_KEY, B_KEY, D_KEY);
        assertEquals(FIG, copy.remove(F_KEY), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY, E_KEY, C_KEY, B_KEY, D_KEY);
        assertEquals(EGGPLANT, copy.remove(E_KEY), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY, C_KEY, B_KEY, D_KEY);
        assertEquals(DATE, copy.remove(D_KEY), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY, C_KEY, B_KEY);
        assertEquals(CHERRY, copy.remove(C_KEY), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY, B_KEY);
        assertEquals(BANANA, copy.remove(B_KEY), "Wrong value");
        //        assertLeafOrder(fcm, A_KEY);
        assertEquals(APPLE, copy.remove(A_KEY), "Wrong value");

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
        final VirtualRootNode root2 = createRoot();

        deserializeRootNodeAndVerify(
                new FileInputStream(tempDir.resolve(fileName).toFile()), ClassVersion.CURRENT_VERSION);
    }

    private void deserializeRootNodeAndVerify(InputStream resourceAsStream, int version) throws IOException {
        final VirtualRootNode root = createRoot();

        try (SerializableDataInputStream input = new SerializableDataInputStream(resourceAsStream)) {
            root.deserialize(input, tempDir, version);
            root.postInit(new DummyVirtualStateAccessor());
            for (int i = 0; i < 100; i++) {
                if (i % 7 != 0) {
                    assertEquals(TestValue.longToValue(i), root.get(TestKey.longToKey(i)));
                } else {
                    assertNull(root.get(TestKey.longToKey(i)));
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
            root.put(TestKey.longToKey(i), TestValue.longToValue(i));
            if (i % 7 == 0) {
                keysToRemove.add(TestKey.longToKey(i));
            }
        }

        for (Bytes key : keysToRemove) {
            root.remove(key);
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
            final Bytes value = TestValue.longToValue(index);
            root1.put(key, value);
        }

        final VirtualRootNode root2 = createRoot();
        final long firstLeafPath = root1.getState().getFirstLeafPath();
        final long lastLeafPath = root1.getState().getLastLeafPath();
        for (long index = firstLeafPath; index <= lastLeafPath; index++) {
            final VirtualLeafBytes leaf = root1.getRecords().findLeafRecord(index, false);
            final Bytes key = leaf.keyBytes().replicate();
            final Bytes value = leaf.valueBytes().replicate();
            root2.put(key, value);
        }

        for (int index = 0; index < totalSize; index++) {
            final Bytes key = TestKey.longToKey(index);
            root1.remove(key);
        }

        assertTrue(root1.isEmpty(), "All elements have been removed");
        root1.release();
        TimeUnit.MILLISECONDS.sleep(100);
        System.gc();
        assertEquals(totalSize, root2.size(), "New map still has all data");
        for (int index = 0; index < totalSize; index++) {
            final Bytes key = TestKey.longToKey(index);
            final Bytes expectedValue = TestValue.longToValue(index);
            final Bytes value = root2.get(key);
            assertEquals(expectedValue, value, "Values have the same content");
        }
    }

    @Test
    @DisplayName("Detach Test")
    void detachTest() {
        final List<Path> paths = new LinkedList<>();
        paths.add(Path.of("asdf"));
        paths.add(null);
        for (final Path destination : paths) {
            final VirtualMap original = new VirtualMap("test", new InMemoryBuilder());
            final VirtualMap copy = original.copy();

            final VirtualRootNode root = original.getChild(1);
            root.getHash(); // forces copy to become hashed
            root.getPipeline().detachCopy(root, destination);
            assertTrue(root.isDetached(), "root should be detached");

            original.release();
            copy.release();
        }
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
        ConfigurationHolder.getInstance().setConfiguration(configuration);

        final VirtualMapConfig config = ConfigurationHolder.getConfigData(VirtualMapConfig.class);

        VirtualRootNode root = createRoot();
        assertEquals(0, root.getFlushThreshold());
        final int flushInterval = config.flushInterval();
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
            root.put(TestKey.longToKey(index), TestValue.longToValue(nextInt()));
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
        assertEquals(3, createRoot().getVersion());
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
