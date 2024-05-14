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

package com.swirlds.platform.tss;

import com.swirlds.platform.tss.ecdh.EcdhPrivateKey;
import com.swirlds.platform.tss.ecdh.EcdhPublicKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Set;

public interface Tss {

    /**
     * If a threshold number of {@link TssSignature}s by shares is provided a signature by the {@link TssDirectory}'s
     * secret private key is computed.
     *
     * @param signatures the map of share ids to signatures created by each share.
     * @return the interpolated signature if the threshold is met, otherwise null.
     */
    TssSignature aggregateSignatures(@NonNull final Map<TssShareId, TssSignature> signatures);

    /**
     * If a threshold number of {@link TssPublicKey}s of shares is provided a public key for the {@link TssDirectory}'s
     * secret private key is computed.
     *
     * @param publicKeys the map of share ids to public keys created by each share.
     * @return the interpolated public key if the threshold is met, otherwise null.
     */
    TssPublicKey aggregatePublicKeys(@NonNull final Map<TssShareId, TssPublicKey> publicKeys);

    /**
     * Generate a DKG message with the given secret using the {@link EcdhPublicKey}s of the signers in the next
     * {@link TssDirectory}.
     *
     * @param nextTssDirectory the next {@link TssDirectory} of signers
     * @param signerId         the id of the signer generating the DKG message.
     * @param secret           the secret to use for generating new keys.
     * @param threshold        the threshold for recovering the secret.
     * @param shareOwnership   the mapping from share id to its owning signer id.
     * @return the DKG message for the given signer use to initialize the keys in the next {@link TssDirectory}.
     */
    @NonNull
    TssMessage generateDkgMessage(
            @NonNull final TssDirectory nextTssDirectory,
            @NonNull final TssSignerId signerId,
            final byte[] secret,
            final int threshold,
            Map<TssShareId, TssSignerId> shareOwnership);

    /**
     * Process a threshold number of {@link TssMessage}s to create the first {@link TssDirectory} with the given
     * signer's private keys initialized.
     *
     * @param genesisDirectory the genesis directory of signers without keys initialized
     * @param tssSigner        the id of the signer whose shares' private keys should be initialized
     * @param shares           the shares belonging to the signer
     * @param dkgMessages      the DKG messages from signers in the genesis directory
     * @return the genesis directory with keys initialized
     * @throws IllegalArgumentException if the signer does not have an {@link EcdhPrivateKey} set or the number of
     *                                  {@link TssMessage}s is less than the threshold in the genesis
     *                                  {@link TssDirectory}.
     */
    @NonNull
    TssDirectory setup(
            @NonNull TssDirectory genesisDirectory,
            @NonNull final TssSignerId tssSigner,
            Set<TssShareId> shares,
            @NonNull final Map<TssSignerId, TssMessage> dkgMessages);

    /**
     * Process a threshold number of {@link TssMessage}s to initialize the next {@link TssDirectory}'s shares' public
     * keys and the given signer's shares' private keys. The threshold that must be met is from the previous
     * {@link TssDirectory}.
     *
     * @param previousDirectory the previous directory of signers
     * @param nextDirectory     the next directory of signers
     * @param tssSigner         the signer whose shares' private keys are to be initialized
     * @param shares            the shares belonging to the signer
     * @param dkgMessages       the DKG messages from signers in the previous directory
     * @return the next {@link TssDirectory} with keys initialized
     * @throws IllegalArgumentException if the signer does not have an {@link EcdhPrivateKey} set or the number of
     *                                  {@link TssMessage}s is less than the threshold in the previous
     *                                  {@link TssDirectory}.
     */
    @NonNull
    TssDirectory rekey(
            @NonNull final TssDirectory previousDirectory,
            @NonNull final TssDirectory nextDirectory,
            @NonNull final TssSignerId tssSigner,
            Set<TssShareId> shares,
            @NonNull final Map<TssSignerId, TssMessage> dkgMessages);
}
