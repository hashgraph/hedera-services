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

package com.swirlds.crypto.tss;

import com.swirlds.crypto.signaturescheme.api.PairingPrivateKey;
import com.swirlds.crypto.signaturescheme.api.PairingPublicKey;
import com.swirlds.crypto.signaturescheme.api.PairingSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A Threshold Signature Scheme Service.
 * Contract of TSS:
 * <ul>
 *     <li>Generate TssMessages out of PrivateShares</li>
 *     <li>Verify TssMessages out of a ParticipantDirectory</li>
 *     <li>Obtain PrivateShares out of TssMessages for each owned share</li>
 *     <li>Aggregate PrivateShares</li>
 *     <li>Obtain PublicShares out of TssMessages for each share</li>
 *     <li>Aggregate PublicShares</li>
 *     <li>Sign Messages</li>
 *     <li>Verify Signatures</li>
 *     <li>Aggregate Signatures</li>
 * </ul>
 * @implNote an instance of the service would require a source of randomness {@link java.util.Random}, and a{@link com.swirlds.crypto.signaturescheme.api.SignatureSchema}
 */
public interface TssService {

    /**
     * Generate a TSS message for a pendingParticipantDirectory, from a random private share.
     *
     * @param pendingParticipantDirectory the pending participant directory that we should generate the message for
     * @return a TSSMessage produced out of a random share.
     */
    @NonNull
    TssMessage generateTssMessage(@NonNull TssParticipantDirectory pendingParticipantDirectory);

    /**
     * Generate a TSS message for a pendingParticipantDirectory, for the specified {@link TssPrivateShare}.
     *
     * @param pendingParticipantDirectory the pending participant directory that we should generate the message for
     * @param privateShare       the secret to use for generating new keys
     * @return a TSSMessage for each owned share.
     */
    @NonNull
    TssMessage generateTssMessages(
            @NonNull TssParticipantDirectory pendingParticipantDirectory, @NonNull TssPrivateShare privateShare);

    /**
     * Verify that a {@link TssMessage} is valid.
     *
     * @param participantDirectory the participant directory used to generate the message
     * @param tssMessages the TSS messages to validate
     * @return true if the message is valid, false otherwise
     */
    boolean verifyTssMessage(@NonNull TssParticipantDirectory participantDirectory, @NonNull TssMessage tssMessages);

    /**
     * Compute all private shares that belongs to this participant from a threshold minimum number of {@link TssMessage}s.
     * It is the responsibility of the caller to ensure that the list of validTssMessages meets the required threshold.
     *
     * @param participantDirectory the pending participant directory that we should generate the private share for
     * @param validTssMessages the TSS messages to extract the private shares from. They must be previously validated.
     * @return a sorted by sharedId list of private shares the current participant owns, or null if there aren't enough shares to meet the threshold.
     */
    @Nullable
    List<TssPrivateShare> decryptPrivateShares(
            @NonNull TssParticipantDirectory participantDirectory, @NonNull List<TssMessage> validTssMessages);

    /**
     * Aggregate a threshold number of {@link TssPrivateShare}s.
     * It is the responsibility of the caller to ensure that the list of private shares meets the required threshold.
     * If the threshold is not met, the private key returned by this method will be invalid.
     *
     * @param privateShares   the private shares to aggregate
     * @return the aggregate private key
     */
    @NonNull
    PairingPrivateKey aggregatePrivateShares(@NonNull List<TssPrivateShare> privateShares);

    /**
     * Compute all public shares for all the participants in the scheme.
     *
     * @param participantDirectory the pending participant directory that we should generate the message for
     * @param validTssMessages the TSS messages to extract the private shares from. They must be previously validated.
     * @return  a sorted by the sharedId list of public shares, or null if there aren't enough messages to meet the threshold
     */
    @Nullable
    List<TssPublicShare> computePublicShares(
            @NonNull TssParticipantDirectory participantDirectory, @NonNull List<TssMessage> validTssMessages);

    /**
     * Aggregate a threshold number of {@link TssPublicShare}s.
     * It is the responsibility of the caller to ensure that the list of public shares meets the required threshold.
     * If the threshold is not met, the public key returned by this method will be invalid.
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
    PairingPublicKey aggregatePublicShares(@NonNull List<TssPublicShare> publicShares);

    /**
     * Sign a message using the private share's key.
     * @param privateShare the private share to sign the message with
     * @param message the message to sign
     * @return the signature, which will be in the group opposite to the group of the public key
     */
    @NonNull
    TssShareSignature sign(@NonNull TssPrivateShare privateShare, @NonNull byte[] message);

    /**
     * verifies a signature using the participantDirectory and the list of public shares.
     * @param participantDirectory the pending share claims the TSS message was created for
     * @param publicShares the public shares to sign the message with
     * @param signature the signature to verify
     * @return true if the signature is valid, false otherwise.
     */
    boolean verifySignature(
            @NonNull TssParticipantDirectory participantDirectory,
            @NonNull List<TssPublicShare> publicShares,
            @NonNull TssShareSignature signature);

    /**
     * Aggregate a threshold number of {@link TssShareSignature}s.
     * It is the responsibility of the caller to ensure that the list of partial signatures meets the required
     * threshold. If the threshold is not met, the signature returned by this method will be invalid.
     *
     * @param partialSignatures the list of signatures to aggregate
     * @return the interpolated signature
     */
    @NonNull
    PairingSignature aggregateSignatures(@NonNull List<TssShareSignature> partialSignatures);
}
