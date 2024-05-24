/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.test.fixtures;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;

/**
 * Methods for testing {@link VirtualMap}.
 */
public final class VirtualMapTestUtils {

    private VirtualMapTestUtils() {}

    // Most tests run with in-memory data source builder. However, some tests can only be run with
    // MerkleDb builder, e.g. tests for (de)serialization. This is a helper method to create such
    // a builder
    public static VirtualDataSourceBuilder<TestKey, TestValue> createMerkleDbBuilder() {
        MerkleDb.resetDefaultInstancePath();
        final MerkleDbTableConfig<TestKey, TestValue> tableConfig = new MerkleDbTableConfig<>(
                (short) 1,
                DigestType.SHA_384,
                (short) 1,
                new TestKey.Serializer(),
                (short) 1,
                new TestValue.Serializer());
        return new MerkleDbDataSourceBuilder<>(tableConfig);
    }

    public static VirtualMap<TestKey, TestValue> createMap() {
        return createMap("Test");
    }

    public static VirtualMap<TestKey, TestValue> createMap(final String label) {
        return createMap(label, new InMemoryBuilder());
    }

    public static VirtualMap<TestKey, TestValue> createMap(
            final String label, final VirtualDataSourceBuilder<TestKey, TestValue> builder) {
        return new VirtualMap<>(label, builder);
    }

    public static VirtualRootNode<TestKey, TestValue> createRoot() {
        return createRoot(new InMemoryBuilder());
    }

    public static VirtualRootNode<TestKey, TestValue> createRoot(
            final VirtualDataSourceBuilder<TestKey, TestValue> dataSourceBuilder) {
        final VirtualRootNode<TestKey, TestValue> root = new VirtualRootNode<>(dataSourceBuilder);
        root.postInit(new DummyVirtualStateAccessor());
        return root;
    }

    public static VirtualRootNode<TestKey, TestValue> getRoot(VirtualMap<TestKey, TestValue> map) {
        return map.getChild(1);
    }
}
