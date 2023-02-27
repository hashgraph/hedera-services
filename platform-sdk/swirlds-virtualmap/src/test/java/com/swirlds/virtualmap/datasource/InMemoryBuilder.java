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

package com.swirlds.virtualmap.datasource;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A utility class for testing purposes. Each named {@link InMemoryDataSource} is stored in a map.
 */
public class InMemoryBuilder implements VirtualDataSourceBuilder<TestKey, TestValue> {

    private final Map<String, InMemoryDataSource<TestKey, TestValue>> databases = new ConcurrentHashMap<>();

    private static final long CLASS_ID = 0x29e653a8c81959b8L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InMemoryDataSource<TestKey, TestValue> build(final String label, final boolean withDbCompactionEnabled) {
        return databases.computeIfAbsent(label, (s) -> createDataSource(label));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InMemoryDataSource<TestKey, TestValue> copy(final VirtualDataSource<TestKey, TestValue> snapshotMe) {
        final InMemoryDataSource<TestKey, TestValue> source = (InMemoryDataSource<TestKey, TestValue>) snapshotMe;
        final InMemoryDataSource<TestKey, TestValue> snapshot = new InMemoryDataSource<>(source);
        databases.put(createUniqueDataSourceName(source.getName()), snapshot);
        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void snapshot(final Path to, final VirtualDataSource<TestKey, TestValue> snapshotMe) {
        //		final InMemoryDataSource<TestKey, TestValue> source = (InMemoryDataSource<TestKey, TestValue>) snapshotMe;
        //		final InMemoryDataSource<TestKey, TestValue> snapshot = new InMemoryDataSource<>(source);
        //		databases.put(createUniqueDataSourceName(source.getName()), snapshot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualDataSource<TestKey, TestValue> restore(final String label, final Path from) {
        // FUTURE WORK: determine if there really is something that needs to be done here.
        return null;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        // no configuration data to serialize
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        // no configuration data to deserialize
    }

    protected InMemoryDataSource<TestKey, TestValue> createDataSource(final String name) {
        return new InMemoryDataSource<>(name, TestKey.BYTES, TestKey::new, 1024, TestValue::new);
    }

    private String createUniqueDataSourceName(final String name) {
        return name + "-" + System.currentTimeMillis();
    }
}
