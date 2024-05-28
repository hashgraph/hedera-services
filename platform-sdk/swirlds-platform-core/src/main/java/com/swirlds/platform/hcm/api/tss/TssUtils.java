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
import com.swirlds.platform.hcm.impl.internal.ElGamalCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for a Threshold Signature Scheme.
 */
public final class TssUtils {
    /**
     * Hidden constructor
     */
    private TssUtils() {}

    /**
     * Compute a private share that belongs to this node.
     *
     * @param tss               the TSS instance
     * @param shareId           the share ID owned by this node, for which the private share will be decrypted
     * @param elGamalPrivateKey the ElGamal private key of this node
     * @param tssMessages       the TSS messages to extract the private shares from
     * @param elGamalCache      the ElGamal cache
     * @param threshold         the threshold number of cipher texts required to decrypt the private share
     * @return the private share, or null if there aren't enough shares to meet the threshold
     */
    @Nullable
    public static TssPrivateShare decryptPrivateShare(
            @NonNull final Tss tss,
            @NonNull final TssShareId shareId,
            @NonNull final PairingPrivateKey elGamalPrivateKey,
            @NonNull final List<TssMessage> tssMessages,
            @NonNull final ElGamalCache elGamalCache,
            final int threshold) {

        // check if there are enough messages to meet the required threshold
        if (tssMessages.size() < threshold) {
            return null;
        }

        // decrypt the partial private shares from the cipher texts
        final List<TssPrivateShare> partialPrivateShares = new ArrayList<>();
        tssMessages.forEach(tssMessage -> partialPrivateShares.add(
                tssMessage.cipherText().decryptPrivateShare(elGamalPrivateKey, shareId, elGamalCache)));

        // aggregate the decrypted partial private shares, creating the actual private share
        return new TssPrivateShare(shareId, tss.aggregatePrivateShares(partialPrivateShares));
    }

    /**
     * Compute the public share for a specific share ID.
     *
     * @param tss         the TSS instance
     * @param shareId     the share ID to compute the public share for
     * @param tssMessages the TSS messages to extract the public shares from
     * @param threshold   the threshold number of messages required to compute the public share
     * @return the public share, or null if there aren't enough messages to meet the threshold
     */
    @Nullable
    public static TssPublicShare computePublicShare(
            @NonNull final Tss tss,
            @NonNull final TssShareId shareId,
            @NonNull final List<TssMessage> tssMessages,
            final int threshold) {

        // check if there are enough TSS messages to meet the required threshold
        if (tssMessages.size() < threshold) {
            return null;
        }

        // each share in this partialShares list represents a public key obtained from a commitment
        // the share ID in each of these partial shares corresponds to the share ID that *CREATED* the commitment,
        // NOT to the share ID that the public key is for
        final List<TssPublicShare> partialShares = new ArrayList<>();

        for (final TssMessage tssMessage : tssMessages) {
            partialShares.add(new TssPublicShare(
                    tssMessage.shareId(),
                    new PairingPublicKey(
                            tss.getSignatureSchema(), tssMessage.commitment().extractPublicKey(shareId))));
        }

        return new TssPublicShare(shareId, tss.aggregatePublicShares(partialShares));
    }
}
