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

package com.swirlds.platform.stateproof;

import static com.swirlds.platform.Utilities.isMajority;
import static com.swirlds.platform.Utilities.isStrongMinority;
import static com.swirlds.platform.Utilities.isSuperMajority;

import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.stateproof.internal.StateProofNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A state proof on one or more merkle nodes.
 * <p>
 * Warning: this is an unstable API, and it may be changed and/or removed suddenly and without warning.
 */
public class StateProof implements SelfSerializable {

    private static final long CLASS_ID = 0xbf5d45fc18b63224L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    Map<NodeId, Signature> signatures;
    private StateProofNode root;
    private List<MerkleLeaf> payloads;

    /**
     * Zero arg constructor required by the serialization framework.
     */
    public StateProof() {}

    /**
     * Create a state proof on the given merkle node(0).
     *
     * @param root       the root of the merkle tree to create a state proof on
     * @param signatures signatures on the root hash of the merkle tree
     * @param payloads   one or more leaf nodes to create a state proof on
     */
    public StateProof(
            @NonNull final MerkleNode root,
            @NonNull final Map<NodeId, Signature> signatures,
            @NonNull final List<MerkleLeaf> payloads) {

        Objects.requireNonNull(root);
        this.signatures = Objects.requireNonNull(signatures);
        this.payloads = Objects.requireNonNull(payloads);

        if (signatures.isEmpty()) {
            throw new IllegalArgumentException("signatures must not be empty");
        }
        if (payloads.isEmpty()) {
            throw new IllegalArgumentException("payloads must not be empty");
        }

        // TODO starting at root, iterate all ancestors of the payloads and construct the state proof tree
    }

    /**
     * Cryptographically validate this state proof using the {@link StateProofThreshold#SUPER_MAJORITY} threshold.
     *
     * @param addressBook the address book to use to validate the state proof
     * @return true if this state proof is valid, otherwise false
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    public boolean isValid(@NonNull final AddressBook addressBook) {
        return isValid(addressBook, StateProofThreshold.SUPER_MAJORITY);
    }

    /**
     * Cryptographically validate this state proof using the provided threshold.
     *
     * @param addressBook the address book to use to validate the state proof
     * @return true if this state proof is valid, otherwise false
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    public boolean isValid(@NonNull final AddressBook addressBook, @NonNull final StateProofThreshold threshold) {
        Objects.requireNonNull(addressBook);

        // TODO recalculate the root hash
        long validWeight = 0; // TODO find the weight of the valid signatures

        return switch (threshold) {
            case STRONG_MINORITY -> isStrongMinority(validWeight, addressBook.getTotalWeight());
            case MAJORITY -> isMajority(validWeight, addressBook.getTotalWeight());
            case SUPER_MAJORITY -> isSuperMajority(validWeight, addressBook.getTotalWeight());
        };
    }

    /**
     * Get the payloads of this state proof (i.e. the leaf nodes being "proven"). Do not trust the authenticity of these
     * payloads unless {@link #isValid(AddressBook)} returns true.
     *
     * @return the payloads of this state proof
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    @NonNull
    public List<MerkleLeaf> getPayloads() {
        if (payloads == null) {
            throw new IllegalStateException("StateProof has not been fully deserialized");
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
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {

        // TODO we need to make sure we write signatures in a deterministic order
        out.writeInt(signatures.size());
        for (final Map.Entry<NodeId, Signature> entry : signatures.entrySet()) {
            out.writeSerializable(entry.getKey(), false);
            out.writeSerializable(entry.getValue(), false);
        }

        out.writeSerializable(root, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        final int numSignatures = in.readInt();
        signatures = new HashMap<>(numSignatures);
        // TODO throw if there are too many signatures
        for (int i = 0; i < numSignatures; i++) {
            final NodeId nodeId = in.readSerializable(false, NodeId::new);
            final Signature signature = in.readSerializable(false, Signature::new);
            signatures.put(nodeId, signature);
        }

        // TODO limit max number of nodes read... or perhaps limit max bytes
        root = in.readSerializable();
        if (root == null) {
            throw new IOException("root is null");
        }

        payloads = root.getPayloads();
    }
}
