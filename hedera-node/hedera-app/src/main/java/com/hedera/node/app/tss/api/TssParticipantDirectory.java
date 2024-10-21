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

package com.hedera.node.app.tss.api;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.tss.pairings.PairingPrivateKey;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.pairings.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Represents a directory of participants in a Threshold Signature Scheme (TSS).
 *<p>Each participant has an associated id (called {@code participantId}), shares count and a tss encryption public key.
 * It is responsibility of the user to assign each participant with a different deterministic integer representation.</p>
 *
 *<p>The current participant is represented by a {@code self} entry, and includes {@code participantId}'s id and the tss decryption private key.</p>
 *<p>The expected {@code participantId} is the unique {@link Integer} identification for each participant executing the scheme.</p>
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
    /**
     * A sorted list of unique integer representations of each participant in the protocol.
     */
    private final List<Integer> sortedParticipantIds;
    /**
     * The list of owned {@link TssShareId} by the participant that created this directory.
     */
    private final List<TssShareId> currentParticipantOwnedShares;
    /**
     * Stores the owner ({@code participantId}) of each {@link TssShareId} in the protocol.
     */
    private final Map<TssShareId, Integer> shareAllocationMap;
    /**
     * Stores the {@link PairingPublicKey} of each {@code participantId} in the protocol.
     */
    private final Map<Integer, PairingPublicKey> tssEncryptionPublicKeyMap;
    /**
     * The storage that holds the key to decrypt TssMessage parts intended for the participant that created this directory.
     * It is transient to assure it does not get serialized and exposed outside.
     */
    private final PrivateKeyStore tssEncryptionPrivateKey;
    /**
     * The minimum value that allows the recovery of Private and Public shares and that guarantees a valid signature.
     */
    private final int threshold;

    /**
     * Constructs a {@link TssParticipantDirectory} with the specified owned share IDs, share ownership map, public key
     * map, persistent pairing private key, and threshold.
     * <p>
     * A unique integer represents each participant in the protocol and is used as {@code participantId}
     *
     * @param sortedParticipantIds the sorted list of {@code participantId}s
     * @param currentParticipantOwnedShares the list of owned share IDs
     * @param shareAllocationMap the map of share IDs to the {@code participantId} of each participant in the
     *                                  protocol.
     * @param tssEncryptionPublicKeyMap the map of participant IDs to public keys
     * @param tssEncryptionPrivateKeyStore the persistent pairing private key store
     * @param threshold the threshold value for the TSS
     */
    private TssParticipantDirectory(
            @NonNull final List<Integer> sortedParticipantIds,
            @NonNull final List<TssShareId> currentParticipantOwnedShares,
            @NonNull final Map<TssShareId, Integer> shareAllocationMap,
            @NonNull final Map<Integer, PairingPublicKey> tssEncryptionPublicKeyMap,
            @NonNull final PrivateKeyStore tssEncryptionPrivateKeyStore,
            final int threshold) {
        this.sortedParticipantIds =
                List.copyOf(Objects.requireNonNull(sortedParticipantIds, "sortedParticipantIds must not be null"));
        this.currentParticipantOwnedShares = List.copyOf(Objects.requireNonNull(
                currentParticipantOwnedShares, "currentParticipantOwnedShares must not be null"));
        this.shareAllocationMap =
                Map.copyOf(Objects.requireNonNull(shareAllocationMap, "shareAllocationMap must not be null"));
        this.tssEncryptionPublicKeyMap = Map.copyOf(
                Objects.requireNonNull(tssEncryptionPublicKeyMap, "tssEncryptionPublicKeyMap must not be null"));
        this.tssEncryptionPrivateKey =
                requireNonNull(tssEncryptionPrivateKeyStore, "tssEncryptionPrivateKeyStore must not be null");
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

    public Map<Integer, List<TssShareId>> getSharesById() {
        Map<Integer, List<TssShareId>> sharesById = new HashMap<>();
        for (Entry<TssShareId, Integer> entry : shareAllocationMap.entrySet()) {
            sharesById.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        return sharesById;
    }

    /**
     * Returns the shares owned by the participant represented as self.
     *
     * @return the shares owned by the participant represented as self.
     */
    @NonNull
    public List<TssShareId> getCurrentParticipantOwnedShares() {
        return currentParticipantOwnedShares;
    }

    /**
     * Return the list of all the shareIds.
     *
     * @return the list of all the shareIds
     */
    @NonNull
    public List<TssShareId> getShareIds() {
        return shareAllocationMap.entrySet().stream()
                .sorted(Entry.comparingByValue())
                .map(Entry::getKey)
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
         * @param participantId the participant unique {@link Integer} representation
         * @param tssEncryptionPrivateKey the pairing private key used to decrypt tss share portions
         * @return the builder instance
         */
        @NonNull
        public Builder withSelf(final int participantId, @NonNull final PairingPrivateKey tssEncryptionPrivateKey) {
            if (selfEntry != null) {
                throw new IllegalArgumentException("There is already an for the current participant");
            }
            selfEntry = new SelfEntry(participantId, new PrivateKeyStore(tssEncryptionPrivateKey));
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
         * @param participantId the participant unique {@link Integer} representation
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

            // Get the total number of shares of to distribute in the protocol
            final int totalShares = participantEntries.values().stream()
                    .map(ParticipantEntry::shareCount)
                    .reduce(0, Integer::sum);

            if (threshold > totalShares) {
                throw new IllegalStateException("Threshold exceeds the number of shares");
            }

            // Create a sorted list of ShareId's from 1 to totalShares + 1
            final List<TssShareId> ids = IntStream.range(1, totalShares + 1)
                    .boxed()
                    // In the future, when paring api is implemented, we need to:
                    // .map(schema.getField()::elementFromLong)
                    .map(TssShareId::new)
                    .toList();

            // Create a sorted list of participants to make sure we assign the shares in the right order.
            final List<Integer> sortedParticipantIds =
                    participantEntries.keySet().stream().sorted().toList();

            final Map<TssShareId, Integer> sharesAllocationMap =
                    new HashMap<>(); /*To keep track of each share id owner*/
            final List<TssShareId> currentParticipantOwnedShareIds =
                    new ArrayList<>(); /*To keep track of the shares owned by the creator of this directory*/
            final Map<Integer, PairingPublicKey> tssEncryptionPublicKeyMap =
                    new HashMap<>(); /*The encryption key of each participant*/

            final AtomicInteger assignedShares = new AtomicInteger(0); /*Counter for assigned shares*/

            // Iteration of the sorted int representation to make sure we assign the shares deterministically.
            sortedParticipantIds.forEach(participantId -> {
                final ParticipantEntry entry = participantEntries.get(participantId);

                // Add the public encryption key for each participant id in the iteration.
                // here the order is not important, but we reuse the iteration.
                tssEncryptionPublicKeyMap.put(participantId, entry.tssEncryptionPublicKey());

                IntStream.range(0, entry.shareCount()).forEach(i -> {
                    final TssShareId tssShareId = ids.get(assignedShares.getAndIncrement());
                    sharesAllocationMap.put(tssShareId, participantId);
                    // Keep a separated collection for the current participant shares
                    if (participantId.equals(selfEntry.participantId())) {
                        currentParticipantOwnedShareIds.add(tssShareId);
                    }
                });
            });

            return new TssParticipantDirectory(
                    sortedParticipantIds,
                    currentParticipantOwnedShareIds,
                    sharesAllocationMap,
                    tssEncryptionPublicKeyMap,
                    selfEntry.tssEncryptionPrivateKeyStore,
                    threshold);
        }
    }

    /**
     * A class for storing the private key as protection for unintentional serialization or exposition of the data.
     */
    private static final class PrivateKeyStore {
        transient PairingPrivateKey privateKey;

        public PrivateKeyStore(@NonNull final PairingPrivateKey privateKey) {
            this.privateKey = privateKey;
        }

        @NonNull
        private PairingPrivateKey getPrivateKey() {
            return privateKey;
        }

        @Override
        public String toString() {
            return "PrivateKeyStore<>";
        }
    }

    /**
     * Represents an entry for the participant executing the protocol, containing the ID and private key.
     * @param participantId identification of the participant
     */
    private record SelfEntry(int participantId, @NonNull PrivateKeyStore tssEncryptionPrivateKeyStore) {
        /**
         * Constructor
         * @param participantId identification of the participant
         */
        public SelfEntry {
            requireNonNull(tssEncryptionPrivateKeyStore, "tssEncryptionPrivateKeyStore must not be null");
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
