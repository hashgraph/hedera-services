package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public final class PathKey implements VirtualKey {
    public static final int SERIALIZED_SIZE = Long.BYTES;
    private static final long CLASS_ID = 0xb641f1a2e699afd0L;
    private static final int CLASS_VERSION = 1;

    private long path;

    public PathKey() {
        // Used by serialization
    }

    public PathKey(long path) {
        this.path = path;
    }

    public long getPath() {
        return path;
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
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        byteBuffer.putLong(path);
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int version) throws IOException {
        path = byteBuffer.getLong();
    }

    @Override
    public boolean equals(ByteBuffer byteBuffer, int version) throws IOException {
        return byteBuffer.getLong() == path;
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int version) throws IOException {
        this.path = serializableDataInputStream.readLong();
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
        serializableDataOutputStream.writeLong(path);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathKey pathKey = (PathKey) o;
        return path == pathKey.path;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }
}
