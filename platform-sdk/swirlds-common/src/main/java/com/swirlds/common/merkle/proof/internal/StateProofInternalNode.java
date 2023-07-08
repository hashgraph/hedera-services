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

package com.swirlds.common.merkle.proof.internal;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An internal node in a state proof tree.
 */
public class StateProofInternalNode extends AbstractStateProofNode {

    private static final long CLASS_ID = 0x63b15d54dec207dfL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * Child state proof nodes.
     */
    private List<StateProofNode> children;

    private boolean visited = false;

    /**
     * Zero arg constructor required by the serialization framework.
     */
    public StateProofInternalNode() {}

    /**
     * Construct a new state proof internal node from the given merkle internal node.
     *
     * @param children the children of the internal node
     */
    public StateProofInternalNode(@NonNull final List<StateProofNode> children) {
        this.children = Objects.requireNonNull(children);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void computeHashableBytes(@NonNull final Cryptography cryptography, @NonNull final MessageDigest digest) {
        if (children == null) {
            throw new IllegalStateException("StateProofInternalNode has not been properly initialized");
        }

        for (final StateProofNode child : children) {
            digest.update(child.getHashableBytes());
        }

        setHashableBytes(digest.digest());
        digest.reset();
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
     * Get the child state proof nodes.
     */
    @NonNull
    public List<StateProofNode> getChildren() {
        return children;
    }

    /**
     * Mark this node as having been visited during a traversal of the state proof tree. Used during state proof
     * validation.
     */
    public void markAsVisited() {
        if (visited) {
            throw new IllegalStateException("Node has already been visited");
        }
        visited = true;
    }

    /**
     * Check if this node has been visited during a traversal of the state proof tree. Used during state proof
     * validation.
     *
     * @return true if {@link #markAsVisited()} has been called on this node, false otherwise
     */
    public boolean hasBeenVisited() {
        return visited;
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
