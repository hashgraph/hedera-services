package com.hedera.services.state.merkle.virtual;

import com.hedera.services.store.models.Id;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class IdKey implements VirtualKey {
    public static final int SERIALIZED_SIZE = Long.BYTES*3;
    private static final long CLASS_ID = 0x2f48ba357c95f343L;
    private static final int CLASS_VERSION = 1;
    
    private Id id;

    public IdKey() {
        // Used by serialization
    }

    public IdKey(Id id) {
        this.id = id;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int i) throws IOException {
        id = new Id(in.readLong(),in.readLong(),in.readLong());
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(id.getShard());
        out.writeLong(id.getRealm());
        out.writeLong(id.getNum());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdKey that = (IdKey) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "IdKey{" + id + "}";
    }

    @Override
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        byteBuffer.putLong(id.getShard());
        byteBuffer.putLong(id.getRealm());
        byteBuffer.putLong(id.getNum());
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {
        id = new Id(byteBuffer.getLong(), byteBuffer.getLong(), byteBuffer.getLong());
    }

    @Override
    public boolean equals(ByteBuffer byteBuffer, int version) throws IOException {
        if (id.getShard() != byteBuffer.getLong()) return false;
        if (id.getRealm() != byteBuffer.getLong()) return false;
        return id.getNum() == byteBuffer.getLong();
    }

}
