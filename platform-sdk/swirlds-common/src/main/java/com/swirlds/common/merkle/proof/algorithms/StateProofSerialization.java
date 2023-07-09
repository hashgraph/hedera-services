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

package com.swirlds.common.merkle.proof.algorithms;

import static com.swirlds.common.merkle.proof.algorithms.StateProofConstants.MAX_CHILD_COUNT;
import static com.swirlds.common.merkle.proof.algorithms.StateProofConstants.MAX_SIGNATURE_COUNT;
import static com.swirlds.common.merkle.proof.algorithms.StateProofConstants.MAX_STATE_PROOF_TREE_SIZE;

import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.io.extendable.ExtendableOutputStream;
import com.swirlds.common.io.extendable.extensions.MaxSizeStreamExtension;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.proof.tree.StateProofInternalNode;
import com.swirlds.common.merkle.proof.tree.StateProofNode;
import com.swirlds.common.merkle.proof.tree.StateProofPayload;
import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Serialization and deserialization logic for state proofs.
 */
public final class StateProofSerialization {

    private StateProofSerialization() {}

    /**
     * Serialize a state proof's signatures.
     *
     * @param out        the output stream
     * @param signatures the signatures to serialize
     * @throws IOException if an IO error occurs
     */
    public static void serializeSignatures(
            @NonNull final SerializableDataOutputStream out, @NonNull final List<NodeSignature> signatures)
            throws IOException {

        // Better to fail early than to fail whenever somebody attempts to deserialize.
        if (signatures.size() > MAX_SIGNATURE_COUNT) {
            throw new IOException("too many signatures: " + signatures.size() + ", limit: " + MAX_SIGNATURE_COUNT);
        }

        out.writeInt(signatures.size());
        for (final NodeSignature entry : signatures) {
            out.writeSerializable(entry.nodeId(), false);
            out.writeSerializable(entry.signature(), false);
        }
    }

    /**
     * Deserialize a state proof's signatures.
     *
     * @param in the input stream
     * @return the deserialized signatures
     * @throws IOException if an IO error occurs
     */
    @NonNull
    public static List<NodeSignature> deserializeSignatures(@NonNull final SerializableDataInputStream in)
            throws IOException {
        final int numSignatures = in.readInt();
        if (numSignatures > MAX_SIGNATURE_COUNT) {
            throw new IOException("too many signatures: " + numSignatures + ", limit: " + MAX_SIGNATURE_COUNT);
        }
        final List<NodeSignature> signatures = new ArrayList<>();
        for (int i = 0; i < numSignatures; i++) {
            final NodeId nodeId = in.readSerializable(false, NodeId::new);
            if (nodeId == null) {
                throw new IOException("nodeId is null");
            }
            final Signature signature = in.readSerializable(false, Signature::new);
            if (signature == null) {
                throw new IOException("signature is null");
            }

            signatures.add(new NodeSignature(nodeId, signature));
        }
        return signatures;
    }

    /**
     * Serialize a state proof tree.
     *
     * @param out  the stream to write to
     * @param root the root of the tree to serialize
     * @throws IOException if an IO error occurs
     */
    public static void serializeStateProofTree(
            @NonNull final SerializableDataOutputStream out, @NonNull final StateProofNode root) throws IOException {

        // This stream will throw an IO exception if asked to read more than MAX_STATE_PROOF_TREE_SIZE bytes.
        // This check is not needed at serialization time for the sake of safety. But if this check fails, then
        // we can expect it to fail at deserialization time as well, so we might as well fail fast.
        final SerializableDataOutputStream limitedStream = new SerializableDataOutputStream(
                new ExtendableOutputStream(out, new MaxSizeStreamExtension(MAX_STATE_PROOF_TREE_SIZE, false)));

        // Walk the tree in BFS order.
        final Queue<StateProofNode> queue = new LinkedList<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            final StateProofNode next = queue.remove();

            if (next instanceof final StateProofInternalNode internal) {
                limitedStream.writeBoolean(true);

                // Better to fail early than to fail whenever somebody attempts to deserialize.
                if (internal.getChildren().size() > MAX_CHILD_COUNT) {
                    throw new IOException(
                            "too many children: " + internal.getChildren().size() + ", limit: " + MAX_CHILD_COUNT);
                }

                limitedStream.writeInt(internal.getChildren().size());
                queue.addAll(internal.getChildren());
            } else {
                limitedStream.writeBoolean(false);
                limitedStream.writeSerializable(((SelfSerializable) next), true);
            }
        }
    }

    /**
     * A record for tracking the state of a parent node while deserializing a state proof tree.
     */
    private record ParentAwaitingChildren(@NonNull StateProofInternalNode parent, int expectedChildCount) {}

    /**
     * Add a child to its parent. Remove the parent from the queue if all of its children have been deserialized.
     *
     * @param queue the queue of parents awaiting children
     * @param child the child node
     */
    private static void addToParent(
            @NonNull final Queue<ParentAwaitingChildren> queue, @NonNull final StateProofNode child)
            throws IOException {

        if (queue.isEmpty()) {
            throw new IOException("No parent awaiting children");
        }

        final ParentAwaitingChildren parent = queue.peek();
        parent.parent.getChildren().add(child);
        if (parent.parent.getChildren().size() == parent.expectedChildCount) {
            // We have deserialized all of this node's children.
            queue.remove();
        }
    }

    /**
     * Deserialize a state proof tree.
     *
     * @param in the stream to read from
     * @return the deserialized tree
     * @throws IOException if an IO error occurs
     */
    @NonNull
    public static StateProofNode deserializeStateProofTree(@NonNull final SerializableDataInputStream in)
            throws IOException {

        // This stream will throw an IO exception if asked to read more than MAX_STATE_PROOF_TREE_SIZE bytes.
        final SerializableDataInputStream limitedStream = new SerializableDataInputStream(
                new ExtendableInputStream(in, new MaxSizeStreamExtension(MAX_STATE_PROOF_TREE_SIZE, false)));

        // Tree was written in BFS order. Read it back and reconstruct it.

        final Queue<ParentAwaitingChildren> queue = new LinkedList<>();
        boolean firstNode = true;
        StateProofNode root = null;
        while (!queue.isEmpty() || firstNode) {

            final boolean isInternal = limitedStream.readBoolean();

            final StateProofNode next;
            if (isInternal) {
                next = new StateProofInternalNode();
                if (!firstNode) {
                    addToParent(queue, next);
                }
                final int childCount = limitedStream.readInt();
                if (childCount > MAX_CHILD_COUNT) {
                    throw new IOException("Child count exceeds maximum allowed value of " + MAX_CHILD_COUNT);
                }

                queue.add(new ParentAwaitingChildren((StateProofInternalNode) next, childCount));
            } else {
                next = limitedStream.readSerializable();
                if (!firstNode) {
                    addToParent(queue, next);
                }
            }

            if (firstNode) {
                root = next;
                firstNode = false;
            }
        }

        if (root == null) {
            throw new IOException("Failed to find root");
        }
        return root;
    }

    /**
     * Walk a state proof tree and find the payloads.
     *
     * @param root the root of the tree
     * @return the payloads in the tree
     */
    @NonNull
    public static List<MerkleLeaf> extractPayloads(@NonNull final StateProofNode root) throws IOException {

        final List<MerkleLeaf> payloads = new ArrayList<>();

        final Queue<StateProofNode> queue = new LinkedList<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            final StateProofNode next = queue.remove();

            if (next instanceof final StateProofPayload payload) {
                payloads.add(payload.getPayload());
            } else if (next instanceof final StateProofInternalNode internal) {
                queue.addAll(internal.getChildren());
            }
        }

        if (payloads.isEmpty()) {
            throw new IOException("No payloads found");
        }
        return payloads;
    }
}
