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

package com.hedera.node.app.state.merkle.disk;

import static com.hedera.node.app.state.merkle.StateUtils.readFromStream;
import static com.hedera.node.app.state.merkle.StateUtils.writeToStream;

import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A {@link VirtualValue} used for storing the actual value. In our system, a state might have
 * business objects as values, such as {@code Account} or {@code Token}. However, the {@link
 * com.swirlds.virtualmap.VirtualMap} requires each value in the map to be of the type {@link
 * VirtualValue}. Rather than exposing each service to the merkle APIs, we allow them to work in
 * terms of business objects, and this one implementation of {@link VirtualValue} is used for all
 * types of values.
 *
 * @param <V> The type of the value (business object) held in this merkel data structure
 */
public class OnDiskValue<V> implements VirtualValue {
    @Deprecated(forRemoval = true)
    private static final long CLASS_ID = 0x8837746626372L;

    private final Codec<V> codec;
    private final StateMetadata<?, V> md;
    private V value;
    private boolean immutable = false;

    // Default constructor is for deserialization
    public OnDiskValue() {
        this.codec = null;
        this.md = null;
    }

    public OnDiskValue(@NonNull final StateMetadata<?, V> md) {
        this.md = md;
        this.codec = md.stateDefinition().valueCodec();
    }

    public OnDiskValue(@NonNull final StateMetadata<?, V> md, @NonNull final V value) {
        this(md);
        this.value = Objects.requireNonNull(value);
    }

    /** {@inheritDoc} */
    @Override
    public VirtualValue copy() {
        final var copy = new OnDiskValue<>(md, value);
        this.immutable = true;
        return copy;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isImmutable() {
        return immutable;
    }

    /** {@inheritDoc} */
    @Override
    public VirtualValue asReadOnly() {
        if (isImmutable()) {
            return this;
        } else {
            final var copy = new OnDiskValue<>(md, value);
            copy.immutable = true;
            return copy;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(@NonNull final ByteBuffer byteBuffer) throws IOException {
        serializeReturningWrittenBytes(byteBuffer);
    }

    public int serializeReturningWrittenBytes(@NonNull ByteBuffer byteBuffer) throws IOException {
        final var output = BufferedData.wrap(byteBuffer);
        final var initPos = output.position();
        output.skip(Integer.BYTES);
        codec.write(value, output);
        final var pos = output.position();
        output.position(initPos);
        output.writeInt((int) (pos - initPos - Integer.BYTES));
        output.position(pos);
        return (int) (pos - initPos);
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        writeToStream(out, codec, value);
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(@NonNull final ByteBuffer byteBuffer, int ignored) throws IOException {
        final var buff = BufferedData.wrap(byteBuffer);
        final var len = buff.readInt();
        final var pos = buff.position();
        final var oldLimit = buff.limit();
        buff.limit(pos + len);
        value = codec.parse(buff);
        buff.limit(oldLimit);
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, int ignored) throws IOException {
        value = readFromStream(in, codec);
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        // SHOULD NOT ALLOW md TO BE NULL, but ConstructableRegistry has foiled me.
        return md == null ? CLASS_ID : md.onDiskValueClassId();
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return 1;
    }

    /**
     * Gets the value.
     *
     * @return The value (business object)
     */
    @Nullable
    public V getValue() {
        return value;
    }

    /**
     * Sets the value
     *
     * @param value the business object value
     */
    public void setValue(@Nullable final V value) {
        throwIfImmutable();
        this.value = Objects.requireNonNull(value);
    }
}
