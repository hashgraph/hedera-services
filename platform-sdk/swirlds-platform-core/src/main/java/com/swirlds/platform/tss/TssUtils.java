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

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.roster.Roster;
import com.swirlds.platform.tss.bls.BlsShareId;
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
     * @return the private shares, or null if there aren't enough shares to meet the threshold
     */
    @Nullable
    public static List<TssPrivateShare> decryptPrivateShares(
            @NonNull final Tss tss,
            @NonNull final NodeId selfId,
            @NonNull final Roster roster,
            @NonNull final EcdhPrivateKey ecdhPrivateKey,
            @NonNull final List<TssCiphertext> cipherTexts) {

        // final List<TssShareId> shareIds = roster.getShareIds(selfId); // TODO: Implement this roster method
        final List<TssShareId> shareIds = List.of(); // TODO placeholder

        // TODO: check if there are enough shares to meet the threshold, and return null if not

        // decrypt the partial private shares from the cipher texts
        final Map<TssShareId, List<TssPrivateKey>> partialPrivateShares = new HashMap<>();
        for (final TssCiphertext cipherText : cipherTexts) {
            for (final TssShareId shareId : shareIds) {
                partialPrivateShares
                        .computeIfAbsent(shareId, k -> new ArrayList<>())
                        .add(cipherText.decryptPrivateKey(ecdhPrivateKey, shareId));
            }
        }

        // aggregate the decrypted partial private keys, creating the actual private shares
        final List<TssPrivateShare> privateShares = new ArrayList<>();
        for (final Map.Entry<TssShareId, List<TssPrivateKey>> entry : partialPrivateShares.entrySet()) {
            final TssShareId shareId = entry.getKey();
            final List<TssPrivateKey> partialSharesForId = entry.getValue();

            privateShares.add(new TssPrivateShare(shareId, tss.aggregatePrivateKeys(partialSharesForId)));
        }

        return privateShares;
    }

    /**
     * Compute the public shares for the whole roster from a list of cipher texts.
     *
     * @param tss         the TSS instance
     * @param roster      the roster
     * @param cipherTexts the cipher texts
     * @return the public shares, or null if there aren't enough shares to meet the threshold
     */
    @Nullable
    public static Map<TssShareId, TssPublicKey> computePublicShares(
            @NonNull final Tss tss, @NonNull final Roster roster, @NonNull final List<TssCiphertext> cipherTexts) {

        // TODO: check if there are enough shares to meet the threshold, and return null if not

        final Map<TssShareId, TssPublicKey> publicShares = new HashMap<>();
        roster.getNodeIds().forEach(nodeId -> {
            // final List<TssShareId> shareIds = roster.getShareIds(selfId); // TODO: Implement this roster method
            final List<TssShareId> shareIds = List.of(); // TODO placeholder

            for (final TssShareId shareId : shareIds) {
                final List<TssPublicShare> partialSharesForId = new ArrayList<>();
                for (final TssCiphertext cipherText : cipherTexts) {
                    partialSharesForId.add(cipherText.extractPublicShare(shareId));
                }
                publicShares.put(shareId, tss.aggregatePublicShares(partialSharesForId));
            }
        });

        return publicShares;
    }
}
