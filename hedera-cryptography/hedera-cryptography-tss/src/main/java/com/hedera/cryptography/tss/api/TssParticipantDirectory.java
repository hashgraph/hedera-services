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

package com.hedera.cryptography.tss.api;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import com.hedera.cryptography.signaturescheme.api.PairingPrivateKey;
import com.hedera.cryptography.signaturescheme.api.PairingPublicKey;
import com.hedera.cryptography.signaturescheme.api.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Represents a directory of participants in a Threshold Signature Scheme (TSS).
 * Each participant has associated id, shares count and a tss encryption public keys.
 * It also includes current participant's tss decryption private key.
 *
 * <pre>{@code
 * PairingPrivateKey tssDecryptionPrivateKey = ...;
 * List<PairingPublicKey> tssEncryptionPublicKeys = ...;
 * TssParticipantDirectory participantDirectory = TssParticipantDirectory.createBuilder()
 *     //id, tss private decryption key
 *     .self(0, persistentParticipantKey)
 *     //id, number of shares, tss public encryption key
 *     .withParticipant(0, 5, tssEncryptionPublicKeys.get(0))
 *     .withParticipant(1, 2, tssEncryptionPublicKeys.get(1))
 *     .withParticipant(2, 1, tssEncryptionPublicKeys.get(2))
 *     .withParticipant(3, 1, tssEncryptionPublicKeys.get(3))
 *     .withThreshold(5)
 *     .build(signatureScheme);
 * }</pre>
 *
 */
public final class TssParticipantDirectory {
    private final List<TssShareId> ownedShareIds;
    private final Map<TssShareId, Integer> shareOwnersMap;
    private final Map<Integer, PairingPublicKey> publicKeyMap;
    private final PairingPrivateKey persistentPairingPrivateKey;
    private final int threshold;

    /**
     * Constructs a {@link TssParticipantDirectory} with the specified owned share IDs, share ownership map,
     * public key map, persistent pairing private key, and threshold.
     *
     * @param ownedShareIds the list of owned share IDs
     * @param shareOwnersMap the map of share IDs to participant IDs
     * @param tssEncryptionPublicKeyMap the map of participant IDs to public keys
     * @param tssEncryptionPrivateKey the persistent pairing private key
     * @param threshold the threshold value for the TSS
     */
    private TssParticipantDirectory(
            @NonNull final List<TssShareId> ownedShareIds,
            @NonNull final Map<TssShareId, Integer> shareOwnersMap,
            @NonNull final Map<Integer, PairingPublicKey> tssEncryptionPublicKeyMap,
            @NonNull final PairingPrivateKey tssEncryptionPrivateKey,
            final int threshold) {
        this.ownedShareIds = ownedShareIds;
        this.shareOwnersMap = shareOwnersMap;
        this.publicKeyMap = tssEncryptionPublicKeyMap;
        this.persistentPairingPrivateKey = tssEncryptionPrivateKey;
        this.threshold = threshold;
    }

    /**
     * Creates a new Builder for constructing a {@link TssParticipantDirectory}.
     *
     * @return a new Builder instance
     */
    @NonNull
    public static Builder createBuilder() {
        return new Builder();
    }

    /**
     * Returns the threshold value.
     *
     * @return the threshold value
     */
    public int getThreshold() {
        return threshold;
    }

    /**
     * Returns the shares owned by the participant represented as self.
     *
     * @return the shares owned by the participant represented as self.
     */
    @NonNull
    public List<TssShareId> getOwnedShareIds() {
        return ownedShareIds;
    }

    /**
     * Return the list of all the shareIds.
     *
     * @return the list of all the shareIds
     */
    @NonNull
    public List<TssShareId> getShareIds() {
        return shareOwnersMap.keySet().stream()
                .sorted(Comparator.comparing(TssShareId::idElement))
                .toList();
    }

    /**
     * A builder for creating {@link TssParticipantDirectory} instances.
     */
    public static class Builder {
        private SelfEntry selfEntry;
        private final Map<Integer, ParticipantEntry> participantEntries = new HashMap<>();
        private int threshold;

        private Builder() {}

        /**
         * Sets the self entry for the builder.
         *
         * @param participantId the participant ID
         * @param tssEncryptionPrivateKey the pairing private key used to decrypt tss share portions
         * @return the builder instance
         */
        @NonNull
        public Builder withSelf(final int participantId, @NonNull final PairingPrivateKey tssEncryptionPrivateKey) {
            if (selfEntry != null) {
                throw new IllegalArgumentException("There is already an for the current participant");
            }
            selfEntry = new SelfEntry(participantId, tssEncryptionPrivateKey);
            return this;
        }

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
         * @param participantId the participant ID
         * @param numberOfShares the number of shares
         * @param tssEncryptionPublicKey the pairing public key used to encrypt tss share portions designated to the participant represented by this entry
         * @return the builder instance
         * @throws IllegalArgumentException if participantId was previously added.
         */
        @NonNull
        public Builder withParticipant(
                final int participantId,
                final int numberOfShares,
                @NonNull final PairingPublicKey tssEncryptionPublicKey) {
            if (participantEntries.containsKey(participantId))
                throw new IllegalArgumentException(
                        "Participant with id " + participantId + " was previously added to the directory");

            participantEntries.put(participantId, new ParticipantEntry(numberOfShares, tssEncryptionPublicKey));
            return this;
        }

        /**
         * Builds and returns a {@link TssParticipantDirectory} instance based on the provided entries and schema.
         *
         * @param schema the signature schema
         * @return the constructed ParticipantDirectory instance
         * @throws NullPointerException if schema is null
         * @throws IllegalStateException if there is no entry for the current participant
         * @throws IllegalStateException if there are no configured participants
         * @throws IllegalStateException if the threshold value is higher than the total shares
         */
        @NonNull
        public TssParticipantDirectory build(@NonNull final SignatureSchema schema) {
            Objects.requireNonNull(schema, "Schema must not be null");

            if (isNull(selfEntry)) {
                throw new IllegalStateException("There should be an entry for the current participant");
            }

            if (participantEntries.isEmpty()) {
                throw new IllegalStateException("There should be at least one participant in the protocol");
            }

            if (!participantEntries.containsKey(selfEntry.participantId())) {
                throw new IllegalStateException(
                        "The participant list does not contain a reference to the current participant");
            }

            int totalShares = participantEntries.values().stream()
                    .map(ParticipantEntry::shareCount)
                    .reduce(0, Integer::sum);

            if (threshold > totalShares) {
                throw new IllegalStateException("Threshold exceeds the number of shares");
            }

            final List<TssShareId> ids = IntStream.range(1, totalShares + 1)
                    .boxed()
                    // .map(schema.getField()::elementFromLong)
                    .map(TssShareId::new)
                    .toList();

            final Iterator<TssShareId> elementIterator = ids.iterator();
            final Map<TssShareId, Integer> shareOwnersMap = new HashMap<>();
            final List<TssShareId> currentParticipantOwnedShareIds = new ArrayList<>();
            final List<Integer> sortedParticipantIds =
                    participantEntries.keySet().stream().sorted().toList();
            final Map<Integer, PairingPublicKey> tssEncryptionPublicKeyMap = new HashMap<>();

            for (final Integer participantId : sortedParticipantIds) {
                final ParticipantEntry entry = participantEntries.get(participantId);
                tssEncryptionPublicKeyMap.put(participantId, entry.tssEncryptionPublicKey());
                for (int i = 0; i < entry.shareCount(); i++) {
                    if (elementIterator.hasNext()) {
                        final TssShareId tssShareId = elementIterator.next();
                        shareOwnersMap.put(tssShareId, participantId);
                        if (participantId == selfEntry.participantId()) {
                            currentParticipantOwnedShareIds.add(tssShareId);
                        }
                    }
                }
            }

            return new TssParticipantDirectory(
                    List.copyOf(currentParticipantOwnedShareIds),
                    Map.copyOf(shareOwnersMap),
                    Map.copyOf(tssEncryptionPublicKeyMap),
                    selfEntry.tssEncryptionPrivateKey,
                    threshold);
        }
    }

    /**
     * Represents an entry for the participant executing the protocol, containing the ID and private key.
     * @param participantId identification of the participant
     */
    private record SelfEntry(int participantId, @NonNull PairingPrivateKey tssEncryptionPrivateKey) {
        /**
         * Constructor
         * @param participantId identification of the participant
         */
        public SelfEntry {
            requireNonNull(tssEncryptionPrivateKey, "tssEncryptionPrivateKey must not be null");
        }
    }

    /**
     * Represents an entry for a participant, containing the ID, share count, and public key.
     * @param shareCount number of shares owned by the participant represented by this record
     * @param tssEncryptionPublicKey the pairing public key used to encrypt tss share portions designated to the participant represented by this record
     */
    private record ParticipantEntry(int shareCount, @NonNull PairingPublicKey tssEncryptionPublicKey) {
        /**
         * Constructor
         *
         * @param shareCount number of shares owned by the participant represented by this record
         * @param tssEncryptionPublicKey the pairing public key used to encrypt tss share portions designated to the participant represented by this record
         */
        public ParticipantEntry {
            requireNonNull(tssEncryptionPublicKey, "tssEncryptionPublicKey must not be null");
        }
    }
}
