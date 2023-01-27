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
package com.hedera.node.app.signatures.crypto;

import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import java.util.List;

/**
 * A source of signatures scoped to a single transaction. Hedera users can provide signatures for
 * transactions through a top-level {@code SignatureMap}. A {@code SignatureMap} is a list of {@code
 * SignaturePair}'s, where each {@code SignaturePair} contains a public key prefix and a signature.
 * For example, suppose Alice needs to sign a {@code CryptoTransfer} with two Ed25519 private keys,
 * because this transfer debits two different accounts---say, {@code 0.0.X} controlled by key A and
 * {@code 0.0.Y} controlled by key B.
 *
 * <p>Now note that the node software <i>knows</i> and <i>enforces</i> this signing requirement. It
 * does not actually need Alice to give it any information about which public keys must have
 * corresponding signatures.
 *
 * <p>Alice <i>does</i> need to indicate which signature matches key, however. (Otherwise the
 * network would have to check four Ed25519 signatures for this transaction, instead of two.)
 * Suppose the public key for A starts with {@code 0x01ee} and the public key for B starts with
 * {@code 0x01ff}. Then the minimum information Alice needs to send is just which unique prefix
 * corresponds to which signature.
 *
 * <p>This class handles the problem of finding the signature bytes for a given public key prefix
 * inside a {@code SignatureMap}.
 *
 * <p>There is an important exception in which public key prefixes are not sufficient, and the
 * network <i>does</i> need the full public key. This is when the public key will be needed to give
 * permissions to an EVM system contract. In this case, the network cannot possibly predict which
 * public key will be needed, so the user must provide it explicitly.
 */
@SuppressWarnings({"java:S1068", "java:S1192"})
public class SignatureMapAccessor {
    private static final byte[] MISSING_SIG = new byte[0];

    private final SignatureMap signatureMap;

    public SignatureMapAccessor(final SignatureMap signatureMap) {
        this.signatureMap = signatureMap;
    }

    /**
     * Returns the signature bytes for the given public key prefix, <b>if</b> the {@code
     * SignatureMap} contains a unique {@code SignaturePair} with that prefix.
     *
     * <p>If there is no such {@code SignaturePair}, this returns an empty byte array.
     *
     * <p>If there are multiple {@code SignaturePair} whose prefixes match the given key, throws a
     * {@link NonUniquePrefixException}.
     *
     * @param publicKey the public key that should match exactly one prefix in the map
     * @return the signature bytes for the given public key, or an empty byte array if no prefixes
     *     match
     * @throws NonUniquePrefixException if more than one prefix matches
     */
    public byte[] getSignatureByPublicKey(final byte[] publicKey) throws NonUniquePrefixException {
        throw new AssertionError("Not implemented");
    }

    /**
     * Returns the signature bytes from the first {@code SignaturePair} whose full public key is set
     * in the prefix; and would have the given address on the Ethereum network.
     *
     * <p>(Recall that on Ethereum, the address of an account is the last 20 bytes of the Keccak-256
     * hash of the public key.)
     *
     * @param evmAddress an EVM address
     * @return the signature bytes for the full public key that implies the given address
     */
    public byte[] getSignatureByEvmAddress(final byte[] evmAddress) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Following zero or more calls to {@link #getSignatureByPublicKey(byte[])} and {@link
     * #getSignatureByEvmAddress(byte[])}, this method returns a list of all the {@code
     * SignaturePair}'s <b>with full-key prefixes</b> that were not used in those calls.
     *
     * @return the remaining {@code SignaturePair}'s with full-key prefixes
     */
    public List<SignaturePair> getRemainingExplicitSignatures() {
        throw new AssertionError("Not implemented");
    }
}
