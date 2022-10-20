package com.hedera.services.state.virtual;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.ValueSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class UniqueTokenMerkleDbValueSerializer implements ValueSerializer<UniqueTokenValue> {

    // Serializer class ID
    static final long CLASS_ID = 0xc4d512c6695451d5L;

    // Serializer version
    static final int CURRENT_VERSION = 1;

    // Value data version
    static final long DATA_VERSION = 1;

    // Serializer info


    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    // Value info

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    // Value serialization

    @Override
    public int serialize(final UniqueTokenValue value, final SerializableDataOutputStream out) throws IOException {
        Objects.requireNonNull(value);
        Objects.requireNonNull(out);
        value.serialize(out);
        return value.getSerializedSize();
    }

    // Value deserialization

    @Override
    public UniqueTokenValue deserialize(final ByteBuffer buffer, final long version) throws IOException {
        Objects.requireNonNull(buffer);
        final UniqueTokenValue value = new UniqueTokenValue();
        value.deserialize(buffer, (int) version);
        return value;
    }
}
