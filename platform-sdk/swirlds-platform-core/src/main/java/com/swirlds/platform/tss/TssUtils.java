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

import com.swirlds.platform.tss.bls.api.PublicKey;
import com.swirlds.platform.tss.ecdh.EcdhPrivateKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for a Threshold Signature Scheme.
 */
public final class TssUtils {
    /**
     * Hidden constructor
     */
    private TssUtils() {}

    /**
     * Compute the private shares that belong to this node.
     *
     * @param tss            the TSS instance
     * @param shareIds       the share IDs owned by this node, for which the private shares will be decrypted
     * @param ecdhPrivateKey the ECDH private key of this node
     * @param cipherTexts    the cipher texts to extract the private shares from
     * @param threshold      the threshold number of cipher texts required to decrypt the private shares
     * @param <P>            the type of public key that can be used to verify signatures produced by the secret keys
     *                       encrypted in the cipher texts
     * @return the private shares, or null if there aren't enough shares to meet the threshold
     */
    @Nullable
    public static <P extends PublicKey> List<TssPrivateShare<P>> decryptPrivateShares(
            @NonNull final Tss<P> tss,
            @NonNull final List<TssShareId> shareIds,
            @NonNull final EcdhPrivateKey ecdhPrivateKey,
            @NonNull final List<TssCiphertext<P>> cipherTexts,
            final int threshold) {

        // check if there are enough cipher texts to meet the required threshold
        if (cipherTexts.size() < threshold) {
            return null;
        }

        // decrypt the partial private shares from the cipher texts
        final Map<TssShareId, List<TssPrivateKey<P>>> partialPrivateKeys = new HashMap<>();
        for (final TssCiphertext<P> cipherText : cipherTexts) {
            for (final TssShareId shareId : shareIds) {
                partialPrivateKeys
                        .computeIfAbsent(shareId, k -> new ArrayList<>())
                        .add(cipherText.decryptPrivateKey(ecdhPrivateKey, shareId));
            }
        }

        // aggregate the decrypted partial private keys, creating the actual private shares
        final List<TssPrivateShare<P>> privateShares = new ArrayList<>();
        for (final Map.Entry<TssShareId, List<TssPrivateKey<P>>> entry : partialPrivateKeys.entrySet()) {
            final TssShareId shareId = entry.getKey();
            final List<TssPrivateKey<P>> partialKeysForId = entry.getValue();

            // TODO: make sure private key aggregate doesn't return null
            privateShares.add(new TssPrivateShare<>(shareId, tss.aggregatePrivateKeys(partialKeysForId)));
        }

        return privateShares;
    }

    /**
     * Compute public shares from a list of TSS messages.
     *
     * @param tss         the TSS instance
     * @param shareIds    the share IDs to compute the public shares for
     * @param tssMessages the TSS messages
     * @param threshold   the threshold number of commitments required to compute the public shares
     * @param <P>         the type of public that will be computed
     * @return the public shares, or null if there aren't enough shares to meet the threshold
     */
    @Nullable
    public static <P extends PublicKey> Map<TssShareId, P> computePublicShares(
            @NonNull final Tss<P> tss,
            @NonNull final List<TssShareId> shareIds,
            @NonNull final List<TssMessage<P>> tssMessages,
            final int threshold) {

        // check if there are enough TSS messages to meet the required threshold
        if (tssMessages.size() < threshold) {
            return null;
        }

        final Map<TssShareId, P> outputShares = new HashMap<>();

        // go through each specified share ID and compute the corresponding public key
        for (final TssShareId shareId : shareIds) {
            // each share in this partialShares list represents a public key obtained from a commitment
            // the share ID in each of these partial shares corresponds to the share ID that *CREATED* the commitment,
            // NOT to the share ID that the public key is for
            final List<TssPublicShare<P>> partialShares = new ArrayList<>();

            for (final TssMessage<P> tssMessage : tssMessages) {
                partialShares.add(new TssPublicShare<>(
                        tssMessage.shareId(), tssMessage.commitment().extractPublicKey(shareId)));
            }
            outputShares.put(shareId, tss.aggregatePublicShares(partialShares));
        }

        return outputShares;
    }
}
