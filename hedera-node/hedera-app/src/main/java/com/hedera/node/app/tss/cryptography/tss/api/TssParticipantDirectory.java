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

package com.hedera.node.app.tss.cryptography.tss.api;

import com.hedera.node.app.tss.cryptography.bls.BlsPublicKey;
import com.hedera.node.app.tss.cryptography.tss.extensions.TssParticipantAssigmentMapping;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Represents a public directory of participants in a Threshold Signature Scheme (TSS).
 *<p>Each participant has an {@code participantId}, an assigned number of shares and a {@code tssEncryptionPublicKey}.
 *<p>The directory will come up with a consecutive integer representation of each participant of the scheme.
 *
 *<p>The expected {@code participantId} is the unique {@link Long} identification for each participant executing the scheme.</p>
 * <pre>{@code
 * List<PairingPublicKey> tssEncryptionPublicKeys = ...; //retrieve all participant's keys from whatever storage
 * TssParticipantDirectory participantDirectory = TssParticipantDirectory.createBuilder()
 *     //participantId, number-of-shares, tssEncryptionPublicKey
 *     .withParticipant(0, 5, tssEncryptionPublicKeys.get(0))
 *     .withParticipant(1, 2, tssEncryptionPublicKeys.get(1))
 *     .withParticipant(2, 1, tssEncryptionPublicKeys.get(2))
 *     .withParticipant(3, 1, tssEncryptionPublicKeys.get(3))
 *     .withThreshold(6)
 *     .build();
 * }</pre>
 */
public final class TssParticipantDirectory implements TssShareTable<BlsPublicKey> {
    /**
     * While executing the scheme there exist up to two threshold values:<p>
     * <ul>
     *   <li>a candidate threshold.</li>
     *   <li>and, a current threshold.</li>
     * </ul>
     * In directory used to generate TssMessages, the candidate {@code threshold} value defines the number of shares-of-shares that will be created to perform shamir-secret-sharing.<p>
     * The current {@code threshold} value is the minimum number of messages that assures the correct recovery of {@link TssPrivateShare} and {@link TssPublicShare}.<p>
     * In any case, to which of those of this property refers to, depends on whether the directory represents a candidate directory or an adopted one.
     */
    private final int threshold;
    /**
     * Stores different kinds of necessary mappings among them, the {@link BlsPublicKey} of each assigned {@code ShareId}.
     */
    private final TssParticipantAssigmentMapping participantAssigmentMapping;

    /**
     * Constructs a {@link TssParticipantDirectory}.
     *
     * @param participantAssigmentMapping different kinds of necessary mappings
     * @param threshold the threshold value for the TSS
     */
    private TssParticipantDirectory(
            @NonNull final TssParticipantAssigmentMapping participantAssigmentMapping, final int threshold) {
        this.participantAssigmentMapping = participantAssigmentMapping;
        this.threshold = threshold;
    }

    /**
     * Creates a new Builder for {@link TssParticipantDirectory}.
     *
     * @return a new Builder instance
     */
    @NonNull
    public static Builder createBuilder() {
        return new Builder();
    }

    /**
     * While executing the scheme there exist up to two threshold values:<br>
     * <ul>
     *   <li>a candidate threshold.</li>
     *   <li>and, a current threshold.</li>
     * </ul>
     * In directory used to generate TssMessages, the candidate {@code threshold} value defines the number of shares-of-shares that will be created to perform shamir-secret-sharing.<p>
     * The current {@code threshold} value is the minimum number of messages that assures the correct recovery of {@link TssPrivateShare} and {@link TssPublicShare}.<p>
     * In any case, to which of those of this property refers to, depends on whether the directory represents a candidate directory or an adopted one.
     * @return the threshold value.
     */
    public int getThreshold() {
        return threshold;
    }

    /**
     * Returns the total number of shares.
     * @return the total number of shares.
     */
    public int getTotalShares() {
        return participantAssigmentMapping.totalShares();
    }

    /**
     * Return the list of all the shareIds.
     * In this list, the first share has value of 1.
     * This returns the numeric value of the share, not the index.
     * @return the list of all the shareIds
     */
    @NonNull
    public List<Integer> getShareIds() {
        return participantAssigmentMapping.getShareIds();
    }

    /**
     * The list of participant's owned shareIds.
     * This returns the numeric value of the share, not the index.
     * @param participantId the participant that wants to know the ids of its shares.
     * @return the shares owned by the participant {@code participantId}.
     */
    @NonNull
    public List<Integer> ownedShares(long participantId) {
        return participantAssigmentMapping.getSharesForParticipantId(participantId);
    }

    /**
     * Returns a tssShareId owner's {@link BlsPublicKey}.
     * @param shareId the numeric value of the share, not the index.
     * @return a BlsPublicKey belonging to the owner of the share.
     */
    @NonNull
    @Override
    public BlsPublicKey getForShareId(final int shareId) {
        return participantAssigmentMapping.tssEncryptionKeyForShareId(shareId);
    }

    /**
     * A builder for creating {@link TssParticipantDirectory} instances.
     */
    public static class Builder {
        private final Map<Long, TssParticipantAssigmentMapping.ParticipantMappingEntry> participantEntries = new HashMap<>();
        private int threshold;

        private Builder() {}

        /**
         * Sets the threshold value for the TSS.
         *
         * @param threshold the threshold value
         * @return the builder instance
         * @throws IllegalArgumentException if threshold is less than or equals to 0
         */
        @NonNull
        public Builder withThreshold(final int threshold) {
            if (threshold <= 0) {
                throw new IllegalArgumentException("Invalid threshold: " + threshold);
            }
            this.threshold = threshold;
            return this;
        }

        /**
         * Adds a participant entry to the builder.
         *
         * @param participantId the participant unique {@link Long} representation
         * @param numberOfShares the number of shares
         * @param tssEncryptionPublicKey the pairing public key used to encrypt tss share portions designated to the participant represented by this entry
         * @return the builder instance
         * @throws IllegalArgumentException if participantId was previously added.
         */
        @NonNull
        public Builder withParticipant(
                final long participantId,
                final int numberOfShares,
                @NonNull final BlsPublicKey tssEncryptionPublicKey) {
            if (participantEntries.containsKey(participantId))
                throw new IllegalArgumentException(
                        "Participant with participantId " + participantId + " was previously added to the directory");

            participantEntries.put(
                    participantId, new TssParticipantAssigmentMapping.ParticipantMappingEntry(participantId, numberOfShares, tssEncryptionPublicKey));
            return this;
        }

        /**
         * Builds and returns a {@link TssParticipantDirectory} instance based on the provided entries.
         *
         * @return the constructed ParticipantDirectory instance
         * @throws IllegalStateException if there are no configured participants
         * @throws IllegalStateException if the threshold value is higher than the total shares
         */
        @NonNull
        public TssParticipantDirectory build() {

            if (participantEntries.isEmpty()) {
                throw new IllegalStateException("There should be at least one participant in the protocol");
            }

            // Get the total number of shares of to distribute in the protocol
            final int totalShares = participantEntries.values().stream()
                    .map(TssParticipantAssigmentMapping.ParticipantMappingEntry::shareCount)
                    .reduce(0, Integer::sum);

            if (threshold > totalShares) {
                throw new IllegalStateException("Threshold exceeds the number of shares");
            }

            final TssParticipantAssigmentMapping.ParticipantMappingEntry[] sortedEntries = participantEntries.entrySet().stream()
                    .sorted(Entry.comparingByKey())
                    .map(Entry::getValue)
                    .toArray(TssParticipantAssigmentMapping.ParticipantMappingEntry[]::new);

            return new TssParticipantDirectory(
                    new TssParticipantAssigmentMapping(totalShares, sortedEntries), threshold);
        }
    }
}
