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

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;

/**
 * Methods for testing {@link VirtualMap}.
 */
public final class VirtualMapTestUtils {

    private VirtualMapTestUtils() {}

    public static Configuration configuration() {
        // TODO: check if MerkleDbConfig, TemporaryFileConfig and StateCommonConfig needed
        return ConfigurationBuilder.create()
                .withConfigDataType(VirtualMapConfig.class)
                .build();
    }

    public static VirtualMapConfig vmConfig() {
        return ConfigurationBuilder.create()
                .withConfigDataType(VirtualMapConfig.class)
                .build()
                .getConfigData(VirtualMapConfig.class);
    }

    public static VirtualMap<TestKey, TestValue> createMap(String label) {
        final VirtualDataSourceBuilder builder = new InMemoryBuilder();
        return new VirtualMap<>(
                label, TestKeySerializer.INSTANCE, TestValueSerializer.INSTANCE, builder, configuration());
    }

    public static VirtualMap<TestKey, TestValue> createMap() {
        return createMap("Test");
    }

    // TODO: refactor

    public static VirtualRootNode<TestKey, TestValue> createRoot() {
        final VirtualRootNode<TestKey, TestValue> root = new VirtualRootNode<>(
                TestKeySerializer.INSTANCE, TestValueSerializer.INSTANCE, new InMemoryBuilder(), configuration());
        root.postInit(new DummyVirtualStateAccessor());
        return root;
    }

    public static VirtualRootNode<TestKey, TestValue> createRoot(final Configuration configuration) {
        final VirtualRootNode<TestKey, TestValue> root = new VirtualRootNode<>(
                TestKeySerializer.INSTANCE, TestValueSerializer.INSTANCE, new InMemoryBuilder(), configuration);
        root.postInit(new DummyVirtualStateAccessor());
        return root;
    }

    public static VirtualRootNode<TestKey, TestValue> getRoot(VirtualMap<TestKey, TestValue> map) {
        return map.getChild(1);
    }
}
