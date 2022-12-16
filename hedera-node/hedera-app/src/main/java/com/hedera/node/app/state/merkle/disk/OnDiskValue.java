/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.hedera.node.app.spi.state.Parser;
import com.hedera.node.app.spi.state.Writer;
import com.hedera.node.app.state.merkle.data.ByteBufferDataInput;
import com.hedera.node.app.state.merkle.data.ByteBufferDataOutput;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.DataInputStream;
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
    private static final long CLASS_ID = 0xc1bf8fb7edb292c7L;

    private V value;
    private final Parser<V> valueParser;
    private final Writer<V> valueWriter;
    private boolean immutable = false;

    public OnDiskValue() {
        // This constructor should NEVER be called, only magically by the
        // ConstructableRegistry, because it scans the package looking for
        // everything that is SelfSerializable, throwing an exception if it
        // encounters any without a default constructor. But this class is
        // NEVER USED with ConstructableRegistry, so we just need a dummy
        // implementation.
        this.valueParser = (input) -> null;
        this.valueWriter = (item, output) -> {};
    }

    public OnDiskValue(@NonNull final Parser<V> valueParser, @NonNull final Writer<V> valueWriter) {
        this.valueParser = Objects.requireNonNull(valueParser);
        this.valueWriter = Objects.requireNonNull(valueWriter);
    }

    public OnDiskValue(
            @NonNull final V value,
            @NonNull final Parser<V> valueParser,
            @NonNull final Writer<V> valueWriter) {
        this.value = Objects.requireNonNull(value);
        this.valueParser = Objects.requireNonNull(valueParser);
        this.valueWriter = Objects.requireNonNull(valueWriter);
    }

    /** {@inheritDoc} */
    @Override
    public VirtualValue copy() {
        throwIfImmutable();
        final var copy = new OnDiskValue<>(value, valueParser, valueWriter);
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
            final var copy = new OnDiskValue<>(value, valueParser, valueWriter);
            copy.immutable = true;
            return copy;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(@NonNull final ByteBuffer byteBuffer) throws IOException {
        final var output = new ByteBufferDataOutput(byteBuffer);
        valueWriter.write(value, output);
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream serializableDataOutputStream)
            throws IOException {
        valueWriter.write(value, serializableDataOutputStream);
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(@NonNull final ByteBuffer byteBuffer, int ignored) throws IOException {
        final var input = new ByteBufferDataInput(byteBuffer);
        value = valueParser.parse(input);
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(
            @NonNull final SerializableDataInputStream serializableDataInputStream, int ignored)
            throws IOException {
        value = valueParser.parse(new DataInputStream(serializableDataInputStream));
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return CLASS_ID;
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
