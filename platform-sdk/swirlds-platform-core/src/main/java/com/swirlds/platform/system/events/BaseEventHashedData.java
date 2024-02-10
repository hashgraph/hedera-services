/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.events;

import static com.swirlds.common.io.streams.SerializableDataOutputStream.getSerializedLength;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.OptionalSelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.config.TransactionConfig;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A class used to store base event data that is used to create the hash of that event.
 * <p>
 * A base event is a set of data describing an event at the point when it is created, before it is added to the
 * hashgraph and before its consensus can be determined. Some of this data is used to create a hash of an event
 * and some data is additional and does not affect the hash. This data is split into 2 classes:
 * {@link BaseEventHashedData} and {@link BaseEventUnhashedData}.
 */
public class BaseEventHashedData extends AbstractSerializableHashable
        implements OptionalSelfSerializable<EventSerializationOptions> {
    public static final int TO_STRING_BYTE_ARRAY_LENGTH = 5;
    private static final long CLASS_ID = 0x21c2620e9b6a2243L;

    public static class ClassVersion {
        /**
         * In this version, the transactions contained by this event are encoded using
         * LegacyTransaction class. No longer supported.
         */
        public static final int ORIGINAL = 1;
        /**
         * In this version, the transactions contained by this event are encoded using a newer version Transaction
         * class with different subclasses to support internal system transactions and application transactions
         */
        public static final int TRANSACTION_SUBCLASSES = 2;

        /**
         * In this version, the software version of the node that created this event is included in the event.
         */
        public static final int SOFTWARE_VERSION = 3;

        /**
         * Event descriptors replace the hashes and generation of the parents in the event.
         * Multiple otherParents are supported.
         * birthRound is added for lookup of the effective roster at the time of event creation.
         *
         * @since 0.46.0
         */
        public static final int BIRTH_ROUND = 4;
    }

    /**
     * The version of the serialization to use.  May be overridden by the version encountered when deserializing.
     *
     * DEPRECATED:  remove after 0.46.0 goes to mainnet.
     */
    private int serializedVersion = ClassVersion.BIRTH_ROUND;

    ///////////////////////////////////////
    // immutable, sent during normal syncs, affects the hash that is signed:
    ///////////////////////////////////////

    /** the software version of the node that created this event. */
    private SoftwareVersion softwareVersion;
    /** ID of this event's creator (translate before sending) */
    private NodeId creatorId;
    /** the round number in which this event was created, used to look up the effective roster at that time. */
    private long birthRound;
    /** the self parent event descriptor */
    private EventDescriptor selfParent;
    /** the other parents' event descriptors */
    private List<EventDescriptor> otherParents;
    /** creation time, as claimed by its creator */
    private Instant timeCreated;
    /** the payload: an array of transactions */
    private ConsensusTransactionImpl[] transactions;

    public BaseEventHashedData() {}

    /**
     * Create a BaseEventHashedData object
     *
     * @param softwareVersion
     *      the software version of the node that created this event.
     * @param creatorId
     * 		ID of this event's creator
     * @param selfParent
     *         self parent event descriptor
     * @param otherParents
     *        other parent event descriptors
     * @param birthRound
     *         the round in which this event was created.
     * @param timeCreated
     * 		creation time, as claimed by its creator
     * @param transactions
     * 		the payload: an array of transactions included in this event instance
     */
    public BaseEventHashedData(
            @NonNull SoftwareVersion softwareVersion,
            @NonNull final NodeId creatorId,
            @Nullable final EventDescriptor selfParent,
            @NonNull final List<EventDescriptor> otherParents,
            final long birthRound,
            @NonNull final Instant timeCreated,
            @Nullable final ConsensusTransactionImpl[] transactions) {
        this.softwareVersion = Objects.requireNonNull(softwareVersion, "The softwareVersion must not be null");
        this.creatorId = Objects.requireNonNull(creatorId, "The creatorId must not be null");
        this.selfParent = selfParent;
        Objects.requireNonNull(otherParents, "The otherParents must not be null");
        otherParents.forEach(Objects::requireNonNull);
        this.otherParents = otherParents;
        this.birthRound = birthRound;
        this.timeCreated = Objects.requireNonNull(timeCreated, "The timeCreated must not be null");
        this.transactions = transactions;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.TRANSACTION_SUBCLASSES;
    }

    @Override
    public void serialize(
            @NonNull final SerializableDataOutputStream out, @NonNull final EventSerializationOptions option)
            throws IOException {
        out.writeSerializable(softwareVersion, true);
        if (serializedVersion < ClassVersion.BIRTH_ROUND) {
            out.writeLong(creatorId.id());
            out.writeLong(selfParent != null ? selfParent.getGeneration() : EventConstants.GENERATION_UNDEFINED);
            out.writeLong(
                    !otherParents.isEmpty()
                            ? otherParents.get(0).getGeneration()
                            : EventConstants.GENERATION_UNDEFINED);
            out.writeSerializable(selfParent != null ? selfParent.getHash() : null, false);
            out.writeSerializable(!otherParents.isEmpty() ? otherParents.get(0).getHash() : null, false);
        } else {
            out.writeSerializable(creatorId, false);
            out.writeSerializable(selfParent, false);
            out.writeSerializableList(otherParents, false, true);
            out.writeLong(birthRound);
        }
        out.writeInstant(timeCreated);

        // write serialized length of transaction array first, so during the deserialization proces
        // it is possible to skip transaction array and move on to the next object
        if (option == EventSerializationOptions.OMIT_TRANSACTIONS) {
            out.writeInt(getSerializedLength(null, true, false));
            out.writeSerializableArray(null, true, false);
        } else {
            out.writeInt(getSerializedLength(transactions, true, false));
            // transactions may include both system transactions and application transactions
            // so writeClassId set to true and allSameClass set to false
            out.writeSerializableArray(transactions, true, false);
        }
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serialize(out, EventSerializationOptions.FULL);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        final TransactionConfig transactionConfig = ConfigurationHolder.getConfigData(TransactionConfig.class);
        deserialize(in, version, transactionConfig.maxTransactionCountPerEvent());
    }

    public void deserialize(
            @NonNull final SerializableDataInputStream in, final int version, final int maxTransactionCount)
            throws IOException {
        Objects.requireNonNull(in, "The input stream must not be null");
        serializedVersion = version;
        if (version >= ClassVersion.SOFTWARE_VERSION) {
            softwareVersion = in.readSerializable();
        } else {
            softwareVersion = SoftwareVersion.NO_VERSION;
        }
        if (version < ClassVersion.BIRTH_ROUND) {
            // FUTURE WORK: The creatorId should be a selfSerializable NodeId at some point.
            // Changing the event format may require a HIP.  The old format is preserved for now.
            creatorId = NodeId.deserializeLong(in, false);
            final long selfParentGen = in.readLong();
            final long otherParentGen = in.readLong();
            final Hash selfParentHash = in.readSerializable(false, Hash::new);
            final Hash otherParentHash = in.readSerializable(false, Hash::new);
            selfParent = selfParentHash == null
                    ? null
                    : new EventDescriptor(
                            selfParentHash, creatorId, selfParentGen, EventConstants.BIRTH_ROUND_UNDEFINED);
            // The creator for the other parent descriptor is not here and should be retrieved from the unhashed data.
            otherParents = otherParentHash == null
                    ? Collections.emptyList()
                    : Collections.singletonList(
                            new EventDescriptor(otherParentHash, otherParentGen, EventConstants.BIRTH_ROUND_UNDEFINED));
            birthRound = EventConstants.BIRTH_ROUND_UNDEFINED;
        } else {
            creatorId = in.readSerializable(false, NodeId::new);
            if (creatorId == null) {
                throw new IOException("creatorId is null");
            }
            selfParent = in.readSerializable(false, EventDescriptor::new);
            otherParents = in.readSerializableList(AddressBook.MAX_ADDRESSES, false, EventDescriptor::new);
            birthRound = in.readLong();
        }
        timeCreated = in.readInstant();
        in.readInt(); // read serialized length
        transactions = in.readSerializableArray(ConsensusTransactionImpl[]::new, maxTransactionCount, true);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BaseEventHashedData that = (BaseEventHashedData) o;

        return (Objects.equals(creatorId, that.creatorId))
                && Objects.equals(selfParent, that.selfParent)
                && Objects.equals(otherParents, that.otherParents)
                && birthRound == that.birthRound
                && Objects.equals(timeCreated, that.timeCreated)
                && Arrays.equals(transactions, that.transactions)
                && (softwareVersion.compareTo(that.softwareVersion) == 0);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(softwareVersion, creatorId, selfParent, otherParents, birthRound, timeCreated);
        result = 31 * result + Arrays.hashCode(transactions);
        return result;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("softwareVersion", softwareVersion)
                .append("creatorId", creatorId)
                .append("selfParent", selfParent)
                .append("otherParents", otherParents)
                .append("birthRound", birthRound)
                .append("timeCreated", timeCreated)
                .append("transactions size", transactions == null ? "null" : transactions.length)
                .append("hash", CommonUtils.hex(valueOrNull(getHash()), TO_STRING_BYTE_ARRAY_LENGTH))
                .toString();
    }

    @Nullable
    private byte[] valueOrNull(final Hash hash) {
        return hash == null ? null : hash.getValue();
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return serializedVersion;
    }

    /**
     * Returns the software version of the node that created this event.
     *
     * @return the software version of the node that created this event
     */
    @Nullable
    public SoftwareVersion getSoftwareVersion() {
        return softwareVersion;
    }

    /**
     * The ID of the node that created this event.
     *
     * @return the ID of the node that created this event
     */
    @NonNull
    public NodeId getCreatorId() {
        return creatorId;
    }

    /**
     * Get the birth round of the event.
     *
     * @return the birth round of the event
     */
    public long getBirthRound() {
        return birthRound;
    }

    /**
     * Get the event descriptor for the self parent.
     *
     * @return the event descriptor for the self parent
     */
    @Nullable
    public EventDescriptor getSelfParent() {
        return selfParent;
    }

    /**
     * Get the event descriptors for the other parents.
     *
     * @return the event descriptors for the other parents
     */
    @NonNull
    public List<EventDescriptor> getOtherParents() {
        return otherParents;
    }

    /**
     * Get the self parent generation
     *
     * @return the self parent generation
     */
    public long getSelfParentGen() {
        if (selfParent == null) {
            return EventConstants.GENERATION_UNDEFINED;
        }
        return selfParent.getGeneration();
    }

    /**
     * Get the maximum generation of the other parents.
     *
     * @return the maximum generation of the other parents
     * @deprecated this method should be replaced since there can be multiple other parents.
     */
    public long getOtherParentGen() {
        if (otherParents == null || otherParents.isEmpty()) {
            return EventConstants.GENERATION_UNDEFINED;
        }
        if (otherParents.size() == 1) {
            return otherParents.get(0).getGeneration();
        }
        // 0.46.0 adds support for multiple other parents in the serialization scheme, but not yet in the
        // implementation. This exception should never be reached unless we have multiple parents and need to
        // update the implementation.
        throw new UnsupportedOperationException("Multiple other parents is not supported yet");
    }

    /**
     * Get the hash of the self parent.
     *
     * @return the hash of the self parent
     */
    @Nullable
    public Hash getSelfParentHash() {
        if (selfParent == null) {
            return null;
        }
        return selfParent.getHash();
    }

    /**
     * Get the hash of the other parent with the maximum generation.
     *
     * @return the hash of the other parent with the maximum generation
     * @deprecated
     */
    @Nullable
    public Hash getOtherParentHash() {
        if (otherParents == null || otherParents.isEmpty()) {
            return null;
        }
        if (otherParents.size() == 1) {
            return otherParents.get(0).getHash();
        }
        // 0.46.0 adds support for multiple other parents in the serialization scheme, but not yet in the
        // implementation. This exception should never be reached unless we have multiple parents and need to
        // update the implementation.
        throw new UnsupportedOperationException("Multiple other parents is not supported yet");
    }

    /**
     *  Check if the event has a self parent.
     * @return true if the event has a self parent
     */
    public boolean hasSelfParent() {
        return selfParent != null;
    }

    /**
     * Check if the event has other parents.
     * @return true if the event has other parents
     */
    public boolean hasOtherParent() {
        return otherParents != null && !otherParents.isEmpty();
    }

    /**
     * Get the hash value of the parent event.
     * @return the hash value of the parent event
     */
    @Nullable
    public byte[] getSelfParentHashValue() {
        return selfParent == null ? null : getSelfParentHash().getValue();
    }

    /**
     * Get the hash value of the other parent with the maximum generation.
     * @return  the hash value of the other parent with the maximum generation
     */
    @Nullable
    public byte[] getOtherParentHashValue() {
        return otherParents.isEmpty() ? null : getOtherParentHash().getValue();
    }

    @NonNull
    public Instant getTimeCreated() {
        return timeCreated;
    }

    /**
     * @return array of transactions inside this event instance
     */
    @Nullable
    public ConsensusTransactionImpl[] getTransactions() {
        return transactions;
    }

    public long getGeneration() {
        return calculateGeneration(getSelfParentGen(), getOtherParentGen());
    }

    /**
     * Calculates the generation of an event based on its parents generations
     *
     * @param selfParentGeneration
     * 		the generation of the self parent
     * @param otherParentGeneration
     * 		the generation of the other parent
     * @return the generation of the event
     */
    public static long calculateGeneration(final long selfParentGeneration, final long otherParentGeneration) {
        return 1 + Math.max(selfParentGeneration, otherParentGeneration);
    }

    /**
     * Create an event descriptor for this event.
     *
     * @return an event descriptor for this event
     */
    @NonNull
    public EventDescriptor createEventDescriptor() {
        return new EventDescriptor(getHash(), getCreatorId(), getGeneration(), getBirthRound());
    }
}
