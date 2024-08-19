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

import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventDescriptor;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.AbstractHashable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import com.swirlds.platform.util.TransactionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A class used to store base event data that is used to create the hash of that event.
 * <p>
 * A base event is a set of data describing an event at the point when it is created, before it is added to the
 * hashgraph and before its consensus can be determined. Some of this data is used to create a hash of an event and some
 * data is additional and does not affect the hash.
 */
public class UnsignedEvent extends AbstractHashable {
    public static final int TO_STRING_BYTE_ARRAY_LENGTH = 5;
    private static final long CLASS_ID = 0x21c2620e9b6a2243L;

    private static final long APPLICATION_TRANSACTION_CLASS_ID = 0x9ff79186f4c4db97L;
    private static final int APPLICATION_TRANSACTION_VERSION = 1;
    private static final long STATE_SIGNATURE_CLASS_ID = 0xaf7024c653caabf4L;
    private static final int STATE_SIGNATURE_VERSION = 3;

    public static class ClassVersion {
        /**
         * Event descriptors replace the hashes and generation of the parents in the event. Multiple otherParents are
         * supported. birthRound is added for lookup of the effective roster at the time of event creation.
         *
         * @since 0.46.0
         */
        public static final int BIRTH_ROUND = 4;
    }

    /**
     * the software version of the node that created this event.
     */
    private final SoftwareVersion softwareVersion;

    /**
     * ID of this event's creator (translate before sending)
     */
    private final NodeId creatorId;

    /**
     * the self parent event descriptor
     */
    private final EventDescriptorWrapper selfParent;

    /**
     * the other parents' event descriptors
     */
    private final List<EventDescriptorWrapper> otherParents;

    /** a combined list of all parents, selfParent + otherParents */
    private final List<EventDescriptorWrapper> allParents;

    /**
     * creation time, as claimed by its creator
     */
    private final Instant timeCreated;

    /**
     * list of transactions
     */
    private final List<TransactionWrapper> transactions;

    /**
     * The core event data.
     */
    private final EventCore eventCore;

    /**
     * The transactions of the event.
     */
    private final List<EventTransaction> eventTransactions;

    /**
     * The event descriptor for this event. Is not itself hashed.
     */
    private EventDescriptorWrapper descriptor;

    /**
     * Create a UnsignedEvent object
     *
     * @param softwareVersion the software version of the node that created this event.
     * @param creatorId       ID of this event's creator
     * @param selfParent      self parent event descriptor
     * @param otherParents    other parent event descriptors
     * @param birthRound      the round in which this event was created.
     * @param timeCreated     creation time, as claimed by its creator
     * @param transactions    list of transactions included in this event instance
     */
    public UnsignedEvent(
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final NodeId creatorId,
            @Nullable final EventDescriptorWrapper selfParent,
            @NonNull final List<EventDescriptorWrapper> otherParents,
            final long birthRound,
            @NonNull final Instant timeCreated,
            @NonNull final List<EventTransaction> transactions) {
        Objects.requireNonNull(transactions, "The transactions must not be null");
        this.softwareVersion = Objects.requireNonNull(softwareVersion, "The softwareVersion must not be null");
        this.creatorId = Objects.requireNonNull(creatorId, "The creatorId must not be null");
        this.selfParent = selfParent;
        Objects.requireNonNull(otherParents, "The otherParents must not be null");
        otherParents.forEach(Objects::requireNonNull);
        this.otherParents = otherParents;
        this.allParents = createAllParentsList();
        this.timeCreated = Objects.requireNonNull(timeCreated, "The timeCreated must not be null");

        this.eventTransactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.eventCore = new EventCore(
                creatorId.id(),
                birthRound,
                HapiUtils.asTimestamp(timeCreated),
                this.allParents.stream()
                        .map(EventDescriptorWrapper::eventDescriptor)
                        .toList(),
                softwareVersion.getPbjSemanticVersion());
        this.transactions = transactions.stream().map(TransactionWrapper::new).toList();
    }

    /**
     * Serialize the event for the purpose of creating a hash.
     * Since events are being migrated to protobuf, calculating the hash of an event will change.
     *
     * @param out the stream to which this object is to be written
     * @throws IOException if unsupported transaction types are encountered
     */
    public void serializeLegacyHashBytes(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeLong(CLASS_ID);
        serialize(out);
    }

    /**
     * Serialize the event
     *
     * @param out the stream to which this object is to be written
     *
     * @throws IOException if unsupported transaction types are encountered
     */
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeInt(ClassVersion.BIRTH_ROUND);
        out.writeSerializable(softwareVersion, true);
        out.writeInt(NodeId.ClassVersion.ORIGINAL);
        out.writeLong(eventCore.creatorNodeId());
        EventDescriptorWrapper.serialize(out, selfParent);
        EventDescriptorWrapper.serializeList(out, otherParents);
        out.writeLong(eventCore.birthRound());
        out.writeInstant(HapiUtils.asInstant(eventCore.timeCreated()));

        // write serialized length of transaction array first, so during the deserialization proces
        // it is possible to skip transaction array and move on to the next object
        out.writeInt(TransactionUtils.getLegacyObjectSize(eventTransactions));
        // transactions may include both system transactions and application transactions
        // so writeClassId set to true and allSameClass set to false
        out.writeInt(eventTransactions.size());
        if (!eventTransactions.isEmpty()) {
            final boolean allSameClass = false;
            out.writeBoolean(allSameClass);
        }
        for (final EventTransaction transaction : eventTransactions) {
            switch (transaction.transaction().kind()) {
                case APPLICATION_TRANSACTION:
                    serializeApplicationTransaction(out, transaction);
                    break;
                case STATE_SIGNATURE_TRANSACTION:
                    serializeStateSignatureTransaction(out, transaction);
                    break;
                default:
                    throw new IOException("Unknown transaction type: "
                            + transaction.transaction().kind());
            }
        }
    }

    private static void serializeApplicationTransaction(
            @NonNull final SerializableDataOutputStream out, @NonNull final EventTransaction transaction)
            throws IOException {
        out.writeLong(APPLICATION_TRANSACTION_CLASS_ID);
        out.writeInt(APPLICATION_TRANSACTION_VERSION);
        final Bytes bytes = transaction.transaction().as();
        out.writeInt((int) bytes.length());
        bytes.writeTo(out);
    }

    private static void serializeStateSignatureTransaction(
            @NonNull final SerializableDataOutputStream out, @NonNull final EventTransaction transaction)
            throws IOException {
        final StateSignatureTransaction stateSignatureTransaction =
                transaction.transaction().as();

        out.writeLong(STATE_SIGNATURE_CLASS_ID);
        out.writeInt(STATE_SIGNATURE_VERSION);
        out.writeInt((int) stateSignatureTransaction.signature().length());
        stateSignatureTransaction.signature().writeTo(out);

        out.writeInt((int) stateSignatureTransaction.hash().length());
        stateSignatureTransaction.hash().writeTo(out);

        out.writeLong(stateSignatureTransaction.round());
        out.writeInt(Integer.MIN_VALUE); // epochHash is always null
    }

    /**
     * Deserialize the event.
     *
     * @param in the stream from which this object is to be read
     * @return the deserialized event
     * @throws IOException if unsupported transaction types are encountered
     */
    public static UnsignedEvent deserialize(@NonNull final SerializableDataInputStream in) throws IOException {
        final int version = in.readInt();
        if (version != ClassVersion.BIRTH_ROUND) {
            throw new IOException("Unsupported version: " + version);
        }

        Objects.requireNonNull(in, "The input stream must not be null");
        final SoftwareVersion softwareVersion =
                in.readSerializable(StaticSoftwareVersion.getSoftwareVersionClassIdSet());

        final NodeId creatorId = in.readSerializable(false, NodeId::new);
        if (creatorId == null) {
            throw new IOException("creatorId is null");
        }
        final EventDescriptorWrapper selfParent = EventDescriptorWrapper.deserialize(in);
        final List<EventDescriptorWrapper> otherParents = EventDescriptorWrapper.deserializeList(in);
        final long birthRound = in.readLong();

        final Instant timeCreated = in.readInstant();
        in.readInt(); // read serialized length
        final List<EventTransaction> transactionList = new ArrayList<>();
        final int transactionSize = in.readInt();
        if (transactionSize > 0) {
            in.readBoolean(); // allSameClass
        }
        for (int i = 0; i < transactionSize; i++) {
            final long classId = in.readLong();
            final int classVersion = in.readInt();
            if (classId == APPLICATION_TRANSACTION_CLASS_ID) {
                final OneOf<TransactionOneOfType> oneOf = new OneOf<>(
                        TransactionOneOfType.APPLICATION_TRANSACTION,
                        deserializeApplicationTransaction(in, classVersion));
                transactionList.add(new EventTransaction(oneOf));
            } else if (classId == STATE_SIGNATURE_CLASS_ID) {
                final OneOf<TransactionOneOfType> oneOf = new OneOf<>(
                        TransactionOneOfType.STATE_SIGNATURE_TRANSACTION,
                        deserializeStateSignatureTransaction(in, classVersion));
                transactionList.add(new EventTransaction(oneOf));
            } else {
                throw new IOException("Unknown classId: " + classId);
            }
        }

        return new UnsignedEvent(
                softwareVersion, creatorId, selfParent, otherParents, birthRound, timeCreated, transactionList);
    }

    @Nullable
    private static Bytes deserializeApplicationTransaction(
            @NonNull final SerializableDataInputStream in, final int classVersion) throws IOException {
        if (classVersion != APPLICATION_TRANSACTION_VERSION) {
            throw new IOException("Unsupported application class version: " + classVersion);
        }
        final byte[] bytes = in.readByteArray(1000000);

        if (bytes != null) {
            return Bytes.wrap(bytes);
        }
        return null;
    }

    private static StateSignatureTransaction deserializeStateSignatureTransaction(
            SerializableDataInputStream in, int classVersion) throws IOException {
        if (classVersion != STATE_SIGNATURE_VERSION) {
            throw new IOException("Unsupported state signature class version: " + classVersion);
        }
        final byte[] sigBytes = in.readByteArray(1000000);
        final byte[] hashBytes = in.readByteArray(1000000);
        final long round = in.readLong();
        in.readInt(); // epochHash is always null
        return new StateSignatureTransaction(round, Bytes.wrap(sigBytes), Bytes.wrap(hashBytes));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final UnsignedEvent that = (UnsignedEvent) o;

        return (Objects.equals(creatorId, that.creatorId))
                && Objects.equals(selfParent, that.selfParent)
                && Objects.equals(otherParents, that.otherParents)
                && eventCore.birthRound() == that.eventCore.birthRound()
                && Objects.equals(timeCreated, that.timeCreated)
                && Objects.equals(transactions, that.transactions)
                && (softwareVersion.compareTo(that.softwareVersion) == 0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getSoftwareVersion(),
                getCreatorId(),
                getSelfParent(),
                getOtherParents(),
                getTimeCreated(),
                getTransactions());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("softwareVersion", softwareVersion)
                .append("creatorId", creatorId)
                .append("selfParent", selfParent)
                .append("otherParents", otherParents)
                .append("birthRound", eventCore.birthRound())
                .append("timeCreated", timeCreated)
                .append("transactions size", transactions == null ? "null" : transactions.size())
                .append("hash", getHash() == null ? "null" : getHash().toHex(TO_STRING_BYTE_ARRAY_LENGTH))
                .toString();
    }

    /**
     * Returns the software version of the node that created this event.
     *
     * @return the software version of the node that created this event
     */
    @NonNull
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
        return eventCore.birthRound();
    }

    /**
     * Get the event descriptor for the self parent.
     *
     * @return the event descriptor for the self parent
     */
    @Nullable
    public EventDescriptorWrapper getSelfParent() {
        return selfParent;
    }

    /**
     * Get the event descriptors for the other parents.
     *
     * @return the event descriptors for the other parents
     */
    @NonNull
    public List<EventDescriptorWrapper> getOtherParents() {
        return otherParents;
    }

    /** @return a list of all parents, self parent (if any), + all other parents */
    @NonNull
    public List<EventDescriptorWrapper> getAllParents() {
        return allParents;
    }

    @NonNull
    private List<EventDescriptorWrapper> createAllParentsList() {
        return !hasSelfParent()
                ? otherParents
                : Stream.concat(Stream.of(selfParent), otherParents.stream()).toList();
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
        return selfParent.eventDescriptor().generation();
    }

    /**
     * Get the maximum generation of the other parents.
     *
     * @return the maximum generation of the other parents
     * @deprecated this method should be replaced since there can be multiple other parents.
     */
    @Deprecated
    public long getOtherParentGen() {
        if (otherParents == null || otherParents.isEmpty()) {
            return EventConstants.GENERATION_UNDEFINED;
        }
        if (otherParents.size() == 1) {
            return otherParents.getFirst().eventDescriptor().generation();
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
        return selfParent.hash();
    }

    /**
     * Get the hash of the other parent with the maximum generation.
     *
     * @return the hash of the other parent with the maximum generation
     */
    @Nullable
    @Deprecated
    public Hash getOtherParentHash() {
        if (otherParents == null || otherParents.isEmpty()) {
            return null;
        }
        if (otherParents.size() == 1) {
            return otherParents.getFirst().hash();
        }
        // 0.46.0 adds support for multiple other parents in the serialization scheme, but not yet in the
        // implementation. This exception should never be reached unless we have multiple parents and need to
        // update the implementation.
        throw new UnsupportedOperationException("Multiple other parents is not supported yet");
    }

    /**
     * Check if the event has a self parent.
     *
     * @return true if the event has a self parent
     */
    public boolean hasSelfParent() {
        return selfParent != null;
    }

    /**
     * Check if the event has other parents.
     *
     * @return true if the event has other parents
     */
    public boolean hasOtherParent() {
        return otherParents != null && !otherParents.isEmpty();
    }

    @NonNull
    public Instant getTimeCreated() {
        return timeCreated;
    }

    /**
     * @return array of transactions inside this event instance
     */
    @NonNull
    public List<TransactionWrapper> getTransactions() {
        return transactions;
    }

    public long getGeneration() {
        return calculateGeneration(getSelfParentGen(), getOtherParentGen());
    }

    /**
     * Calculates the generation of an event based on its parents generations
     *
     * @param selfParentGeneration  the generation of the self parent
     * @param otherParentGeneration the generation of the other parent
     * @return the generation of the event
     */
    public static long calculateGeneration(final long selfParentGeneration, final long otherParentGeneration) {
        return 1 + Math.max(selfParentGeneration, otherParentGeneration);
    }

    /**
     * Get the event descriptor for this event, creating one if it hasn't yet been created. If called more than once
     * then return the same instance.
     *
     * @return an event descriptor for this event
     * @throws IllegalStateException if called prior to this event being hashed
     */
    @NonNull
    public EventDescriptorWrapper getDescriptor() {
        if (descriptor == null) {
            if (getHash() == null) {
                throw new IllegalStateException("The hash of the event must be set before creating the descriptor");
            }

            descriptor = new EventDescriptorWrapper(new EventDescriptor(
                    getHash().getBytes(), getEventCore().creatorNodeId(), getBirthRound(), getGeneration()));
        }

        return descriptor;
    }

    /**
     * Get the core event data.
     *
     * @return the core event data
     */
    @NonNull
    public EventCore getEventCore() {
        return eventCore;
    }

    /**
     * Get the transactions of the event.
     *
     * @return list of transactions
     */
    @NonNull
    public List<EventTransaction> getEventTransactions() {
        return eventTransactions;
    }
}
