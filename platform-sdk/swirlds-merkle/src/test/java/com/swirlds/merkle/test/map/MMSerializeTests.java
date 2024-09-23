/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.test.map;

import static com.swirlds.common.test.fixtures.ConfigurationUtils.configuration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.common.test.fixtures.dummy.Key;
import com.swirlds.common.test.fixtures.dummy.Value;
import com.swirlds.common.test.fixtures.junit.tags.TestComponentTags;
import com.swirlds.common.test.fixtures.merkle.dummy.DummyMerkleInternal;
import com.swirlds.common.test.fixtures.merkle.dummy.DummyMerkleInternal2;
import com.swirlds.common.test.fixtures.merkle.util.MerkleSerializeUtils;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.map.internal.MerkleMapInfo;
import com.swirlds.merkle.test.fixtures.map.util.KeyValueProvider;
import java.io.IOException;
import java.nio.file.Path;

import com.swirlds.merkle.tree.MerkleBinaryTree;
import com.swirlds.merkle.tree.MerkleTreeInternalNode;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MMSerializeTests {

    private static MerkleCryptography cryptography;
    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructable(new ClassConstructorPair(MerkleMap.class, MerkleMap::new));
        registry.registerConstructable(new ClassConstructorPair(MerkleBinaryTree.class, MerkleBinaryTree::new));
        registry.registerConstructable(new ClassConstructorPair(MerkleMapInfo.class, MerkleMapInfo::new));
        registry.registerConstructable(new ClassConstructorPair(MerkleLong.class, MerkleLong::new));
        registry.registerConstructable(new ClassConstructorPair(MerkleTreeInternalNode.class, MerkleTreeInternalNode::new));
        registry.registerConstructable(new ClassConstructorPair(Value.class, Value::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualMap.class, () -> new VirtualMap(configuration())));
        cryptography = MerkleCryptoFactory.getInstance();
    }

    @Test
    @Tag(TestComponentTags.MMAP)
    @DisplayName("Add value to deserialized map")
    void serializeDeserializeAdd() throws IOException {
        final MerkleMap<Key, Value> fcm = new MerkleMap<>();
        KeyValueProvider.KEY_VALUE.insertIntoMap(0, 100, fcm);
        cryptography.digestTreeSync(fcm);
        final MerkleMap<Key, Value> serDes = MerkleSerializeUtils.serializeDeserialize(testDirectory, fcm);
        cryptography.digestTreeSync(serDes);
        KeyValueProvider.KEY_VALUE.insertIntoMap(100, 200, serDes);
        assertEquals(200, serDes.size(), "expected to deserialize 200 entries");
        final Value value = serDes.get(new Key(0, 0, 0));
        assertNotNull(value, "value should be present in the map");

        fcm.release();
        serDes.release();
    }
}
