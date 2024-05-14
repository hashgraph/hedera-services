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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A Threshold Signature Scheme.
 */
public interface Tss {
    /**
     * Aggregate a threshold number of {@link TssSignature}s.
     *
     * @param partialSignatures the list of signatures to aggregate
     * @return the interpolated signature if the threshold is met, otherwise null.
     */
    @Nullable
    TssSignature aggregateSignatures(@NonNull final List<TssSignature> partialSignatures);

    /**
     * Aggregate a threshold number of {@link TssPublicShare}s.
     *
     * @param partialShares the public shares to aggregate
     * @return the interpolated public key if the threshold is met, otherwise null.
     */
    @Nullable
    TssPublicKey aggregatePublicShares(@NonNull final List<TssPublicShare> partialShares);

    /**
     * Aggregate a threshold number of {@link TssPrivateKey}s.
     *
     * @param partialKeys the private keys to aggregate
     * @return the aggregate private key, or null if the threshold is not met
     */
    @Nullable
    TssPrivateKey aggregatePrivateKeys(@NonNull final List<TssPrivateKey> partialKeys);

    /**
     * Generate a TSS message for a set of share claims, from a private share.
     *
     * @param pendingShareClaims the share claims that we should generate the message for
     * @param privateShare       the secret to use for generating new keys
     * @param threshold          the threshold for recovering the secret
     * @return the TSS message produced for the input share claims
     */
    @NonNull
    TssMessage generateTssMessage(
            @NonNull final List<TssShareClaim> pendingShareClaims,
            @NonNull final TssPrivateShare privateShare,
            final int threshold);
}
