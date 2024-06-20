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
import com.hedera.hapi.platform.event.EventPayload;
import com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType;
import com.hedera.hapi.platform.event.StateSignaturePayload;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.AbstractHashable;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.config.TransactionConfig;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.platform.util.PayloadUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    ///////////////////////////////////////
    // immutable, sent during normal syncs, affects the hash that is signed:
    ///////////////////////////////////////

    /**
     * the software version of the node that created this event.
     */
    private SoftwareVersion softwareVersion;

    /**
     * ID of this event's creator (translate before sending)
     */
    private NodeId creatorId;

    /**
     * the self parent event descriptor
     */
    private EventDescriptor selfParent;

    /**
     * the other parents' event descriptors
     */
    private List<EventDescriptor> otherParents;

    /** a combined list of all parents, selfParent + otherParents */
    private List<EventDescriptor> allParents;

    /**
     * creation time, as claimed by its creator
     */
    private Instant timeCreated;

    /**
     * the payload: an array of transactions
     */
    private ConsensusTransactionImpl[] transactions;

    /**
     * The event descriptor for this event. Is not itself hashed.
     */
    private EventDescriptor descriptor;

    /**
     * Class IDs of permitted transaction types.
     */
    private static final Set<Long> TRANSACTION_TYPES =
            Set.of(StateSignatureTransaction.CLASS_ID, SwirldTransaction.CLASS_ID);

    /**
     * The core event data.
     */
    private final EventCore eventCore;

    /**
     * The payloads of the event.
     */
    private final List<EventPayload> payloads;

    /**
     * Create a UnsignedEvent object
     *
     * @param softwareVersion the software version of the node that created this event.
     * @param creatorId       ID of this event's creator
     * @param selfParent      self parent event descriptor
     * @param otherParents    other parent event descriptors
     * @param birthRound      the round in which this event was created.
     * @param timeCreated     creation time, as claimed by its creator
     * @param transactions    the payload: an array of transactions included in this event instance
     */
    public UnsignedEvent(
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final NodeId creatorId,
            @Nullable final EventDescriptor selfParent,
            @NonNull final List<EventDescriptor> otherParents,
            final long birthRound,
            @NonNull final Instant timeCreated,
            @NonNull final List<OneOf<PayloadOneOfType>> transactions) {
        Objects.requireNonNull(transactions, "The transactions must not be null");
        this.softwareVersion = Objects.requireNonNull(softwareVersion, "The softwareVersion must not be null");
        this.creatorId = Objects.requireNonNull(creatorId, "The creatorId must not be null");
        this.selfParent = selfParent;
        Objects.requireNonNull(otherParents, "The otherParents must not be null");
        otherParents.forEach(Objects::requireNonNull);
        this.otherParents = otherParents;
        this.allParents = createAllParentsList();
        this.timeCreated = Objects.requireNonNull(timeCreated, "The timeCreated must not be null");

        this.payloads = transactions.stream().map(EventPayload::new).toList();
        this.eventCore = new EventCore(
                creatorId.id(),
                birthRound,
                HapiUtils.asTimestamp(timeCreated),
                this.allParents.stream()
                        .map(ed -> new com.hedera.hapi.platform.event.EventDescriptor(
                                ed.getHash().getBytes(), ed.getCreator().id(), ed.getGeneration(), ed.getBirthRound()))
                        .toList(),
                softwareVersion.getPbjSemanticVersion());
        this.transactions = transactions.stream()
                .map(t -> switch (t.kind()) {
                    case STATE_SIGNATURE_PAYLOAD -> new StateSignatureTransaction(t.as());
                    case APPLICATION_PAYLOAD -> new SwirldTransaction((Bytes) t.as());
                    default -> throw new IllegalArgumentException("Unexpected transaction type: " + t.kind());
                })
                .toList()
                .toArray(new ConsensusTransactionImpl[0]);
    }

    /**
     * Serialize the event for the purpose of creating a hash.
     *
     * @param out the stream to which this object is to be written
     *
     * @throws IOException if unsupported payload types are encountered
     */
    public void serializeForHash(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(CLASS_ID);
        serialize(out);
    }

    /**
     * Serialize the event
     *
     * @param out the stream to which this object is to be written
     *
     * @throws IOException if unsupported payload types are encountered
     */
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeInt(ClassVersion.BIRTH_ROUND);
        out.writeSerializable(softwareVersion, true);
        out.writeInt(NodeId.ClassVersion.ORIGINAL);
        out.writeLong(eventCore.creatorNodeId());
        out.writeSerializable(selfParent, false);
        out.writeSerializableList(otherParents, false, true);
        out.writeLong(eventCore.birthRound());
        out.writeInstant(HapiUtils.asInstant(eventCore.timeCreated()));

        // write serialized length of transaction array first, so during the deserialization proces
        // it is possible to skip transaction array and move on to the next object
        out.writeInt(PayloadUtils.getObjectSize(payloads));
        // transactions may include both system transactions and application transactions
        // so writeClassId set to true and allSameClass set to false
        final boolean allSameClass = false;
        out.writeInt(payloads.size());
        if (!payloads.isEmpty()) {
            out.writeBoolean(allSameClass);
        }
        for (final EventPayload payload : payloads) {
            switch (payload.payload().kind()) {
                case APPLICATION_PAYLOAD:
                    out.writeLong(APPLICATION_TRANSACTION_CLASS_ID);
                    out.writeInt(APPLICATION_TRANSACTION_VERSION);
                    final Bytes bytes = payload.payload().as();
                    out.writeInt((int) bytes.length());
                    bytes.writeTo(out);
                    break;
                case STATE_SIGNATURE_PAYLOAD:
                    final StateSignaturePayload stateSignaturePayload =
                            payload.payload().as();

                    out.writeLong(STATE_SIGNATURE_CLASS_ID);
                    out.writeInt(STATE_SIGNATURE_VERSION);
                    out.writeInt((int) stateSignaturePayload.signature().length());
                    stateSignaturePayload.signature().writeTo(out);

                    out.writeInt((int) stateSignaturePayload.hash().length());
                    stateSignaturePayload.hash().writeTo(out);

                    out.writeLong(stateSignaturePayload.round());
                    out.writeInt(Integer.MIN_VALUE); // epochHash is always null
                    break;
                default:
                    throw new IOException(
                            "Unknown payload type: " + payload.payload().kind());
            }
        }
    }

    /**
     * Deserialize the event.
     *
     * @param in the stream from which this object is to be read
     * @return the deserialized event
     * @throws IOException if unsupported payload types are encountered
     */
    public static UnsignedEvent deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        in.readInt(); // read and ignore version
        final TransactionConfig transactionConfig = ConfigurationHolder.getConfigData(TransactionConfig.class);
        Objects.requireNonNull(in, "The input stream must not be null");
        final SoftwareVersion softwareVersion =
                in.readSerializable(StaticSoftwareVersion.getSoftwareVersionClassIdSet());

        final var creatorId = in.readSerializable(false, NodeId::new);
        if (creatorId == null) {
            throw new IOException("creatorId is null");
        }
        final var selfParent = in.readSerializable(false, EventDescriptor::new);
        final var otherParents = in.readSerializableList(AddressBook.MAX_ADDRESSES, false, EventDescriptor::new);
        final var birthRound = in.readLong();

        final var timeCreated = in.readInstant();
        in.readInt(); // read serialized length
        final var transactions = in.readSerializableArray(
                ConsensusTransactionImpl[]::new,
                transactionConfig.maxTransactionCountPerEvent(),
                true,
                TRANSACTION_TYPES);
        final var transactionList = Arrays.stream(transactions)
                .map(ConsensusTransactionImpl::getPayload)
                .toList();

        return new UnsignedEvent(
                softwareVersion, creatorId, selfParent, otherParents, birthRound, timeCreated, transactionList);
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
                && Arrays.equals(transactions, that.transactions)
                && (softwareVersion.compareTo(that.softwareVersion) == 0);
    }

    @Override
    public int hashCode() {
        int result =
                Objects.hash(softwareVersion, creatorId, selfParent, otherParents, eventCore.birthRound(), timeCreated);
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
                .append("birthRound", eventCore.birthRound())
                .append("timeCreated", timeCreated)
                .append("transactions size", transactions == null ? "null" : transactions.length)
                .append("hash", getHash() == null ? "null" : getHash().toHex(TO_STRING_BYTE_ARRAY_LENGTH))
                .toString();
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
        return eventCore.birthRound();
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

    /** @return a list of all parents, self parent (if any), + all other parents */
    @NonNull
    public List<EventDescriptor> getAllParents() {
        return allParents;
    }

    @NonNull
    private List<EventDescriptor> createAllParentsList() {
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
    public ConsensusTransactionImpl[] getTransactions() {
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
    public EventDescriptor getDescriptor() {
        if (descriptor == null) {
            if (getHash() == null) {
                throw new IllegalStateException("The hash of the event must be set before creating the descriptor");
            }

            descriptor = new EventDescriptor(getHash(), getCreatorId(), getGeneration(), eventCore.birthRound());
        }

        return descriptor;
    }

    public EventCore getEventCore() {
        return eventCore;
    }

    public List<EventPayload> getPayloads() {
        return payloads;
    }
}
