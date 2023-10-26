/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.network;

import static org.mockito.Mockito.mock;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

/**
 * A descriptor for a {@link CountingChatterEvent}
 */
public class CountingEventDescriptor extends EventDescriptor {
    private static final long CLASS_ID = 0x281cc80fd18964f0L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /** The creator of the event */
    private NodeId creator;
    /** The unique order number of this event */
    private long order;

    /** Default constructor for constructable registry */
    public CountingEventDescriptor() {}

    public CountingEventDescriptor(@NonNull final NodeId creator, final long order) {
        this.creator = Objects.requireNonNull(creator, "creator must not be null");
        this.order = order;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {}

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {}

    @Override
    public @NonNull Hash getHash() {
        return mock(Hash.class);
    }

    @Override
    public @NonNull NodeId getCreator() {
        return creator;
    }

    @Override
    public long getGeneration() {
        return order;
    }

    @Override
    public String toString() {
        return "Desc(" + order + ", c:" + creator + ")";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(creator, order);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final CountingEventDescriptor that = (CountingEventDescriptor) o;

        if (this.hashCode() != that.hashCode()) {
            return false;
        }

        return creator == that.creator && order == that.order;
    }
}
