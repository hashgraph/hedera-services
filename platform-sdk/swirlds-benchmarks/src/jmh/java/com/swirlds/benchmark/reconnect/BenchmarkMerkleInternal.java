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

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

public class BenchmarkMerkleInternal extends PartialNaryMerkleInternal implements BenchmarkMerkleNode, MerkleInternal {

    private final String value;

    private static final long CLASS_ID = 0x86753092L;

    public static final int CLASS_VERSION = 1;

    private boolean initialized;

    private final AtomicBoolean released = new AtomicBoolean(false);

    private static final BiFunction<BenchmarkMerkleInternal, Integer, MerkleNode> migrationMapper =
            (node, version) -> node;

    public BenchmarkMerkleInternal(String value) {
        this.value = value;
        this.initialized = false;
    }

    public BenchmarkMerkleInternal() {
        this("?");
        initialized = false;
    }

    private BenchmarkMerkleInternal(final BenchmarkMerkleInternal that) {
        super(that);
        initialized = that.initialized;
        value = that.value;
    }

    @Override
    public void rebuild() {
        if (initialized) {
            throw new RuntimeException("Node should only be initialized once.");
        }
        int numberOfChildren = getNumberOfChildren();
        for (int childIndex = 0; childIndex < numberOfChildren; childIndex++) {
            MerkleNode child = getChild(childIndex);
            if (child instanceof BenchmarkMerkleNode && child.isInternal()) {
                BenchmarkMerkleInternal internalChild = (BenchmarkMerkleInternal) child;
                if (!internalChild.isInitialized()) {
                    throw new RuntimeException("Children must be initialized before their parents.");
                }
            }
        }

        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getClassVersion() {
        return CLASS_VERSION;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(value);
        sb.append("](");
        for (int childIndex = 0; childIndex < getNumberOfChildren(); childIndex++) {
            MerkleNode next = getChild(childIndex);
            sb.append(" ");
            String str = next == null ? "null" : next.toString();
            sb.append(str);
            sb.append(" ");
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroyNode() {
        if (!released.compareAndSet(false, true)) {
            throw new IllegalStateException("This type of node should only be deleted once");
        }
    }

    @Override
    public BenchmarkMerkleInternal copy() {
        return new BenchmarkMerkleInternal(this);
    }

    /**
     * When this node migrates, it will always replace itself with a DummyMerkleInternal containing a single leaf
     * in position 0, and that leaf will equal this node.
     */
    @Override
    public MerkleNode migrate(final int version) {
        return migrationMapper.apply(this, version);
    }
}
