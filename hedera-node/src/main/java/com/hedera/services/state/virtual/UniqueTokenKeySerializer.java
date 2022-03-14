package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class UniqueTokenKeySerializer implements KeySerializer<UniqueTokenKey> {
    static final long CLASS_ID = 0xb3c94b6cf62aa6c4L;
    private UniqueTokenKey scratch = new UniqueTokenKey();

    @Override
    public boolean isVariableSize() {
        return true;
    }

    @Override
    public int getTypicalSerializedSize() {
        return UniqueTokenKey.ESTIMATED_SIZE_BYTES;
    }

    @Override
    public int getSerializedSize() {
        return DataFileCommon.VARIABLE_DATA_SIZE;
    }

    @Override
    public long getCurrentDataVersion() {
        return UniqueTokenKey.CURRENT_VERSION;
    }

    @Override
    public UniqueTokenKey deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        Objects.requireNonNull(buffer);
        UniqueTokenKey tokenKey = new UniqueTokenKey();
        tokenKey.deserialize(buffer, (int) dataVersion);
        return tokenKey;
    }

    @Override
    public int serialize(UniqueTokenKey tokenKey, SerializableDataOutputStream outputStream) throws IOException {
        Objects.requireNonNull(tokenKey);
        Objects.requireNonNull(outputStream);
        return tokenKey.serializeTo(outputStream::write);
    }

    @Override
    public int deserializeKeySize(ByteBuffer byteBuffer) {
        return byteBuffer.get() + 1;
    }

    @Override
    public boolean equals(ByteBuffer byteBuffer, int dataVersion, UniqueTokenKey uniqueTokenKey) throws IOException {
        scratch.deserialize(byteBuffer, dataVersion);
        return scratch.equals(uniqueTokenKey);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
        /* no state to load, so no-op */
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
        /* no state to save, so no-op */
    }

    @Override
    public int getVersion() {
        return UniqueTokenKey.CURRENT_VERSION;
    }
}
