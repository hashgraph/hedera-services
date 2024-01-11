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

package com.hedera.node.app.state.merkle.queue;

import static com.hedera.node.app.state.logging.TransactionStateLogger.*;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.merkle.StateUtils;
import com.hedera.node.app.state.merkle.singleton.StringLeaf;
import com.hedera.node.app.state.merkle.singleton.ValueLeaf;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.utility.Labeled;
import com.swirlds.fcqueue.FCQueue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;

/**
 * A merkle node with a string (the label) as the left child, and the merkle node value as the right
 * child. We actually support a raw type (any type!) as the value, and we serialize it and put it
 * into a simple merkle node.
 *
 * @param <E> The element type
 */
public class QueueNode<E> extends PartialBinaryMerkleInternal implements Labeled, MerkleInternal {
    private static final long CLASS_ID = 0x990FF87AD2691DCL;
    public static final int CLASS_VERSION = 1;

    /** The state metadata, needed for adding new elements */
    private final StateMetadata<?, E> md;

    /**
     * @deprecated Only exists for constructable registry as it works today. Remove ASAP!
     */
    @Deprecated(forRemoval = true)
    public QueueNode() {
        setLeft(new StringLeaf());
        setRight(null);
        this.md = null;
    }

    /**
     * Create a new instance.
     *
     * @param md The metadata
     */
    public QueueNode(@NonNull final StateMetadata<?, E> md) {
        setLeft(new StringLeaf(
                StateUtils.computeLabel(md.serviceName(), md.stateDefinition().stateKey())));
        setRight(new FCQueue<ValueLeaf<E>>());
        this.md = requireNonNull(md);
    }

    /** Copy constructor */
    private QueueNode(@NonNull final QueueNode<E> other) {
        this.setLeft(other.getLeft().copy());
        this.setRight(other.getRight().copy());
        this.md = other.md;
    }

    @Override
    public QueueNode<E> copy() {
        return new QueueNode<>(this);
    }

    @Override
    public long getClassId() {
        return md == null ? CLASS_ID : md.queueNodeClassId();
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
        getQueue().add(new ValueLeaf<>(md, element));
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
}
