/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.virtualmap.VirtualMapTestUtils.createRoot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.Hash;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualTestBase;
import com.swirlds.virtualmap.datasource.InMemoryBuilder;
import com.swirlds.virtualmap.datasource.InMemoryDataSource;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@SuppressWarnings("ALL")
class VirtualRootNodeTest extends VirtualTestBase {

    void testEnableVirtualRootFlush() throws ExecutionException, InterruptedException {
        VirtualRootNode<TestKey, TestValue> fcm0 = createRoot();
        fcm0.postInit(new DummyVirtualStateAccessor());
        assertFalse(fcm0.requestedToFlush(), "map should not yet be flushed");

        VirtualRootNode<TestKey, TestValue> fcm1 = fcm0.copy();
        fcm1.postInit(new DummyVirtualStateAccessor());
        assertFalse(fcm1.requestedToFlush(), "map should not yet be flushed");

        VirtualRootNode<TestKey, TestValue> fcm2 = fcm1.copy();
        fcm2.postInit(new DummyVirtualStateAccessor());
        assertFalse(fcm1.requestedToFlush(), "map should not yet be flushed");

        VirtualRootNode<TestKey, TestValue> fcm3 = fcm2.copy();
        fcm3.postInit(new DummyVirtualStateAccessor());
        fcm3.enableFlush();
        assertTrue(fcm3.requestedToFlush(), "map should now be flushed");

        fcm0.release();
        fcm1.release();
        fcm2.release();
        fcm3.release();
    }

    @Test
    @DisplayName("A new map with a datasource with a root hash reveals it")
    void mapWithExistingHashedDataHasNonNullRootHash() throws ExecutionException, InterruptedException {
        // The builder I will use with this map is unique in that each call to "build" returns THE SAME DATASOURCE.
        final InMemoryDataSource<TestKey, TestValue> ds = new InMemoryDataSource<>(
                "mapWithExistingHashedDataHasNonNullRootHash", TestKey.BYTES, TestKey::new, 1024, TestValue::new);
        final VirtualDataSourceBuilder<TestKey, TestValue> builder = new InMemoryBuilder();

        final VirtualRootNode<TestKey, TestValue> fcm = new VirtualRootNode<>(builder);
        fcm.postInit(new DummyVirtualStateAccessor());
        fcm.enableFlush();
        fcm.put(A_KEY, APPLE);

        final VirtualRootNode<TestKey, TestValue> copy = fcm.copy();
        copy.postInit(fcm.getState());

        fcm.getHash();
        final Hash expectedHash = fcm.getChild(0).getHash();
        fcm.release();
        fcm.waitUntilFlushed();

        final VirtualRootNode<TestKey, TestValue> fcm2 = new VirtualRootNode<>(builder);
        fcm2.postInit(copy.getState());
        assertNotNull(fcm2.getChild(0), "child should not be null");
        assertEquals(expectedHash, fcm2.getChild(0).getHash(), "hash should match expected");

        copy.release();
        fcm2.release();
    }

    @Test
    @DisplayName("Remove only element")
    void removeOnlyElement() throws ExecutionException, InterruptedException {

        final VirtualRootNode<TestKey, TestValue> fcm = createRoot();
        fcm.enableFlush();
        fcm.put(A_KEY, APPLE);

        final VirtualRootNode<TestKey, TestValue> copy = fcm.copy();
        copy.postInit(fcm.getState());
        fcm.release();
        fcm.waitUntilFlushed();

        final TestValue removed = copy.remove(A_KEY);
        assertEquals(APPLE, removed, "Wrong value");

        // TODO validate hashing works as expected

        copy.release();
    }

    @Test
    @DisplayName("Remove element twice")
    void removeElementTwice() throws ExecutionException, InterruptedException {
        final VirtualRootNode<TestKey, TestValue> fcm = createRoot();
        fcm.enableFlush();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        fcm.put(C_KEY, CHERRY);

        final VirtualRootNode<TestKey, TestValue> copy = fcm.copy();
        copy.postInit(fcm.getState());
        fcm.release();
        fcm.waitUntilFlushed();

        final TestValue removed = copy.remove(B_KEY);
        final TestValue removed2 = copy.remove(B_KEY);
        assertEquals(BANANA, removed, "Wrong value");
        assertNull(removed2, "Expected null");
        copy.release();
    }

    @Test
    @DisplayName("Remove elements in reverse order")
    void removeInReverseOrder() throws ExecutionException, InterruptedException {
        final VirtualRootNode<TestKey, TestValue> fcm = createRoot();
        fcm.enableFlush();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        fcm.put(C_KEY, CHERRY);
        fcm.put(D_KEY, DATE);
        fcm.put(E_KEY, EGGPLANT);
        fcm.put(F_KEY, FIG);
        fcm.put(G_KEY, GRAPE);

        final VirtualRootNode<TestKey, TestValue> copy = fcm.copy();
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

        // TODO validate hashing works as expected

        copy.release();
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
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void moveDataAcrossMaps() throws InterruptedException {
        final int totalSize = 1_000_000;
        final VirtualRootNode<TestKey, TestValue> root1 = createRoot();
        for (int index = 0; index < totalSize; index++) {
            final TestKey key = new TestKey(index);
            final TestValue value = new TestValue(index);
            root1.put(key, value);
        }

        final VirtualRootNode<TestKey, TestValue> root2 = createRoot();
        final long firstLeafPath = root1.getState().getFirstLeafPath();
        final long lastLeafPath = root1.getState().getLastLeafPath();
        for (long index = firstLeafPath; index <= lastLeafPath; index++) {
            final VirtualLeafRecord<TestKey, TestValue> leaf =
                    root1.getRecords().findLeafRecord(index, false);
            final TestKey key = leaf.getKey().copy();
            final TestValue value = leaf.getValue().copy();
            root2.put(key, value);
        }

        for (int index = 0; index < totalSize; index++) {
            final TestKey key = new TestKey(index);
            root1.remove(key);
        }

        assertTrue(root1.isEmpty(), "All elements have been removed");
        root1.release();
        TimeUnit.MILLISECONDS.sleep(100);
        System.gc();
        assertEquals(totalSize, root2.size(), "New map still has all data");
        for (int index = 0; index < totalSize; index++) {
            final TestKey key = new TestKey(index);
            final TestValue expectedValue = new TestValue(index);
            final TestValue value = root2.get(key);
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
            final VirtualMap<TestKey, TestValue> original = new VirtualMap<>("test", new InMemoryBuilder());
            final VirtualMap<TestKey, TestValue> copy = original.copy();

            final VirtualRootNode<TestKey, TestValue> root = original.getChild(1);
            root.getHash(); // forces copy to become hashed
            root.getPipeline().detachCopy(root, destination);
            assertTrue(root.isDetached(), "root should be detached");

            original.release();
            copy.release();
        }
    }
}
