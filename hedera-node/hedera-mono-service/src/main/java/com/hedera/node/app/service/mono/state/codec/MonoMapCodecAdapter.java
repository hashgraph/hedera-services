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

package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * A temporary utility that constructs a {@link Codec} for a {@link SelfSerializable}, {@link VirtualKey},
 * or {@link VirtualValue} type. This is only useful for adapting parts of the {@code mono-service} Merkle
 * tree, since we expect key and value types in {@code hedera-app} to enjoy protobuf serialization.
 *
 * <p>Note that {@code fastEquals()} is not implemented in any case; and only the {@link VirtualKey}
 * codec needs to implement {@code measure()} and {@code typicalSize()}.
 *
 * <p>Also note the {@link SelfSerializable} codec are only usable with a
 * {@code SerializableDataInputStream} and {@code SerializableDataOutputStream}.
 */
public class MonoMapCodecAdapter {
    private MonoMapCodecAdapter() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T extends SelfSerializable> Codec<T> codecForSelfSerializable(
            final int version, final Supplier<T> factory) {
        return new Codec<>() {
            @NonNull
            @Override
            public T parse(final @NonNull ReadableSequentialData input, final boolean strictMode, final int maxDepth)
                    throws ParseException {
                final var buffer = new byte[input.readInt()];
                input.readBytes(buffer);
                final var bais = new ByteArrayInputStream(buffer);
                final var item = factory.get();
                try {
                    item.deserialize(new SerializableDataInputStream(bais), version);
                } catch (final IOException e) {
                    throw new ParseException(e);
                }
                return item;
            }

            @Override
            public void write(final @NonNull T item, final @NonNull WritableSequentialData output) throws IOException {
                final var baos = new ByteArrayOutputStream();
                try (final var out = new SerializableDataOutputStream(baos)) {
                    item.serialize(out);
                    out.flush();
                }
                output.writeInt(baos.toByteArray().length);
                output.writeBytes(baos.toByteArray());
            }

            @Override
            public int measure(final @NonNull ReadableSequentialData input) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int measureRecord(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(@NonNull T item, @NonNull ReadableSequentialData input) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static <T extends VirtualKey> Codec<T> codecForVirtualKey(
            final int version, final Supplier<T> factory, final KeySerializer<T> keySerializer) {
        return new Codec<>() {
            @NonNull
            @Override
            public T parse(final @NonNull ReadableSequentialData input, final boolean strictMode, final int maxDepth)
                    throws ParseException {
                try {
                    if (input instanceof ReadableStreamingData in) {
                        final var item = factory.get();
                        final var buffer = new byte[input.readInt()];
                        input.readBytes(buffer);
                        final var bais = new ByteArrayInputStream(buffer);
                        item.deserialize(new SerializableDataInputStream(bais), version);
                        return item;
                    } else if (input instanceof BufferedData dataBuffer) {
                        // TODO: Is it possible to get direct access to the underlying ByteBuffer here?
                        final var byteBuffer = ByteBuffer.allocate(Math.toIntExact(dataBuffer.capacity()));
                        dataBuffer.readBytes(byteBuffer);
                        // TODO: Remove the following line once this was fixed in BufferedData
                        dataBuffer.skip(dataBuffer.remaining());
                        byteBuffer.rewind();
                        return keySerializer.deserialize(byteBuffer, version);
                    } else {
                        throw new IllegalArgumentException("Unsupported DataInput type: "
                                + input.getClass().getName());
                    }
                } catch (final IOException e) {
                    throw new ParseException(e);
                }
            }

            @Override
            public void write(final @NonNull T item, final @NonNull WritableSequentialData output) throws IOException {
                if (output instanceof WritableStreamingData out) {
                    final var baos = new ByteArrayOutputStream();
                    try (final var outStream = new SerializableDataOutputStream(baos)) {
                        item.serialize(outStream);
                        outStream.flush();
                    }
                    out.writeInt(baos.toByteArray().length);
                    out.writeBytes(baos.toByteArray());
                } else if (output instanceof BufferedData dataBuffer) {
                    // TODO: Is it possible to get direct access to the underlying ByteBuffer here?
                    final var byteBuffer = ByteBuffer.allocate(Math.toIntExact(dataBuffer.capacity()));
                    keySerializer.serialize(item, byteBuffer);
                    byteBuffer.rewind();
                    dataBuffer.writeBytes(byteBuffer);
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataOutput type: " + output.getClass().getName());
                }
            }

            @Override
            public int measure(final @NonNull ReadableSequentialData input) {
                return keySerializer.getSerializedSize();
            }

            @Override
            public int measureRecord(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(@NonNull T item, @NonNull ReadableSequentialData input) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static <T extends VirtualValue> Codec<T> codecForVirtualValue(
            final int version, final Supplier<T> factory, final ValueSerializer<T> valueSerializer) {
        return new Codec<>() {
            @NonNull
            @Override
            public T parse(final @NonNull ReadableSequentialData input, final boolean strictMode, final int maxDepth)
                    throws ParseException {
                try {
                    if (input instanceof ReadableStreamingData in) {
                        final var item = factory.get();
                        final var buffer = new byte[input.readInt()];
                        input.readBytes(buffer);
                        final var bais = new ByteArrayInputStream(buffer);
                        item.deserialize(new SerializableDataInputStream(bais), version);
                        return item;
                    } else if (input instanceof BufferedData dataBuffer) {
                        // TODO: Is it possible to get direct access to the underlying ByteBuffer here?
                        final var byteBuffer = ByteBuffer.allocate(Math.toIntExact(dataBuffer.capacity()));
                        dataBuffer.readBytes(byteBuffer);
                        // TODO: Remove the following line once this was fixed in BufferedData
                        dataBuffer.skip(dataBuffer.remaining());
                        byteBuffer.rewind();
                        return valueSerializer.deserialize(byteBuffer, version);
                    } else {
                        throw new IllegalArgumentException("Unsupported DataInput type: "
                                + input.getClass().getName());
                    }
                } catch (final IOException e) {
                    throw new ParseException(e);
                }
            }

            @Override
            public void write(final @NonNull T item, final @NonNull WritableSequentialData output) throws IOException {
                if (output instanceof WritableStreamingData out) {
                    final var baos = new ByteArrayOutputStream();
                    try (final var outStream = new SerializableDataOutputStream(baos)) {
                        item.serialize(outStream);
                        outStream.flush();
                    }
                    out.writeInt(baos.toByteArray().length);
                    out.writeBytes(baos.toByteArray());
                } else if (output instanceof BufferedData dataBuffer) {
                    // TODO: Is it possible to get direct access to the underlying ByteBuffer here?
                    final var byteBuffer = ByteBuffer.allocate(Math.toIntExact(dataBuffer.capacity()));
                    valueSerializer.serialize(item, byteBuffer);
                    byteBuffer.rewind();
                    dataBuffer.writeBytes(byteBuffer);
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataOutput type: " + output.getClass().getName());
                }
            }

            @Override
            public int measure(final @NonNull ReadableSequentialData input) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int measureRecord(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(final @NonNull T item, final @NonNull ReadableSequentialData input) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
