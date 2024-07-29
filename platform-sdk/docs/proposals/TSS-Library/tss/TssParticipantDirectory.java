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

import com.hedera.cryptography.signaturescheme.api.PairingPrivateKey;
import com.hedera.cryptography.signaturescheme.api.PairingPublicKey;
import com.hedera.cryptography.signaturescheme.api.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a directory of participants in a Threshold Signature Scheme (TSS).
 * Each participant has associated shares count, public keys.
 * Also includes the current participant private key.
 */
public class TssParticipantDirectory {
    private final List<TssShareId> ownedShareIds;
    private final Map<TssShareId, Integer> ownershipMap;
    private final Map<Integer, PairingPublicKey> publicKeyMap;
    private final PairingPrivateKey persistentPairingPrivateKey;
    private final int threshold;

    /**
     * Constructs a ParticipantDirectory with the specified owned share IDs, ownership map,
     * public key map, persistent pairing private key, and threshold.
     *
     * @param ownedShareIds                the list of owned share IDs
     * @param ownershipMap                 the map of share IDs to participant IDs
     * @param publicKeyMap                 the map of participant IDs to public keys
     * @param persistentPairingPrivateKey  the persistent pairing private key
     * @param threshold                    the threshold value for the TSS
     */
    public TssParticipantDirectory(@NonNull final List<TssShareId> ownedShareIds,
            @NonNull final Map<TssShareId, Integer> ownershipMap,
            @NonNull final Map<Integer, PairingPublicKey> publicKeyMap,
            @NonNull final PairingPrivateKey persistentPairingPrivateKey,
            final int threshold) {
        this.ownedShareIds = List.copyOf(ownedShareIds);
        this.ownershipMap = Map.copyOf(ownershipMap);
        this.publicKeyMap = Map.copyOf(publicKeyMap);
        this.persistentPairingPrivateKey = persistentPairingPrivateKey;
        this.threshold = threshold;
    }

    /**
     * Returns the list of owned share IDs.
     *
     * @return the list of owned share IDs
     */
    public List<TssShareId> getOwnedShareIds() {
        return ownedShareIds;
    }

    /**
     * Returns the persistent pairing private key.
     *
     * @return the persistent pairing private key
     */
    public PairingPrivateKey getPersistentPairingPrivateKey() {
        return persistentPairingPrivateKey;
    }

    /**
     * Returns the threshold value for the TSS.
     *
     * @return the threshold value
     */
    public int getThreshold() {
        return threshold;
    }

    /**
     * Returns a collection of all share IDs.
     *
     * @return a collection of all share IDs
     */
    public Collection<TssShareId> getShareIds() {
        return ownershipMap.keySet();
    }

    /**
     * Returns the totalNumber of shares
     *
     * @return a collection of all share IDs
     */
    public int getTotalNumberOfShares() {
        return ownershipMap.size();
    }

    /**
     * Returns a list of public keys corresponding to the given list of share IDs.
     *
     * @param ids the list of share IDs
     * @return the list of public keys
     */
    public List<PairingPublicKey> getPairingPublicKeys(@NonNull final List<TssShareId> ids) {
        List<PairingPublicKey> publicKeys = new ArrayList<>(ids.size());
        for (TssShareId id : ids) {
            int key = ownershipMap.get(id);
            publicKeys.add(publicKeyMap.get(key));
        }
        return publicKeys;
    }

    /**
     * Creates a new Builder for constructing a ParticipantDirectory.
     *
     * @return a new Builder instance
     */
    public static Builder createBuilder() {
        return new Builder();
    }

    /**
     * A builder for creating ParticipantDirectory instances.
     */
    public static class Builder {
        private SelfEntry selfEntry;
        private final List<ParticipantEntry> participantEntries = new ArrayList<>();
        private int threshold;

        private Builder() {}

        /**
         * Sets the self entry for the builder.
         *
         * @param id the participant ID
         * @param key the pairing private key
         * @return the builder instance
         */
        Builder withSelf(final int id, @NonNull final PairingPrivateKey key) {
            selfEntry = new SelfEntry(id, key);
            return this;
        }

        /**
         * Sets the threshold value for the TSS.
         *
         * @param threshold the threshold value
         * @return the builder instance
         */
        Builder withThreshold(final int threshold) {
            this.threshold = threshold;
            return this;
        }

        /**
         * Adds a participant entry to the builder.
         *
         * @param participantId the participant ID
         * @param numberOfShares the number of shares
         * @param publicKey the pairing public key
         * @return the builder instance
         */
        Builder withParticipant(final int participantId, final int numberOfShares, final @NonNull PairingPublicKey publicKey) {
            participantEntries.add(new ParticipantEntry(participantId, numberOfShares, publicKey));
            return this;
        }

        /**
         * Adds a participant entry with one share to the builder.
         *
         * @param participantId the participant ID
         * @param publicKey the pairing public key
         * @return the builder instance
         */
        Builder withParticipant(final int participantId, final @NonNull PairingPublicKey publicKey) {
            participantEntries.add(new ParticipantEntry(participantId, 1, publicKey));
            return this;
        }

        /**
         * Adds a participant entry with a generated ID and one share to the builder.
         *
         * @param publicKey the pairing public key
         * @return the builder instance
         */
        Builder withParticipant(final @NonNull PairingPublicKey publicKey) {
            participantEntries.add(new ParticipantEntry(participantEntries.size(), 1, publicKey));
            return this;
        }

        /**
         * Builds and returns a ParticipantDirectory instance based on the provided entries and schema.
         *
         * @param schema the signature schema
         * @return the constructed ParticipantDirectory instance
         */
        public TssParticipantDirectory build(SignatureSchema schema) {
            int totalShares = participantEntries.stream().map(ParticipantEntry::shareCount).reduce(0, Integer::sum);

            List<TssShareId> ids = IntStream.range(1, totalShares + 1)
                    .boxed()
                    .map(schema.getField()::elementFromLong)
                    .map(TssShareId::new)
                    .toList();

            Iterator<TssShareId> elementIterator = ids.iterator();
            Map<TssShareId, Integer> ownershipMap = new HashMap<>();
            List<TssShareId> ownedShareIds = new ArrayList<>();

            for (ParticipantEntry record : participantEntries) {
                for (int i = 0; i < record.shareCount(); i++) {
                    if (elementIterator.hasNext()) {
                        TssShareId tssShareId = elementIterator.next();
                        ownershipMap.put(tssShareId, record.id());
                        if (record.id == selfEntry.id) {
                            ownedShareIds.add(tssShareId);
                        }
                    }
                }
            }

            Map<Integer, PairingPublicKey> publicKeyMap = participantEntries.stream()
                    .collect(Collectors.toMap(ParticipantEntry::id, ParticipantEntry::publicKey));

            return new TssParticipantDirectory(ownedShareIds, ownershipMap, publicKeyMap, selfEntry.privateKey, threshold);
        }
    }

    /**
     * Represents an entry for the participant itself, containing the ID and private key.
     */
    record SelfEntry(int id, PairingPrivateKey privateKey) {}

    /**
     * Represents an entry for a participant, containing the ID, share count, and public key.
     */
    record ParticipantEntry(int id, int shareCount, PairingPublicKey publicKey) {}
}