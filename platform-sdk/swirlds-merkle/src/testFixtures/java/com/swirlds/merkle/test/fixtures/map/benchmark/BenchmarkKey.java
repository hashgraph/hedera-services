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

package com.swirlds.merkle.test.fixtures.map.benchmark;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * An account key for an MerkleMap benchmark.
 */
public class BenchmarkKey implements SelfSerializable {

    private static final long CLASS_ID = 0x41db5e9d036e36feL;

    /**
     * The actual key.
     */
    private long key1;

    /**
     * Not really used for anything, but makes this key more similar in size to a real key in hedera.
     */
    private long key2;

    /**
     * Not really used for anything, but makes this key more similar in size to a real key in hedera.
     */
    private long key3;

    public BenchmarkKey() {}

    public BenchmarkKey(final long key) {
        key1 = key;
        key2 = key + 1;
        key3 = key + 2;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        key1 = in.readLong();
        key2 = in.readLong();
        key3 = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(key1);
        out.writeLong(key2);
        out.writeLong(key3);
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
    public int getClassVersion() {
        return 1;
    }

    /**
     * For the purpose of the benchmark, we ensure that key1 is unique across all keys, so it is safe to use key1
     * as a unique identifier for a key.
     */
    public long getValue() {
        return key1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final BenchmarkKey that = (BenchmarkKey) o;
        return key1 == that.key1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(key1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "(" + key1 + ", " + key2 + ", " + key3 + ")";
    }
}
