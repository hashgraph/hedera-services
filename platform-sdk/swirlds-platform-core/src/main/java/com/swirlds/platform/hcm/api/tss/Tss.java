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

package com.swirlds.platform.hcm.api.tss;

import com.swirlds.platform.hcm.api.signaturescheme.PairingPrivateKey;
import com.swirlds.platform.hcm.api.signaturescheme.PairingPublicKey;
import com.swirlds.platform.hcm.api.signaturescheme.PairingSignature;
import com.swirlds.platform.hcm.api.signaturescheme.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A Threshold Signature Scheme.
 * <p>
 * Contract of TSS:
 * <ul>
 *     <li>produce a public key for each share</li>
 *     <li>give the corresponding secret to the shareholder</li>
 * </ul>
 */
public interface Tss {
    /**
     * Aggregate a threshold number of {@link TssShareSignature}s.
     * <p>
     * It is the responsibility of the caller to ensure that the list of partial signatures meets the required
     * threshold. If the threshold is not met, the signature returned by this method will be invalid.
     *
     * @param partialSignatures the list of signatures to aggregate
     * @return the interpolated signature
     */
    @NonNull
    PairingSignature aggregateSignatures(@NonNull final List<TssShareSignature> partialSignatures);

    /**
     * Aggregate a threshold number of {@link TssPublicShare}s.
     * <p>
     * It is the responsibility of the caller to ensure that the list of public shares meets the required threshold.
     * If the threshold is not met, the public key returned by this method will be invalid.
     * <p>
     * This method is used for two distinct purposes:
     * <ul>
     *     <li>Aggregating public shares to produce the Ledger ID</li>
     *     <li>Aggregating public shares derived from all commitments, to produce the public key for a given share</li>
     * </ul>
     *
     * @param publicShares the public shares to aggregate
     * @return the interpolated public key
     */
    @NonNull
    PairingPublicKey aggregatePublicShares(@NonNull final List<TssPublicShare> publicShares);

    /**
     * Aggregate a threshold number of {@link TssPrivateShare}s.
     * <p>
     * It is the responsibility of the caller to ensure that the list of private shares meets the required threshold.
     * If the threshold is not met, the private key returned by this method will be invalid.
     *
     * @param privateShares the private shares to aggregate
     * @return the aggregate private key
     */
    @NonNull
    PairingPrivateKey aggregatePrivateShares(@NonNull final List<TssPrivateShare> privateShares);

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

    /**
     * Get the signature schema used by this TSS instance
     *
     * @return the signature schema
     */
    @NonNull
    SignatureSchema getSignatureSchema();
}
