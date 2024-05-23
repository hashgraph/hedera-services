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

package com.swirlds.tss.api;

import com.swirlds.signaturescheme.api.PairingPublicKey;
import com.swirlds.signaturescheme.api.PairingSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A Threshold Signature Scheme.
 * <p>
 * Contract of TSS:
 * <ul>
 *     <li>produce a public key for each share</li>
 *     <li>give the corresponding secret to the shareholder</li>
 * </ul>
 *
 * @param <P> the type of public key that verifies signatures in this threshold scheme
 */
public interface Tss<P extends PairingPublicKey> {
    /**
     * Aggregate a threshold number of {@link PairingSignature}s.
     *
     * @param partialSignatures the list of signatures to aggregate
     * @return the interpolated signature if the threshold is met, otherwise null.
     */
    @Nullable
    PairingSignature aggregateSignatures(@NonNull final List<PairingSignature> partialSignatures);

    /**
     * Aggregate a threshold number of {@link TssPublicShare}s.
     * <p>
     * This method is used for two distinct purposes:
     * <ul>
     *     <li>Aggregating public shares to produce the Ledger ID</li>
     *     <li>Aggregating public shares derived from all commitments, to produce the public key for a given share</li>
     * </ul>
     *
     * @param publicShares the public shares to aggregate
     * @return the interpolated public key if the threshold is met, otherwise null.
     */
    @Nullable
    P aggregatePublicShares(@NonNull final List<TssPublicShare<P>> publicShares);

    /**
     * Aggregate a threshold number of {@link TssPrivateKey}s.
     *
     * @param privateKeys the private keys to aggregate
     * @return the aggregate private key, or null if the threshold is not met
     */
    @Nullable
    TssPrivateKey<P> aggregatePrivateKeys(@NonNull final List<TssPrivateKey<P>> privateKeys);

    /**
     * Generate a TSS message for a set of share claims, from a private share.
     *
     * @param pendingShareClaims the share claims that we should generate the message for
     * @param privateShare       the secret to use for generating new keys
     * @param threshold          the threshold for recovering the secret
     * @return the TSS message produced for the input share claims
     */
    @NonNull
    TssMessage<P> generateTssMessage(
            @NonNull final List<TssShareClaim> pendingShareClaims,
            @NonNull final TssPrivateShare<P> privateShare,
            final int threshold);
}
