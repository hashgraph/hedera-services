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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A Threshold Signature Scheme Service.
 * <p>
 * Contract of TSS:
 * <ul>
 *     <li>produce a public key for each share</li>
 *     <li>give the corresponding secret to the shareholder</li>
 * </ul>
 * @implNote an instance of the service would require  a source of randomness {@link java.util.Random}, and {@link com.swirlds.signaturescheme.api.SignatureSchema}
 */
public interface TssService {

    /**
     * Verify that the message is valid.
     *
     * @param pendingParticipantDirectory the pending participant directory that we should generate the message for
     * @return true if the message is valid, false otherwise
     */
     boolean verifyTssMessage(@NonNull final ParticipantDirectory participantDirectory, @NonNull final TssMessage message);

    /**
     * Generate a TSS message for a pendingParticipantDirectory, from a private share.
     *
     * @param pendingParticipantDirectory the pending participant directory that we should generate the message for
     * @param privateShare       the secret to use for generating new keys
     * @return the TSS message produced for the input share claims
     */
    @NonNull
    TssMessage generateTssMessage(
            @NonNull final ParticipantDirectory pendingParticipantDirectory,
            @NonNull final TssPrivateShare privateShare);

    /**
     * Generate a TSS message for a pendingParticipantDirectory, from a random private share.
     *
     * @param pendingParticipantDirectory the pending participant directory that we should generate the message for
     * @param privateShare       the secret to use for generating new keys
     * @return the TSS message produced for the input share claims
     */
    @NonNull
    TssMessage generateTssMessage(@NonNull final ParticipantDirectory pendingParticipantDirectory);

    /**
     * Compute all private shares that belongs to this participant.
     *
     * @param pendingParticipantDirectory the pending participant directory that we should generate the message for
     * @param validTssMessages       the TSS messages to extract the private shares from. They must be previously validated.
     * @return the list of private share the current participant owns, or null if there aren't enough shares to meet the threshold
     */
    @Nullable
    List<TssPrivateShare> decryptPrivateShares(
            @NonNull final ParticipantDirectory participantDirectory,
            @NonNull final List<TssMessage> validTssMessages);

    /**
     * Compute all public shares.
     *
     * @param pendingParticipantDirectory the pending participant directory that we should generate the message for
     * @param validTssMessages       the TSS messages to extract the private shares from. They must be previously validated.
     * @return the list of public shares, or null if there aren't enough messages to meet the threshold
     */
    @Nullable
    TssPublicShare computePublicShares(
            @NonNull final ParticipantDirectory participantDirectory,
            @NonNull final List<TssMessage> tssMessages) ;

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
     * @param privateShares   the private shares to aggregate
     * @return the aggregate private key
     */
    @NonNull
    PairingPrivateKey aggregatePrivateShares(@NonNull final List<TssPrivateShare> privateShares);

    /**
     * Sign a message using the private share's key.
     * <p>
     * @param privateShare the private share to sign the message with
     * @param message the message to sign
     * @return the signature, which will be in the group opposite to the group of the public key
     */
    @NonNull
    TssShareSignature sign(final @NonNull TssPrivateShare privateShare, final @NonNull byte[] message) ;

    /**
     * verifies a signature using the participantDirectory.
     * <p>
     * @param participantDirectory the pending share claims the TSS message was created for
     * @param privateShare the private share to sign the message with
     * @return true if the signature is valid, false otherwise.
     */
    boolean verify(@NonNull final ParticipantDirectory participantDirectory, final @NonNull TssShareSignature privateShare) ;

}
