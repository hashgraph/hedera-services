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

package com.swirlds.state.merkle.singleton;

import static com.swirlds.state.merkle.StateUtils.readFromStream;
import static com.swirlds.state.merkle.StateUtils.writeToStream;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.Codec;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;

/**
 * A Merkle leaf that stores an arbitrary value with delegated serialization based on the {@link
 * #classId}.
 */
public class ValueLeaf<T> extends PartialMerkleLeaf implements MerkleLeaf {
    /**
     * {@deprecated} Needed for ConstructableRegistry, TO BE REMOVED ASAP
     */
    @Deprecated(forRemoval = true)
    public static final long CLASS_ID = 0x65A48B28C563D72EL;

    private final long classId;
    private final Codec<T> codec;
    /** The actual value. For example, it could be an Account or SmartContract. */
    private T val;

    /**
     * {@deprecated} Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
     */
    @Deprecated(forRemoval = true)
    public ValueLeaf() {
        codec = null;
        classId = CLASS_ID;
    }

    /**
     * Used by the deserialization system to create an {@link ValueLeaf} that does not yet have a
     * value. Normally this should not be used.
     *
     * @param singletonClassId The class ID of the object
     * @param codec   The codec to use for serialization
     */
    public ValueLeaf(final long singletonClassId, @NonNull Codec<T> codec) {
        this.codec = requireNonNull(codec);
        this.classId = singletonClassId;
    }

    /**
     * Create a new instance with the given value.
     *
     * @param singletonClassId The class ID of the object
     * @param codec   The codec to use for serialization
     * @param value The value.
     */
    public ValueLeaf(final long singletonClassId, @NonNull Codec<T> codec, @Nullable final T value) {
        this(singletonClassId, codec);
        this.val = value;
    }

    /** {@inheritDoc} */
    @Override
    public ValueLeaf<T> copy() {
        throwIfImmutable();
        throwIfDestroyed();

        final var cp = new ValueLeaf<>(classId, codec, val);
        setImmutable(true);
        return cp;
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return classId;
    }

    /** {@inheritDoc} */
    @Override
    public int getClassVersion() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        if (codec == null) {
            throw new IllegalStateException("Metadata is null, meaning this is not a proper object");
        }

        writeToStream(out, codec, val);
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        if (codec == null) {
            throw new IllegalStateException("Metadata is null, meaning this is not a proper object");
        }

        this.val = readFromStream(in, codec);
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
