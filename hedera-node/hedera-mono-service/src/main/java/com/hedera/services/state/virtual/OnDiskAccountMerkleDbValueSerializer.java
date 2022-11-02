package com.hedera.services.state.virtual;

import com.hedera.services.state.virtual.entities.OnDiskAccount;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.ValueSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class OnDiskAccountMerkleDbValueSerializer implements ValueSerializer<OnDiskAccount> {

    // Serializer class ID
    static final long CLASS_ID = 0xe5d01987257f5efdL;

    // Serializer version
    static final int CURRENT_VERSION = 1;

    // Value data version
    static final int DATA_VERSION = 1;

    // Serializer info

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    // Data version

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    // Value serialization

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int serialize(final OnDiskAccount value, final SerializableDataOutputStream out) throws IOException {
        Objects.requireNonNull(value);
        Objects.requireNonNull(out);
        return value.serializeTo(out::writeByte, out::writeInt, out::writeLong, out::write);
    }

    // Value deserializatioin

    @Override
    public OnDiskAccount deserialize(final ByteBuffer buffer, final long version) throws IOException {
        Objects.requireNonNull(buffer);
        final OnDiskAccount value = new OnDiskAccount();
        value.deserialize(buffer, (int) version);
        return value;
    }
}
