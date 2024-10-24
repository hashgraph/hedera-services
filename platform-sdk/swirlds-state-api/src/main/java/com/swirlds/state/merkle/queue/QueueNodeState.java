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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.utility.Labeled;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * A merkle leaf node to store a queue state. It's used as a left {@link QueueNode} child.
 */
public final class QueueNodeState extends PartialMerkleLeaf implements Labeled, MerkleLeaf {

    private static final long CLASS_ID = 0x1dd38a57182f4d41L;

    public static final int CLASS_VERSION = 1;

    // Queue state label
    private String label = "";

    // Queue head index. This is the index at which the next element is retrieved from
    // a queue. If equal to tail, the queue is empty
    private long head = 1;

    // Queue tail index. This is the index at which the next element will be added to
    // a queue. Queue size therefore is tail - head
    private long tail = 1;

    /** Zero-arg constructor. */
    public QueueNodeState() {}

    public QueueNodeState(@NonNull final String label) {
        this(label, 1, 1);
    }

    public QueueNodeState(@NonNull final String label, final long head, final long tail) {
        this.label = label;
        this.head = head;
        this.tail = tail;
    }

    /** Copy constructor. */
    private QueueNodeState(@NonNull final QueueNodeState that) {
        this.label = that.label;
        this.head = that.head;
        this.tail = that.tail;
    }

    /** {@inheritDoc} */
    @Override
    public QueueNodeState copy() {
        return new QueueNodeState(this);
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeNormalisedString(label);
        out.writeLong(head);
        out.writeLong(tail);
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        label = in.readNormalisedString(MAX_LABEL_LENGTH);
        head = in.readLong();
        tail = in.readLong();
    }

    /** {@inheritDoc} */
    @Override
    public String getLabel() {
        return label;
    }

    public long getHead() {
        return head;
    }

    public long getHeadAndIncrement() {
        return head++;
    }

    public long getTail() {
        return tail;
    }

    public long getTailAndIncrement() {
        return tail++;
    }

    public boolean isEmpty() {
        return head == tail;
    }
}
