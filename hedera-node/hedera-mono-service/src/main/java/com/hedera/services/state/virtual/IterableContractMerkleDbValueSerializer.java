package com.hedera.services.state.virtual;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.ValueSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class IterableContractMerkleDbValueSerializer implements ValueSerializer<IterableContractValue> {

    static final long CLASS_ID = 0x2137d0dcac9ab2b3L;

    static final int CURRENT_VERSION = 1;

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
    public int serialize(final IterableContractValue value, final SerializableDataOutputStream out) throws IOException {
        Objects.requireNonNull(value);
        Objects.requireNonNull(out);
        value.serialize(out);
        return value.getSerializedSize();
    }

    // Value deserialization

    @Override
    public IterableContractValue deserialize(final ByteBuffer buffer, final long version) throws IOException {
        Objects.requireNonNull(buffer);
        final IterableContractValue value = new IterableContractValue();
        value.deserialize(buffer, (int) version);
        return value;
    }
}
