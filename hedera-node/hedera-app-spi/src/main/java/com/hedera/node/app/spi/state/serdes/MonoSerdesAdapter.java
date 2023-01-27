package com.hedera.node.app.spi.state.serdes;

import com.hedera.node.app.spi.state.Serdes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Supplier;

public class MonoSerdesAdapter {
    private MonoSerdesAdapter() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T extends VirtualKey<T>> Serdes<T> serdesForVirtualKey(
            final int version,
            final Supplier<T> factory,
            final KeySerializer<T> keySerializer
    ) {
        return new Serdes<>() {
            @NonNull
            @Override
            public T parse(final @NonNull DataInput input) throws IOException {
                final var item = factory.get();
                if (input instanceof SerializableDataInputStream in) {
                    item.deserialize(in, version);
                } else if (input instanceof ByteBufferDataInput bb) {
                    item.deserialize(bb.getBuffer(), version);
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataInput type: " + input.getClass().getName());
                }
                return item;
            }

            @Override
            public void write(final @NonNull T item, final @NonNull DataOutput output) throws IOException {
                if (output instanceof SerializableDataOutputStream out) {
                    item.serialize(out);
                } else if (output instanceof ByteBufferDataOutput bb) {
                    item.serialize(bb.getBuffer());
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
            public int typicalSize() {
                return keySerializer.getSerializedSize();
            }

            @Override
            public boolean fastEquals(@NonNull T item, @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static <T extends VirtualValue> Serdes<T> serdesForVirtualValue(
            final int version, final Supplier<T> factory) {
        return new Serdes<>() {
            @NonNull
            @Override
            public T parse(final @NonNull DataInput input) throws IOException {
                final var item = factory.get();
                if (input instanceof SerializableDataInputStream in) {
                    item.deserialize(in, version);
                } else if (input instanceof ByteBufferDataInput bb) {
                    item.deserialize(bb.getBuffer(), version);
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataInput type: " + input.getClass().getName());
                }
                return item;
            }

            @Override
            public void write(final @NonNull T item, final @NonNull DataOutput output) throws IOException {
                if (output instanceof SerializableDataOutputStream out) {
                    item.serialize(out);
                } else if (output instanceof ByteBufferDataOutput bb) {
                    item.serialize(bb.getBuffer());
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
            public int typicalSize() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(final @NonNull T item, final @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
