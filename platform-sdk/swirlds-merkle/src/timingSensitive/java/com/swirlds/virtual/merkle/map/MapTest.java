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

package com.swirlds.virtual.merkle.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.virtual.merkle.TestKey;
import com.swirlds.virtual.merkle.TestKeySerializer;
import com.swirlds.virtual.merkle.TestObjectKey;
import com.swirlds.virtual.merkle.TestObjectKeySerializer;
import com.swirlds.virtual.merkle.TestValue;
import com.swirlds.virtual.merkle.TestValueSerializer;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import org.junit.jupiter.api.*;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

final class MapTest {

    private static Configuration CONFIGURATION = ConfigurationBuilder.create()
            .withConfigDataType(VirtualMapConfig.class)
            .withConfigDataType(MerkleDbConfig.class)
            .withConfigDataType(TemporaryFileConfig.class)
            .withConfigDataType(StateCommonConfig.class)
            .build();
    private static final MerkleDbConfig MERKLE_DB_CONFIG = CONFIGURATION.getConfigData(MerkleDbConfig.class);
    private static final MerkleDbTableConfig TABLE_CONFIG = new MerkleDbTableConfig(
            (short) 1,
            DigestType.SHA_384,
            MERKLE_DB_CONFIG.maxNumOfKeys(),
            MERKLE_DB_CONFIG.hashesRamToDiskThreshold());

    VirtualDataSourceBuilder createLongBuilder() {
        return new MerkleDbDataSourceBuilder(TABLE_CONFIG, CONFIGURATION);
    }

    VirtualDataSourceBuilder createGenericBuilder() {
        return new MerkleDbDataSourceBuilder(TABLE_CONFIG, CONFIGURATION);
    }

    VirtualMap<TestKey, TestValue> createLongMap(String label) {
        return new VirtualMap<>(
                label, new TestKeySerializer(), new TestValueSerializer(), createLongBuilder(), CONFIGURATION);
    }

    VirtualMap<TestObjectKey, TestValue> createObjectMap(String label) {
        return new VirtualMap<>(
                label, new TestObjectKeySerializer(), new TestValueSerializer(), createGenericBuilder(), CONFIGURATION);
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VMAP-019")})
    @DisplayName("Insert one million elements with same key but different value")
    void insertRemoveAndModifyOneMillion() throws InterruptedException {
        final int changesPerBatch = 15_432; // Some unexpected size just to be crazy
        final int max = 1_000_000;
        VirtualMap<TestKey, TestValue> map = createLongMap("insertRemoveAndModifyOneMillion");
        try {
            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.put(new TestKey(i), new TestValue(i));
            }

            for (int i = 0; i < max; i++) {
                assertEquals(new TestValue(i), map.get(new TestKey(i)), "Expected same");
            }

            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.remove(new TestKey(i));
            }

            assertTrue(map.isEmpty(), "Map should be empty");

            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.put(new TestKey(i + max), new TestValue(i + max));
            }

            for (int i = 0; i < max; i++) {
                assertEquals(new TestValue(i + max), map.get(new TestKey(i + max)), "Expected same");
                assertNull(map.get(new TestKey(i)), "The old value should not exist anymore");
            }
        } finally {
            map.release();
        }
    }

    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("Delete a value that was moved to a different virtual path")
    void deletedObjectLeavesOnFlush() throws InterruptedException {
        VirtualMap<TestObjectKey, TestValue> map = createObjectMap("deletedObjectLeavesOnFlush");
        for (int i = 0; i < 8; i++) {
            map.put(new TestObjectKey(i), new TestValue(i));
        }

        VirtualRootNode<TestObjectKey, TestValue> rootNode = map.getRight();
        rootNode.enableFlush();

        RecordAccessor<TestObjectKey, TestValue> records = rootNode.getRecords();
        // Check that key/value 0 is at path 7
        VirtualLeafRecord<TestObjectKey, TestValue> leaf = records.findLeafRecord(7, false);
        assertNotNull(leaf);
        assertEquals(new TestObjectKey(0), leaf.getKey());
        assertEquals(new TestValue(0), leaf.getValue());

        VirtualMap<TestObjectKey, TestValue> copy = map.copy();
        map.release();
        map = copy;
        rootNode.waitUntilFlushed();

        // Move key/value to a different path, then delete
        map.remove(new TestObjectKey(0));
        map.remove(new TestObjectKey(2));
        map.put(new TestObjectKey(8), new TestValue(8));
        map.put(new TestObjectKey(0), new TestValue(0));
        map.remove(new TestObjectKey(0));

        rootNode = map.getRight();
        rootNode.enableFlush();

        copy = map.copy();
        map.release();
        map = copy;
        rootNode.waitUntilFlushed();

        // During this second flush, key/value 0 must be deleted from the map despite it's
        // path the virtual tree doesn't match the path in the data source
        assertFalse(map.containsKey(new TestObjectKey(0)));
        assertNull(map.get(new TestObjectKey(0)));

        map.release();
    }

    private static final Random RANDOM = new Random();

    @RepeatedTest(1000)
    void testVirtualMapOperations() throws InterruptedException {
        VirtualMap<TestObjectKey, TestValue> map = createObjectMap("LSEnodeIssue" +  + System.nanoTime());

        int addCount = RANDOM.nextInt(4);
        for (int i = 0; i < addCount; i++) {
            map.put(new TestObjectKey(i), new TestValue(i));
        }

        VirtualRootNode<TestObjectKey, TestValue> rootNode = map.getRight();
        rootNode.enableFlush();

        VirtualMap<TestObjectKey, TestValue> copy = map.copy();
        map.release();
        map = copy;
        rootNode.waitUntilFlushed();

        System.out.printf("Before: First Leaf Path = %d, Last Leaf Path = %d%n",
                map.getDataSource().getFirstLeafPath(), map.getDataSource().getLastLeafPath());

        Set<TestObjectKey> deletedKeys = new HashSet<>();
        int removeCount = RANDOM.nextInt(addCount + 1);
        for (int i = 0; i < removeCount; i++) {
            int keyToRemove = RANDOM.nextInt(addCount);
            TestObjectKey key = new TestObjectKey(keyToRemove);
            deletedKeys.add(key);
            map.remove(key);
        }

        rootNode = map.getRight();
        rootNode.enableFlush();

        copy = map.copy();
        map.release();
        map = copy;
        rootNode.waitUntilFlushed();

        System.out.println("Deleted entries: " + deletedKeys.size());
        System.out.printf("After: First Leaf Path = %d, Last Leaf Path = %d%n",
                map.getDataSource().getFirstLeafPath(), map.getDataSource().getLastLeafPath());
        System.out.println("----");

        for (int i = 0; i < addCount; i++) {
            TestObjectKey key = new TestObjectKey(i);
            if (deletedKeys.contains(key)) {
                assertFalse(map.containsKey(key), "Deleted key " + i + " should not exist");
            } else {
                assertTrue(map.containsKey(key), "Existing key " + i + " should still exist");
                assertNotNull(map.get(key), "Entry for key " + i + " should exist");
            }
        }

        map.release();
    }
}
