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

package com.swirlds.benchmark.reconnect;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

public class BenchmarkMerkleLeaf extends PartialMerkleLeaf implements BenchmarkMerkleNode, MerkleLeaf {

    protected String value;

    private static final long CLASS_ID = 0x8675309L;

    public static final int CLASS_VERSION = 1;

    private final AtomicBoolean released = new AtomicBoolean(false);

    private static final BiFunction<BenchmarkMerkleLeaf, Integer, MerkleNode> migrationMapper = (node, version) -> node;

    public BenchmarkMerkleLeaf() {
        this("?");
    }

    public BenchmarkMerkleLeaf(String value) {
        if (value.length() > 1024) {
            throw new RuntimeException("Value must not exceed 1024 characters");
        }
        this.value = value;
    }

    private BenchmarkMerkleLeaf(final BenchmarkMerkleLeaf that) {
        super(that);
        this.value = that.value;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeNormalisedString(value);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        value = in.readNormalisedString(1024);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public void destroyNode() {
        if (!released.compareAndSet(false, true)) {
            throw new IllegalStateException("This type of node should only be deleted once");
        }
    }

    @Override
    public void setHash(Hash hash) {
        if (getHash() != null && hash != null) {
            throw new RuntimeException("Hash should not be set if value is already known");
        }
        super.setHash(hash);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public BenchmarkMerkleLeaf copy() {
        throwIfImmutable();
        throwIfDestroyed();
        setImmutable(true);
        return new BenchmarkMerkleLeaf(this);
    }

    @Override
    public int hashCode() {
        if (value == null) {
            return 0;
        } else {
            return value.hashCode();
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BenchmarkMerkleLeaf)) {
            return false;
        }
        BenchmarkMerkleLeaf that = (BenchmarkMerkleLeaf) obj;
        return Objects.equals(this.value, that.value);
    }

    /**
     * When this node migrates, it will always replace itself with a BenchmarkMerkleInternal containing a single leaf
     * in position 0, and that leaf will equal this node.
     */
    @Override
    public MerkleNode migrate(final int version) {
        return migrationMapper.apply(this, version);
    }
}
