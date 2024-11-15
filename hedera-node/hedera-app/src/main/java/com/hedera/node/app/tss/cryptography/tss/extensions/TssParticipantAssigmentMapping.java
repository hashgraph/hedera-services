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

package com.hedera.node.app.tss.cryptography.tss.extensions;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.tss.cryptography.bls.BlsPublicKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Contains mappings useful for the {@link com.hedera.node.app.tss.cryptography.tss.api.TssParticipantDirectory}:
 * <ul>
 *  <li> Maps {@code ParticipantId}s to owned {@code shareId}s</li>
 *  <li> Maps {@code shareId}s to participant's {@code tssEncryptionPublicKey}s</li>
 *  </ul>
 */
public class TssParticipantAssigmentMapping {
    /**
     * Each index represents a participant.
     * Two values are stored per index:
     * <ul>
     *  <li>the first shareId belonging to the participant</li>
     *  <li>the number of shares assigned to that participant</li>
     *  </ul>
     */
    private final int[][] participantsShares;

    /**
     * Stores the {@code participant} index that is the owner of each shareId in the protocol.
     * Index 0 represents shareId 1 and so on.
     * Shares are assigned sequentially.
     */
    private final int[] shareAllocationTable;
    /**
     * Stores the {@link BlsPublicKey} of each {@code participant} in the protocol.
     * There is one BlsPublicKey per participant
     */
    private final BlsPublicKey[] tssKeyTable;
    /**
     * A map that assigns an index to each participant in the directory
     */
    private final Map<Long, Integer> participantIds;

    /**
     * Constructor
     *
     * @param totalShares total assigned shares.
     * @param sortedParticipantEntries a sorted array of entries per participant
     */
    public TssParticipantAssigmentMapping(
            final int totalShares, @NonNull final ParticipantMappingEntry... sortedParticipantEntries) {
        final int[] shareOwnersTable = new int[totalShares];
        final int[][] participantShares = new int[sortedParticipantEntries.length][2];
        final BlsPublicKey[] tssEncryptionPublicKeyTable = new BlsPublicKey[sortedParticipantEntries.length];
        final Map<Long, Integer> participantIndexes = new HashMap<>(sortedParticipantEntries.length);
        int currentIndex = 0;
        // Iteration of the sorted int representation to make sure we assign the shares deterministically.
        for (int i = 0; i < sortedParticipantEntries.length; i++) {
            final ParticipantMappingEntry entry = sortedParticipantEntries[i];
            tssEncryptionPublicKeyTable[i] = entry.tssEncryptionPublicKey;

            // ParticipantId-->ParticipantIndex
            participantIndexes.put(entry.participantId, i);
            // ShareIndex-->ParticipantIndex
            Arrays.fill(shareOwnersTable, currentIndex, currentIndex + entry.shareCount(), i);
            // ParticipantIndex-->First Share; Share count
            participantShares[i][0] = currentIndex + 1;
            participantShares[i][1] = entry.shareCount();

            currentIndex += entry.shareCount();
        }

        this.participantsShares = participantShares;
        this.shareAllocationTable = shareOwnersTable;
        this.tssKeyTable = tssEncryptionPublicKeyTable;
        this.participantIds = participantIndexes;
    }

    /**
     * Returns the {@code share} owner's {@link BlsPublicKey}.
     *
     * @param shareId the numeric value of the share, not the index.
     * @return a BlsPublicKey belonging to the owner of the share.
     * @throws IllegalArgumentException if the share is higher than the number of shares assigned or if is less or equals to 0
     */
    @NonNull
    public BlsPublicKey tssEncryptionKeyForShareId(final int shareId) {
        if (shareId <= 0 || shareId > shareAllocationTable.length) {
            throw new IllegalArgumentException("Invalid ShareId");
        }
        var shareOwner = shareAllocationTable[shareId - 1];
        return tssKeyTable[shareOwner];
    }

    /**
     * Returns the shares owned by the participant {@code participantId }
     * @param participantId the participant querying for the info.
     * @return the list of shares owned by the participant if it owns any share, an empty list if it doesn't or is not a participant in the scheme.
     */
    @NonNull
    public List<Integer> getSharesForParticipantId(final long participantId) {
        final Integer participantIndex = participantIds.get(participantId);
        if (participantIndex == null) {
            return List.of();
        }
        return IntStream.range(
                        participantsShares[participantIndex][0],
                        participantsShares[participantIndex][0] + participantsShares[participantIndex][1])
                .boxed()
                .toList();
    }

    /**
     * Returns the total number of shares.
     * @return the total number of shares.
     */
    public int totalShares() {
        return shareAllocationTable.length;
    }
    /**
     * Return the list of all the shareIds.
     * In this list, the first share has value of 1.
     * This returns the numeric value of the share, not the index.
     * @return the list of all the shareIds
     */
    @NonNull
    public List<Integer> getShareIds() {
        return IntStream.rangeClosed(1, shareAllocationTable.length).boxed().toList();
    }

    /**
     * Represents an entry for a participant, containing the ID, share count, and public key.
     * @param participantId identification of the participant
     * @param shareCount number of shares owned by the participant represented by this record
     * @param tssEncryptionPublicKey the pairing public key used to encrypt tss share portions designated to the participant represented by this record
     */
    public record ParticipantMappingEntry(
            long participantId, int shareCount, @NonNull BlsPublicKey tssEncryptionPublicKey) {
        /**
         * Constructor
         *
         * @param shareCount number of shares owned by the participant represented by this record
         * @param tssEncryptionPublicKey the pairing public key used to encrypt tss share portions designated to the participant represented by this record
         */
        public ParticipantMappingEntry {
            requireNonNull(tssEncryptionPublicKey, "tssEncryptionPublicKey must not be null");
        }
    }
}
