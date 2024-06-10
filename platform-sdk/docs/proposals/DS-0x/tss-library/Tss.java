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

import com.swirlds.signaturescheme.api.PairingPrivateKey;
import com.swirlds.signaturescheme.api.PairingPublicKey;
import com.swirlds.signaturescheme.api.PairingSignature;
import com.swirlds.signaturescheme.api.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * A Threshold Signature Scheme.
 */
public interface Tss {

    @NonNull
    TssShareId createShareId(final int holderId);

    /**
     * Generate a TssShareClaims.
     *
     * @param shareClaimCollection  Information of each share owner.
     * @return the TssShareClaims for all the holders.
     */
    @NonNull
    default TssShareClaims getShareClaims(@NonNull final Collection<ShareClaimInfo> shareClaimCollection){
        List<TssShareClaim> sharesClaims = new ArrayList<>();
        for(ShareClaimInfo entry: shareClaimCollection)
            for (int i = 0; i< entry.amount(); i++){
                sharesClaims.add( new TssShareClaim(createShareId(entry.holder()), entry.publicKey()));
            }
        return new TssShareClaims(sharesClaims);
    }

    /**
     * Generate a TSS message for a set of share claims, from a private share.
     *
     * @param random             a source of randomness
     * @param pendingTssShareClaims the share claims that we should generate the message for
     * @param privateShare       the secret to use for generating new keys
     * @param threshold          the threshold for recovering the secret
     * @return the TSS message produced for the input share claims
     */
    @NonNull
    TssMessage generateTssMessage(
            @NonNull final Random random,
            @NonNull final TssShareClaims pendingTssShareClaims,
            @NonNull final TssPrivateShare privateShare,
            final int threshold);

    /**
     * Compute a private share that belongs to this node.
     *
     * @param shareId           the share ID owned by this node, for which the private share will be decrypted
     * @param elGamalPrivateKey the ElGamal private key of this node
     * @param tssMessages       the TSS messages to extract the private shares from
     * @param tssShareClaims       the share claims that the tss messages were created for
     * @param threshold         the threshold number of cipher texts required to decrypt the private share
     * @return the private share, or null if there aren't enough shares to meet the threshold
     */
    @Nullable
    TssPrivateShare decryptPrivateShare(
            @NonNull final TssShareId shareId,
            @NonNull final PairingPrivateKey elGamalPrivateKey,
            @NonNull final List<TssMessage> tssMessages,
            @NonNull final TssShareClaims tssShareClaims,
            final int threshold);

    /**
     * Compute the public share for a specific share ID.
     *
     * @param shareId         the share ID to compute the public share for
     * @param tssMessages     the TSS messages to extract the public shares from
     * @param threshold       the threshold number of messages required to compute the public share
     * @return the public share, or null if there aren't enough messages to meet the threshold
     */
    @Nullable
    TssPublicShare computePublicShare(@NonNull final TssShareId shareId, @NonNull final List<TssMessage> tssMessages, final int threshold);

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
     * @param privateShares   the private shares to aggregate
     * @return the aggregate private key
     */
    @NonNull
    PairingPrivateKey aggregatePrivateShares(@NonNull final List<TssPrivateShare> privateShares);

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

    public static Tss getFor(SignatureSchema schema){
        return /*Something other*/null;
    }
}
