package com.swirlds.platform.system.events;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import java.time.Instant;
import java.util.List;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventDescriptor;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.util.HapiUtils;
import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.AbstractHashable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.event.EventSerializationUtils;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class EventDataWrappers {
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
     * The event descriptor for this event. Is not itself hashed.
     */
    private EventDescriptorWrapper descriptor;


    /**
     * Create a EventDataWrappers object
     *
     * @param softwareVersion the software version of the node that created this event.
     * @param creatorId       ID of this event's creator
     * @param selfParent      self parent event descriptor
     * @param otherParents    other parent event descriptors
     * @param timeCreated     creation time, as claimed by its creator
     * @param transactions    list of transactions included in this event instance
     */
    public EventDataWrappers(
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final NodeId creatorId,
            @Nullable final EventDescriptorWrapper selfParent,
            @NonNull final List<EventDescriptorWrapper> otherParents,
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

            this.transactions = Objects.requireNonNull(transactions, "transactions must not be null").stream().map(TransactionWrapper::new).toList();
    }

    @NonNull
    private List<EventDescriptorWrapper> createAllParentsList() {
        return selfParent == null
                ? otherParents
                : Stream.concat(Stream.of(selfParent), otherParents.stream()).toList();
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
     * Get the event descriptor for the self parent.
     *
     * @return the event descriptor for the self parent
     */
    @Nullable
    public EventDescriptorWrapper getSelfParent() {
        return selfParent;
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
     * Get the event descriptors for the other parents.
     *
     * @return the event descriptors for the other parents
     */
    @NonNull
    public List<EventDescriptorWrapper> getOtherParents() {
        return otherParents;
    }

    /**
     * @return the hash of the other parent
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
     * Check if the event has other parents.
     *
     * @return true if the event has other parents
     */
    public boolean hasOtherParent() {
        return otherParents != null && !otherParents.isEmpty();
    }

    /** @return a list of all parents, self parent (if any), + all other parents */
    @NonNull
    public List<EventDescriptorWrapper> getAllParents() {
        return allParents;
    }

    @NonNull
    public Instant getTimeCreated() {
        return timeCreated;
    }

    /**
     * @return list of transactions wrappers
     */
    @NonNull
    public List<TransactionWrapper> getTransactions() {
        return transactions;
    }
}
