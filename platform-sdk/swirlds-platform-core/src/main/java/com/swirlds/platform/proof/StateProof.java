/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.proof;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.Threshold;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.proof.algorithms.NodeSignature;
import com.swirlds.platform.proof.algorithms.StateProofSerialization;
import com.swirlds.platform.proof.algorithms.StateProofTreeBuilder;
import com.swirlds.platform.proof.algorithms.StateProofUtils;
import com.swirlds.platform.proof.tree.StateProofNode;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
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

    private List<NodeSignature> signatures;
    private StateProofNode root;

    /**
     * A payload is a leaf node in the merkle tree that is being proven. There will be exactly one payload for each
     * merkle leaf that is being proven.
     */
    private List<MerkleLeaf> payloads;

    private byte[] hashBytes;

    /**
     * Zero arg constructor required by the serialization framework.
     */
    public StateProof() {}

    /**
     * Create a state proof on the given merkle node.
     *
     * @param cryptography provides cryptographic primitives
     * @param merkleRoot   the root of the merkle tree to create a state proof on
     * @param signatures   signatures on the root hash of the merkle tree
     * @param payloads     one or more leaf nodes to create a state proof on, may not contain null leaves
     */
    public StateProof(
            @NonNull final Cryptography cryptography,
            @NonNull final MerkleNode merkleRoot,
            @NonNull final Map<NodeId, Signature> signatures,
            @NonNull final List<MerkleLeaf> payloads) {

        Objects.requireNonNull(cryptography);
        Objects.requireNonNull(merkleRoot);
        Objects.requireNonNull(signatures);
        Objects.requireNonNull(payloads);

        this.payloads = StateProofTreeBuilder.validatePayloads(payloads);
        this.signatures = StateProofTreeBuilder.processSignatures(signatures);
        this.root = StateProofTreeBuilder.buildStateProofTree(cryptography, merkleRoot, payloads);
    }

    /**
     * Cryptographically validate this state proof using the provided threshold.
     *
     * @param cryptography provides cryptographic primitives
     * @param addressBook  the address book to use to validate the state proof
     * @param threshold    the threshold of signatures required to trust this state proof
     * @return true if this state proof is valid, otherwise false
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    public boolean isValid(
            @NonNull final Cryptography cryptography,
            @NonNull final AddressBook addressBook,
            @NonNull final Threshold threshold) {

        return isValid(
                cryptography,
                addressBook,
                threshold,
                (signature, bytes, publicKey) ->
                        CryptoStatic.verifySignature(Bytes.wrap(bytes), signature.getBytes(), publicKey));
    }

    /**
     * Cryptographically validate this state proof using the provided threshold.
     *
     * @param cryptography      provides cryptographic primitives
     * @param addressBook       the address book to use to validate the state proof
     * @param threshold         the threshold of signatures required to trust this state proof
     * @param signatureVerifier a function that verifies a signature
     * @return true if this state proof is valid, otherwise false
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    public boolean isValid(
            @NonNull final Cryptography cryptography,
            @NonNull final AddressBook addressBook,
            @NonNull final Threshold threshold,
            @NonNull final SignatureVerifier signatureVerifier) {

        Objects.requireNonNull(cryptography);
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(threshold);
        Objects.requireNonNull(signatureVerifier);

        if (hashBytes == null) {
            // we only need to recompute the hash once
            hashBytes = StateProofUtils.computeStateProofTreeHash(cryptography, root);
        }
        final long validWeight =
                StateProofUtils.computeValidSignatureWeight(addressBook, signatures, signatureVerifier, hashBytes);
        return threshold.isSatisfiedBy(validWeight, addressBook.getTotalWeight());
    }

    /**
     * Get the payloads of this state proof (i.e. the leaf nodes being "proven"). Do not trust the authenticity of these
     * payloads unless {@link #isValid(Cryptography, AddressBook, Threshold)} returns true.
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
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        StateProofSerialization.serializeSignatures(out, signatures);
        StateProofSerialization.serializeStateProofTree(out, root);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        signatures = StateProofSerialization.deserializeSignatures(in);
        root = StateProofSerialization.deserializeStateProofTree(in);
        payloads = StateProofSerialization.extractPayloads(root);
    }
}
