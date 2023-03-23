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

package com.swirlds.virtualmap;

import static com.swirlds.virtualmap.VirtualMapTestUtils.createMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.test.merkle.util.MerkleTestUtils;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("VirtualMap Hashing Tests")
class VirtualMapHashingTest {
    private static final MerkleCryptography CRYPTO = MerkleCryptoFactory.getInstance();

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Hash Empty Map")
    void hashEmptyMap() {
        final VirtualMap<TestKey, TestValue> map = createMap();
        final VirtualMap<TestKey, TestValue> copy = map.copy();
        final Hash hash = CRYPTO.digestTreeSync(map);
        assertNotNull(hash, "hash should not be null");

        map.release();
        copy.release();
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Hash Map With One Entry")
    void hashMapWithOneEntry() {
        final VirtualMap<TestKey, TestValue> map = createMap();
        map.put(new TestKey('a'), new TestValue("a"));
        final VirtualMap<TestKey, TestValue> copy = map.copy();

        final Hash hash = MerkleCryptoFactory.getInstance().digestTreeSync(map);
        assertNotNull(hash, "hash should not be null");

        map.release();
        copy.release();
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Hash Map With Many Entries")
    void hashMapWithManyEntries() {
        final VirtualMap<TestKey, TestValue> map0 = createMap();
        for (int i = 0; i < 100; i++) {
            map0.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }

        final VirtualMap<TestKey, TestValue> map1 = map0.copy();
        final Hash hash0 = MerkleCryptoFactory.getInstance().digestTreeSync(map0);
        assertNotNull(hash0, "hash should not be null");

        for (int i = 100; i < 200; i++) {
            map1.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }

        final VirtualMap<TestKey, TestValue> map2 = map1.copy();
        final Hash hash1 = MerkleCryptoFactory.getInstance().digestTreeSync(map1);
        assertNotNull(hash1, "hash should not be null");

        final Hash hash0_2 = MerkleCryptoFactory.getInstance().digestTreeSync(map0);
        assertNotEquals(hash0, hash1, "hash should have changed");
        assertEquals(hash0_2, map0.getHash(), "map should still have the same hash");

        map0.release();
        map1.release();
        map2.release();
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Embedded At Root Sync")
    void embeddedAtRootSync() {

        final VirtualMap<TestKey, TestValue> mapA = createMap();
        for (int i = 0; i < 100; i++) {
            mapA.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }
        final VirtualMap<TestKey, TestValue> copyA = mapA.copy();
        final Hash hashA = MerkleCryptoFactory.getInstance().digestTreeSync(mapA);
        assertNotNull(hashA, "hash should not be null");

        final VirtualMap<TestKey, TestValue> mapB = createMap();
        for (int i = 0; i < 100; i++) {
            mapB.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }
        final VirtualMap<TestKey, TestValue> copyB = mapB.copy();
        final Hash hashB = MerkleCryptoFactory.getInstance().digestTreeSync(mapB);
        assertEquals(hashA, hashB, "both trees should derive the same hash");

        mapA.release();
        mapB.release();
        copyA.release();
        copyB.release();
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Embedded At Root Async")
    void embeddedAtRootAsync() throws ExecutionException, InterruptedException {

        final VirtualMap<TestKey, TestValue> mapA = createMap();
        for (int i = 0; i < 100; i++) {
            mapA.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }
        final VirtualMap<TestKey, TestValue> copyA = mapA.copy();
        final Hash hashA = MerkleCryptoFactory.getInstance().digestTreeSync(mapA);
        assertNotNull(hashA, "hash should not be null");

        final VirtualMap<TestKey, TestValue> mapB = createMap();
        for (int i = 0; i < 100; i++) {
            mapB.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }
        final VirtualMap<TestKey, TestValue> copyB = mapB.copy();
        final Hash hashB = MerkleCryptoFactory.getInstance().digestTreeSync(mapB);
        assertEquals(hashA, hashB, "both trees should derive the same hash");

        mapA.release();
        mapB.release();
        copyA.release();
        copyB.release();
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Embedded In Tree Sync")
    void embeddedInTreeSync() {

        final VirtualMap<TestKey, TestValue> map = createMap();
        for (int i = 0; i < 100; i++) {
            map.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }

        final MerkleInternal root = MerkleTestUtils.buildLessSimpleTreeExtended();

        // Put the map deep into the tree
        root.getChild(2)
                .asInternal()
                .getChild(1)
                .asInternal()
                .getChild(0)
                .asInternal()
                .setChild(2, map);

        final VirtualMap<TestKey, TestValue> copy = map.copy();

        MerkleCryptoFactory.getInstance().digestTreeSync(root);

        assertNotNull(map.getHash(), "map should be hashed");
        assertNotNull(root.getHash(), "tree should be hashed");

        root.release();
        copy.release();
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Embedded In Tree ASync")
    void embeddedInTreeAsync() throws ExecutionException, InterruptedException {

        final VirtualMap<TestKey, TestValue> map = createMap();
        for (int i = 0; i < 100; i++) {
            map.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }

        final MerkleInternal root = MerkleTestUtils.buildLessSimpleTreeExtended();

        // Put the map deep into the tree
        root.getChild(2)
                .asInternal()
                .getChild(1)
                .asInternal()
                .getChild(0)
                .asInternal()
                .setChild(2, map);

        final VirtualMap<TestKey, TestValue> copy = map.copy();

        MerkleCryptoFactory.getInstance().digestTreeAsync(root).get();

        assertNotNull(map.getHash(), "map should be hashed");
        assertNotNull(root.getHash(), "tree should be hashed");

        root.release();
        copy.release();
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Multiple Maps Embedded In Tree Sync")
    void multipleMapsEmbeddedInTreeSync() {

        final VirtualMap<TestKey, TestValue> map0 = createMap();
        for (int i = 0; i < 100; i++) {
            map0.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }

        final VirtualMap<TestKey, TestValue> map1 = createMap();
        for (int i = 100; i < 200; i++) {
            map1.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }

        final VirtualMap<TestKey, TestValue> map2 = createMap();
        for (int i = 200; i < 300; i++) {
            map2.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }

        final MerkleInternal root = MerkleTestUtils.buildLessSimpleTreeExtended();

        // Put the maps into the tree
        root.setChild(3, map0);
        root.getChild(2).asInternal().getChild(1).asInternal().setChild(2, map1);
        root.getChild(2)
                .asInternal()
                .getChild(1)
                .asInternal()
                .getChild(0)
                .asInternal()
                .setChild(2, map2);

        final VirtualMap<TestKey, TestValue> copy0 = map0.copy();
        final VirtualMap<TestKey, TestValue> copy1 = map1.copy();
        final VirtualMap<TestKey, TestValue> copy2 = map2.copy();

        MerkleCryptoFactory.getInstance().digestTreeSync(root);

        assertNotNull(map0.getHash(), "map should be hashed");
        assertNotNull(map1.getHash(), "map should be hashed");
        assertNotNull(map2.getHash(), "map should be hashed");
        assertNotNull(root.getHash(), "tree should be hashed");

        root.release();
        copy0.release();
        copy1.release();
        copy2.release();
    }

    @ParameterizedTest
    @CsvSource({"1,2", "1,3", "1,4", "1,5", "2,3", "2,4", "2,5", "3,4", "3,5", "4,5"})
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Delete some tree nodes and hash")
    void hashBugFoundByPTT(long delete1, long delete2) {
        final VirtualMap<TestKey, TestValue> map0 = createMap();
        map0.put(new TestKey(1), new TestValue(1));
        map0.put(new TestKey(2), new TestValue(2));
        map0.put(new TestKey(3), new TestValue(3));
        map0.put(new TestKey(4), new TestValue(4));
        map0.put(new TestKey(5), new TestValue(5));

        map0.remove(new TestKey(delete1));
        map0.remove(new TestKey(delete2));

        final VirtualMap<TestKey, TestValue> map1 = map0.copy();
        final Hash hash0 = MerkleCryptoFactory.getInstance().digestTreeSync(map0);
        assertNotNull(hash0, "hash should not be null");

        map0.release();
        map1.release();
    }

    /**
     * This test failed prior to a race condition that used to exist.
     */
    @ParameterizedTest
    @ValueSource(
            ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 16, 17, 30, 31, 32, 33, 62, 64, 120, 256, 1000, 1022, 1023, 1024
            })
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Internal node operations are properly synchronized")
    void internalNodeSynchronization(int nKeys) throws ExecutionException, InterruptedException {
        VirtualMap<TestKey, TestValue> current = createMap();
        for (int i = 0; i < nKeys; ++i) {
            current.put(new TestKey(i), new TestValue(Integer.toString(i)));
        }

        final VirtualMap<TestKey, TestValue> prev = current;
        current = current.copy();
        Future<Hash> future = MerkleCryptoFactory.getInstance().digestTreeAsync(prev);

        final long numInternals = current.getState().getFirstLeafPath();
        for (int i = 0; i < nKeys; ++i) {
            current.remove(new TestKey(i));
        }

        future.get();
        prev.release();

        final VirtualNodeCache<TestKey, TestValue> cache = current.getRoot().getCache();
        int deletedInternals = 1; // path 0 internal node is preserved for an empty map
        for (int path = 1; path < numInternals; ++path) {
            final Hash hash = cache.lookupHashByPath(path, false);
            assertNotNull(hash, "Unexpected null");
            if (hash == VirtualNodeCache.DELETED_HASH) {
                deletedInternals++;
            }
        }
        assertEquals(numInternals, deletedInternals, "The number of deleted internals doesn't match");
    }
}
