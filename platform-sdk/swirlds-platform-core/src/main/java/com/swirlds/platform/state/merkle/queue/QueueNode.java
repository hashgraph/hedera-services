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

package com.swirlds.platform.state.merkle.queue;

import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_QUEUESTATE_LABEL;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_QUEUESTATE_VALUE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_SINGLETONSTATE_LABEL;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_SINGLETONSTATE_VALUE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_QUEUESTATE_LABEL;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_QUEUESTATE_VALUE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_SINGLETONSTATE_LABEL;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_SINGLETONSTATE_VALUE;
import static com.swirlds.platform.state.merkle.logging.StateLogger.logQueueAdd;
import static com.swirlds.platform.state.merkle.logging.StateLogger.logQueueIterate;
import static com.swirlds.platform.state.merkle.logging.StateLogger.logQueuePeek;
import static com.swirlds.platform.state.merkle.logging.StateLogger.logQueueRemove;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.merkle.utility.DebugIterationEndpoint;
import com.swirlds.common.utility.Labeled;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.platform.state.merkle.StateUtils;
import com.swirlds.platform.state.merkle.singleton.StringLeaf;
import com.swirlds.platform.state.merkle.singleton.ValueLeaf;
import com.swirlds.state.spi.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Function;

/**
 * A merkle node with a string (the label) as the left child, and the merkle node value as the right
 * child. We actually support a raw type (any type!) as the value, and we serialize it and put it
 * into a simple merkle node.
 *
 * @param <E> The element type
 */
@DebugIterationEndpoint
public class QueueNode<E> extends PartialBinaryMerkleInternal implements Labeled, MerkleInternal {

    private static final long CLASS_ID = 0x990FF87AD2691DCL;
    public static final int CLASS_VERSION = 1;

    /** Key codec. */
    private Codec<E> codec;

    /** Key class id. */
    private final long queueNodeClassId;

    private final long leafClassId;

    /**
     * @deprecated Only exists for constructable registry as it works today. Remove ASAP!
     */
    @Deprecated(forRemoval = true)
    public QueueNode() {
        setLeft(new StringLeaf());
        setRight(null);
        this.codec = null;
        this.queueNodeClassId = CLASS_ID;
        this.leafClassId = ValueLeaf.CLASS_ID;
    }

    /**
     * Create a new instance.
     *
     *
     */
    public QueueNode(
            @NonNull String serviceName,
            @NonNull String stateKey,
            final long queueNodeClassId,
            final long leafClassId,
            @NonNull final Codec<E> codec) {
        setLeft(new StringLeaf(StateUtils.computeLabel(serviceName, stateKey)));
        setRight(new FCQueue<ValueLeaf<E>>());
        this.codec = requireNonNull(codec);
        this.queueNodeClassId = queueNodeClassId;
        this.leafClassId = leafClassId;
    }

    /** Copy constructor */
    private QueueNode(@NonNull final QueueNode<E> other) {
        this.setLeft(other.getLeft().copy());
        this.setRight(other.getRight().copy());
        this.codec = other.codec;
        this.queueNodeClassId = other.queueNodeClassId;
        this.leafClassId = other.leafClassId;
    }

    public QueueNode(
            @NonNull final ReadableSequentialData in,
            final Path artifactsDir,
            @NonNull final Codec<E> codec)
            throws MerkleSerializationException {
        this.queueNodeClassId = 0; // Not used
        this.leafClassId = ValueLeaf.CLASS_ID; // Not used
        this.codec = codec;
        protoDeserialize(in, artifactsDir);
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
        return CLASS_VERSION;
    }

    @Override
    public String getLabel() {
        final StringLeaf left = getLeft();
        return left.getLabel();
    }

    /** Adds an element to this queue. */
    public void add(E element) {
        getQueue().add(new ValueLeaf<>(leafClassId, codec, element));
        // Log to transaction state log, what was added
        logQueueAdd(getLabel(), element);
    }

    /** Peek an element */
    public E peek() {
        final var valueLeaf = getQueue().peek();
        // Log to transaction state log, what was peeked
        logQueuePeek(getLabel(), valueLeaf);
        return valueLeaf == null ? null : valueLeaf.getValue();
    }

    /** Retrieve and remove an element */
    public E remove() {
        final var valueLeaf = getQueue().remove();
        // Log to transaction state log, what was added
        logQueueRemove(getLabel(), valueLeaf);
        return valueLeaf == null ? null : valueLeaf.getValue();
    }

    /** Iterate over all elements */
    public Iterator<E> iterator() {
        // Log to transaction state log, what was iterated
        logQueueIterate(getLabel(), getRight());
        final var itr = getQueue().stream().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return itr.hasNext();
            }

            @Override
            public E next() {
                final var valueLeaf = itr.next();
                return valueLeaf == null ? null : valueLeaf.getValue();
            }
        };
    }

    /** Utility shorthand to get the queue */
    private FCQueue<ValueLeaf<E>> getQueue() {
        return getRight();
    }

    // Protobuf serialization

    @Override
    protected boolean isChildNodeProtoTag(final int fieldNum) {
        return (fieldNum == NUM_QUEUESTATE_LABEL) ||
                (fieldNum == NUM_QUEUESTATE_VALUE);
    }

    @Override
    protected MerkleNode protoDeserializeNextChild(@NonNull ReadableSequentialData in, Path artifactsDir)
            throws MerkleSerializationException {
        final int childrenSoFar = getNumberOfChildren();
        if (childrenSoFar == 0) {
            // FUTURE WORK: check that the label matches the state definition
            return new StringLeaf(in);
        } else if (childrenSoFar == 1) {
            return new FCQueue<>(in, artifactsDir, t -> new ValueLeaf<>(t, codec));
        } else {
            throw new MerkleSerializationException("Too many queue state child nodes");
        }
    }

    @Override
    protected FieldDefinition getChildProtoField(int childIndex) {
        return switch (childIndex) {
            case 0 -> FIELD_QUEUESTATE_LABEL;
            case 1 -> FIELD_QUEUESTATE_VALUE;
            default -> throw new IllegalArgumentException("Unknown queue state child index: " + childIndex);
        };
    }
}
