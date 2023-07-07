/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.proof;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An internal node in a state proof tree.
 */
public class StateProofInternalNode implements StateProofNode {

    private static final long CLASS_ID = 0x63b15d54dec207dfL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private List<StateProofNode> children;

    /**
     * Zero arg constructor required by the serialization framework.
     */
    public StateProofInternalNode() {}

    /**
     * Construct a new state proof internal node from the given merkle internal node.
     *
     * @param node the merkle internal node
     */
    public StateProofInternalNode(@NonNull final MerkleInternal node) { // TODO this is probably the wrong API

        // TODO construct child list

    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public byte[] getHashableBytes(@NonNull final Cryptography cryptography) {
        if (children == null) {
            throw new IllegalStateException("StateProofInternalNode has not been properly initialized");
        }
        return new byte[0]; // TODO
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<MerkleLeaf> getPayloads() {
        if (children == null) {
            throw new IllegalStateException("StateProofInternalNode has not been properly initialized");
        }

        // Recursively get the payloads of descendants.
        final List<MerkleLeaf> payloads = new ArrayList<>();
        for (final StateProofNode child : children) {
            payloads.addAll(child.getPayloads());
        }
        return payloads;
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
    public void serialize(@NonNull SerializableDataOutputStream out) throws IOException {
        out.writeInt(children.size());
        for (final StateProofNode child : children) {
            out.writeSerializable(child, true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull SerializableDataInputStream in, int version) throws IOException {
        final int childCount = in.readInt();
        // TODO throw if too big

        children = new ArrayList<>(childCount);
        for (int i = 0; i < childCount; i++) {
            children.add(in.readSerializable());
        }
    }
}
