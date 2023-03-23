/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VirtualLeafNodeTest {

    private static final Random RANDOM = new Random(223);
    private static final Cryptography CRYPTO = CryptographyHolder.get();

    @BeforeAll
    public static void globalSetup() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructable(new ClassConstructorPair(VirtualLeafRecord.class, VirtualLeafRecord::new));
        registry.registerConstructable(new ClassConstructorPair(TestKey.class, TestKey::new));
        registry.registerConstructable(new ClassConstructorPair(TestValue.class, TestValue::new));
        registry.registerConstructable(new ClassConstructorPair(Hash.class, Hash::new));
    }

    @Test
    void copyNotSupported() {
        final VirtualLeafRecord<TestKey, TestValue> leafRecord = new VirtualLeafRecord<>(1, null, null);
        final VirtualRootNode<TestKey, TestValue> root = createRoot();
        final VirtualLeafNode<TestKey, TestValue> virtualLeaf = new VirtualLeafNode<>(leafRecord, null);
        assertThrows(UnsupportedOperationException.class, virtualLeaf::copy, "Copy is not supported");
    }

    @Test
    void toStringTest() {
        // Shameless test to cover toString. All I really care is it doesn't throw an NPE.
        final VirtualLeafRecord<TestKey, TestValue> leafRecord = new VirtualLeafRecord<>(1, null, null);
        final VirtualRootNode<TestKey, TestValue> root = createRoot();
        final VirtualLeafNode<TestKey, TestValue> leaf = new VirtualLeafNode<>(leafRecord, null);
        assertNotNull(leaf.toString(), "leaf should not have a null string");

        // a few addition tests that also just juice the coverage numbers
        assertEquals(leaf, leaf, "A VirtualNode should always be equal to itself.");
        assertNotEquals(leaf, leafRecord, "A VirtualNode should never be equal to a non-VirtualNode value.");
        assertEquals(leaf.hashCode(), leaf.hashCode(), "A VirtualNode's hashCode() should remain constant.");
    }
}
