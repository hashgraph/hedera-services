/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.merkle.singleton;

import com.hedera.node.app.state.merkle.StateMetadata;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

public class ValueLeaf<T> extends PartialMerkleLeaf implements MerkleLeaf {
    @Deprecated(forRemoval = true)
    private static final long CLASS_ID = 0x65A48B28C563D72EL;

    private final StateMetadata<?, T> md;
    /** The actual value. For example, it could be an Account or SmartContract. */
    private T val;

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public ValueLeaf() {
        md = null;
    }

    /**
     * Used by the deserialization system to create an {@link ValueLeaf} that does not yet have a
     * value. Normally this should not be used.
     *
     * @param md The state metadata
     */
    public ValueLeaf(@NonNull final StateMetadata<?, T> md) {
        this.md = Objects.requireNonNull(md);
    }

    /**
     * Create a new instance with the given value.
     *
     * @param md The state metadata
     * @param value The value.
     */
    public ValueLeaf(@NonNull final StateMetadata<?, T> md, @NonNull final T value) {
        this(md);
        this.val = Objects.requireNonNull(value);
    }

    /** {@inheritDoc} */
    @Override
    public ValueLeaf<T> copy() {
        throwIfImmutable();
        throwIfDestroyed();

        final var cp = new ValueLeaf<>(md, val);
        setImmutable(true);
        return cp;
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        // Null `md` for ConstructableRegistry, TO BE REMOVED ASAP
        return md == null ? CLASS_ID : md.singletonClassId();
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        final var valueSerdes = md.stateDefinition().valueSerdes();
        valueSerdes.write(val, out);
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        final var valueSerdes = md.stateDefinition().valueSerdes();
        this.val = valueSerdes.parse(new DataInputStream(in));
    }

    /**
     * Gets the value.
     *
     * @return The value.
     */
    @Nullable
    public T getValue() {
        return val;
    }

    /**
     * Sets the value. Cannot be called if the leaf is immutable.
     *
     * @param value the new value
     */
    public void setValue(@Nullable final T value) {
        throwIfImmutable();
        this.val = value;
    }
}
