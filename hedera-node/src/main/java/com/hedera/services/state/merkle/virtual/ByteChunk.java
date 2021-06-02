package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * An immutable 256-byte array.
 *
 * <p>The Ethereum API uses a 256-byte array as both the key and the value for a
 * single data element. By wrapping that array in this class, we can safely
 * encapsulate the logic for protection against null arrays or malformed (short,
 * or long) arrays. We can also cache the hash code to improve performance.</p>
 */
@ConstructableIgnored
public class ByteChunk implements SelfSerializable {
    private final byte[] data;
    private final int hashCode;

    /**
     * Creates a new ByteChunk with the given array. Makes a safe copy of the array.
     *
     * @param source The source array which cannot be null and must be 256 bytes long.
     */
    public ByteChunk(byte[] source) {
        if (source.length != 256) {
            throw new IllegalArgumentException("We only store 256 byte blocks.");
        }
        this.data = Arrays.copyOf(source, 256);
        this.hashCode = Arrays.hashCode(this.data);
    }

    /**
     * Gets the data as a read-only ByteBuffer.
     *
     * @return A non-null read-only byte buffer that wraps the byte array.
     */
    public ByteBuffer getData() {
        return ByteBuffer.wrap(data).asReadOnlyBuffer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ByteChunk that = (ByteChunk) o;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
        throw new UnsupportedEncodingException();
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
        serializableDataOutputStream.writeByteArray(data);
    }

    @Override
    public long getClassId() {
        // TODO handle these properly
        return 102302032030230L;
    }

    @Override
    public int getVersion() {
        return 1;
    }
}