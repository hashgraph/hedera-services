package com.hedera.node.app.tss;

import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.cryptography.tss.api.TssPublicShare;
import edu.umd.cs.findbugs.annotations.NonNull;
import com.hedera.cryptography.pairings.signatures.api.PairingSignature;
import com.hedera.cryptography.tss.api.TssPrivateShare;
import com.hedera.cryptography.tss.api.TssShareSignature;


import java.util.List;

/**
 * A Threshold Signature Scheme Library.
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
 */
public interface TssLibrary {
    
    /**
     * Sign a message using the private share's key.
     * @param privateShare the private share to sign the message with
     * @param message the message to sign
     * @return the signature
     */
    @NonNull
    TssShareSignature sign(@NonNull TssPrivateShare privateShare, @NonNull byte[] message);

    /**
     * verifies a signature using the participantDirectory and the list of public shares.
     * @param participantDirectory the pending share claims the TSS message was created for
     * @param publicShares the public shares to verify the signature with
     * @param signature the signature to verify
     * @return if the signature is valid.
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
