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

package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.DataBuffer;
import com.hedera.pbj.runtime.io.DataInput;
import com.hedera.pbj.runtime.io.DataOutput;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
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
            public T parse(final @NonNull DataInput input) throws IOException {
                final var item = factory.get();
                if (input instanceof SerializableDataInputStream in) {
                    item.deserialize(in, version);
                } else {
                    throw new IllegalArgumentException("Expected a SerializableDataInputStream");
                }
                return item;
            }

            @NonNull
            @Override
            public T parseStrict(@NonNull DataInput dataInput) throws IOException {
                return parse(dataInput);
            }

            @Override
            public void write(final @NonNull T item, final @NonNull DataOutput output) throws IOException {
                if (output instanceof SerializableDataOutputStream out) {
                    item.serialize(out);
                } else {
                    throw new IllegalArgumentException("Expected a SerializableDataOutputStream");
                }
            }

            @Override
            public int measure(final @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int measureRecord(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(@NonNull T item, @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static <T extends VirtualKey<?>> Codec<T> codecForVirtualKey(
            final int version, final Supplier<T> factory, final KeySerializer<T> keySerializer) {
        return new Codec<>() {
            @NonNull
            @Override
            public T parse(final @NonNull DataInput input) throws IOException {
                final var item = factory.get();
                // TODO - this will never be true, we need the filtered stream from (DataInputStream) in
                if (input instanceof SerializableDataInputStream in) {
                    item.deserialize(in, version);
                } else if (input instanceof DataBuffer db) {
                    // TODO - need to have access to the wrapped ByteBuffer here
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataInput type: " + input.getClass().getName());
                }
                return item;
            }

            @NonNull
            @Override
            public T parseStrict(@NonNull DataInput dataInput) throws IOException {
                return parse(dataInput);
            }

            @Override
            public void write(final @NonNull T item, final @NonNull DataOutput output) throws IOException {
                // TODO - this will never be true, we need the filtered stream from (DataOutputStream) out
                if (output instanceof SerializableDataOutputStream out) {
                    item.serialize(out);
                } else if (output instanceof DataBuffer db) {
                    // TODO - need to have access to the wrapped ByteBuffer here
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataOutput type: " + output.getClass().getName());
                }
            }

            @Override
            public int measure(final @NonNull DataInput input) {
                return keySerializer.getSerializedSize();
            }

            @Override
            public int measureRecord(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(@NonNull T item, @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static <T extends VirtualValue> Codec<T> codecForVirtualValue(final int version, final Supplier<T> factory) {
        return new Codec<>() {
            @NonNull
            @Override
            public T parse(final @NonNull DataInput input) throws IOException {
                final var item = factory.get();
                // TODO - this will never be true, we need the filtered stream from (DataInputStream) in
                if (input instanceof SerializableDataInputStream in) {
                    item.deserialize(in, version);
                } else if (input instanceof DataBuffer db) {
                    // TODO - need to have access to the wrapped ByteBuffer here
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataInput type: " + input.getClass().getName());
                }
                return item;
            }

            @NonNull
            @Override
            public T parseStrict(@NonNull DataInput dataInput) throws IOException {
                return parse(dataInput);
            }

            @Override
            public void write(final @NonNull T item, final @NonNull DataOutput output) throws IOException {
                // TODO - this will never be true, we need the filtered stream from (DataOutputStream) out
                if (output instanceof SerializableDataOutputStream out) {
                    item.serialize(out);
                } else if (output instanceof DataBuffer db) {
                    // TODO - need to have access to the wrapped ByteBuffer here
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataOutput type: " + output.getClass().getName());
                }
            }

            @Override
            public int measure(final @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int measureRecord(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(final @NonNull T item, final @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
