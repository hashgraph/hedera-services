/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.merkle.queue;

import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.state.merkle.logging.StateLogger.logQueueAdd;
import static com.swirlds.state.merkle.logging.StateLogger.logQueueIterate;
import static com.swirlds.state.merkle.logging.StateLogger.logQueuePeek;
import static com.swirlds.state.merkle.logging.StateLogger.logQueueRemove;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.Codec;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.merkle.utility.DebugIterationEndpoint;
import com.swirlds.common.utility.Labeled;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.state.merkle.StateUtils;
import com.swirlds.state.merkle.singleton.StringLeaf;
import com.swirlds.state.merkle.singleton.ValueLeaf;
import com.swirlds.state.merkle.vmapsupport.OnDiskValue;
import com.swirlds.state.merkle.vmapsupport.OnDiskValueSerializer;
import com.swirlds.state.merkle.vmapsupport.SingleLongKey;
import com.swirlds.state.merkle.vmapsupport.SingleLongKeySerializer;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.serialize.KeySerializer;
import com.swirlds.virtualmap.serialize.ValueSerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A merkle node used as a backing store for queue states. The node has two children. The left
 * one is a {@link QueueNodeState}, it contains the label and queue head/tail indices. The
 * right one is a {@link VirtualMap}, it is used to store queue items.
 *
 * @param <E> The element type
 */
@ConstructableIgnored
@DebugIterationEndpoint
public class QueueNode<E> extends PartialBinaryMerkleInternal implements Labeled, MerkleInternal {

    // A "max keys" hint to the underlying virtual map. The largest queue so far has
    // slightly less than 2M elements
    private static final long QUEUE_MAX_KEYS_HINT = 2_000_000;

    // Class version with initial implementation using FCQueue
    public static final int CLASS_VERSION_ORIGINAL = 1;

    // Class version where data is migrated from FCQueue to VirtualMap
    public static final int CLASS_VERSION_VMAP = 2;

    private static final Logger logger = LogManager.getLogger(QueueNode.class);

    /** Codec for queue elements */
    private final Codec<E> codec;

    /** QueueNode class ID */
    private final long queueNodeClassId;

    /** Element serializer class ID */
    private final long elementSerializerClassId;

    /** Queue elements class ID, i.e. E.getClassId(), if it existed. */
    private final long elementClassId;

    /**
     * Create a new QueueNode instance.
     *
     * @param serviceName service name
     * @param stateKey queue state name
     * @param queueNodeClassId queue node class ID
     * @param elementSerializerClassId element serializer class ID
     * @param elementClassId element class ID
     * @param codec element codec
     */
    public QueueNode(
            @NonNull String serviceName,
            @NonNull String stateKey,
            final long queueNodeClassId,
            final long elementSerializerClassId,
            final long elementClassId,
            @NonNull final Codec<E> codec) {
        this(serviceName, stateKey, queueNodeClassId, elementSerializerClassId, elementClassId, codec, true);
    }

    /**
     * Create a new QueueNode instance. {@code init} param indicates whether this queue node should create
     * and initialize its child nodes, the state and the store. This param is set to {@code true}, when a
     * new queue node is created to insert to the merkle tree, or a new queue node is created to deserialize
     * itself from a saved state later. The param is set to {@code false}, when this class is registered in
     * a constructable registry. In this case, the data store (a virtual map) should not be created.
     *
     * @param serviceName service name
     * @param stateKey queue state name
     * @param queueNodeClassId queue node class ID
     * @param elementSerializerClassId element serializer class ID
     * @param elementClassId element class ID
     * @param codec element codec
     */
    public QueueNode(
            @NonNull String serviceName,
            @NonNull String stateKey,
            final long queueNodeClassId,
            final long elementSerializerClassId,
            final long elementClassId,
            @NonNull final Codec<E> codec,
            final boolean init) {
        this.codec = requireNonNull(codec);
        this.queueNodeClassId = queueNodeClassId;
        this.elementSerializerClassId = elementSerializerClassId;
        this.elementClassId = elementClassId;

        if (init) {
            final String label = StateUtils.computeLabel(serviceName, stateKey);
            setLeft(new QueueNodeState(label));
            setRight(createDataStore(label));
        }
    }

    /** Copy constructor */
    private QueueNode(@NonNull final QueueNode<E> other) {
        this.setLeft(other.getLeft().copy());
        this.setRight(other.getRight().copy());
        this.codec = other.codec;
        this.queueNodeClassId = other.queueNodeClassId;
        this.elementSerializerClassId = other.elementSerializerClassId;
        this.elementClassId = other.elementClassId;
    }

    @Override
    public QueueNode<E> copy() {
        return new QueueNode<>(this);
    }

    @Override
    public long getClassId() {
        return queueNodeClassId;
    }

    @Override
    public int getVersion() {
        return CLASS_VERSION_VMAP;
    }

    @Override
    public String getLabel() {
        final QueueNodeState left = getLeft();
        return left.getLabel();
    }

    @Override
    public MerkleNode migrate(int version) {
        if (version < CLASS_VERSION_VMAP) {
            final long start = System.currentTimeMillis();
            logger.info(STARTUP.getMarker(), "Migrating QueueNode from FCQueue to VirtualMap");

            final StringLeaf originalLabeled = getLeft();
            final String label = originalLabeled.getLabel();
            final FCQueue<ValueLeaf<E>> originalStore = getRight();
            final VirtualMap<SingleLongKey, OnDiskValue<E>> store = createDataStore(label);

            // Migrate data
            final long head = 1;
            long tail = 1;
            for (ValueLeaf<E> leaf : originalStore) {
                final long classId = leaf.getClassId();
                final Codec<E> codec = leaf.getCodec();
                final E value = Objects.requireNonNull(leaf.getValue(), "Null value is not expected here");
                store.put(new SingleLongKey(tail++), new OnDiskValue<>(classId, codec, value));
            }

            // Replace child nodes
            setLeft(new QueueNodeState(label, head, tail));
            setRight(store);

            final long end = System.currentTimeMillis();
            logger.info(STARTUP.getMarker(), "QueueNode migration is complete in {} ms", start - end);
        }
        return this;
    }

    /** Adds an element to this queue. */
    public void add(@NonNull E element) {
        final QueueNodeState state = getState();
        getStore()
                .put(new SingleLongKey(state.getTailAndIncrement()), new OnDiskValue<>(elementClassId, codec, element));
        // Log to transaction state log, what was added
        logQueueAdd(getLabel(), element);
    }

    /** Peek an element */
    public E peek() {
        final QueueNodeState state = getState();
        final OnDiskValue<E> value =
                state.isEmpty() ? null : getFromStore(getState().getHead());
        // Log to transaction state log, what was peeked
        logQueuePeek(getLabel(), value);
        return value == null ? null : value.getValue();
    }

    /** Retrieve and remove an element */
    public E remove() {
        final QueueNodeState state = getState();
        final OnDiskValue<E> value =
                state.isEmpty() ? null : getFromStore(getState().getHeadAndIncrement());
        // Log to transaction state log, what was added
        logQueueRemove(getLabel(), value);
        return value == null ? null : value.getValue();
    }

    @NonNull
    private OnDiskValue<E> getFromStore(final long index) {
        final OnDiskValue<E> value = getStore().get(new SingleLongKey(index));
        if (value == null) {
            throw new IllegalStateException("Can't find queue element at index " + index + " in the store");
        }
        return value;
    }

    /** Iterate over all elements */
    public Iterator<E> iterator() {
        final QueueNodeState state = getState();
        final QueueIterator it = new QueueIterator(state.getHead(), state.getTail());
        // Log to transaction state log, what was iterated
        logQueueIterate(getLabel(), state.getTail() - state.getHead(), it);
        it.reset();
        return it;
    }

    /** Utility shorthand to get the state child node */
    @NonNull
    private QueueNodeState getState() {
        return getLeft();
    }

    /** Utility shorthand to get the store child node */
    @NonNull
    private VirtualMap<SingleLongKey, OnDiskValue<E>> getStore() {
        return getRight();
    }

    /**
     * Creates a new virtual map to be used as a data store for this node. For now, map configuration
     * is hard-coded to use a MerkleDb data source with 2M keys hint.
     */
    private VirtualMap<SingleLongKey, OnDiskValue<E>> createDataStore(final String label) {
        final KeySerializer<SingleLongKey> keySerializer = new SingleLongKeySerializer();
        final ValueSerializer<OnDiskValue<E>> valueSerializer =
                new OnDiskValueSerializer<>(elementSerializerClassId, elementClassId, codec);
        final MerkleDbTableConfig merkleDbTableConfig = new MerkleDbTableConfig((short) 1, DigestType.SHA_384);
        merkleDbTableConfig.maxNumberOfKeys(QUEUE_MAX_KEYS_HINT);
        final VirtualDataSourceBuilder dsBuilder = new MerkleDbDataSourceBuilder(merkleDbTableConfig);
        return new VirtualMap<>(label, keySerializer, valueSerializer, dsBuilder);
    }

    /**
     * A tiny utility class to iterate over the queue node.
     */
    private class QueueIterator implements Iterator<E> {

        // Queue position to start from, inclusive
        private final long start;

        // Queue position to iterate up to, exclusive
        private final long limit;

        // The current iterator position, start <= current < limit
        private long current;

        // Start (inc), limit (exc)
        public QueueIterator(final long start, final long limit) {
            this.start = start;
            this.limit = limit;
            reset();
        }

        @Override
        public boolean hasNext() {
            return current < limit;
        }

        @Override
        public E next() {
            if (current == limit) {
                throw new NoSuchElementException();
            }
            try {
                return getFromStore(current++).getValue();
            } catch (final IllegalStateException e) {
                throw new ConcurrentModificationException(e);
            }
        }

        void reset() {
            current = start;
        }
    }
}
