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

import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import com.swirlds.virtualmap.serialize.KeySerializer;
import com.swirlds.virtualmap.serialize.ValueSerializer;

/**
 * Methods for testing {@link VirtualMap}.
 */
public final class VirtualMapTestUtils {

    private static final KeySerializer<TestKey> KEY_SERIALIZER = new TestKeySerializer();

    private static final ValueSerializer<TestValue> VALUE_SERIALIZER = new TestValueSerializer();

    private VirtualMapTestUtils() {}

    public static VirtualMap<TestKey, TestValue> createMap(String label) {
        final VirtualDataSourceBuilder builder = new InMemoryBuilder();
        return new VirtualMap<>(label, KEY_SERIALIZER, VALUE_SERIALIZER, builder);
    }

    public static VirtualMap<TestKey, TestValue> createMap() {
        return createMap("Test");
    }

    public static VirtualRootNode<TestKey, TestValue> createRoot() {
        final VirtualRootNode<TestKey, TestValue> root =
                new VirtualRootNode<>(KEY_SERIALIZER, VALUE_SERIALIZER, new InMemoryBuilder());
        root.postInit(new DummyVirtualStateAccessor());
        return root;
    }

    public static VirtualRootNode<TestKey, TestValue> getRoot(VirtualMap<TestKey, TestValue> map) {
        return map.getChild(1);
    }
}
