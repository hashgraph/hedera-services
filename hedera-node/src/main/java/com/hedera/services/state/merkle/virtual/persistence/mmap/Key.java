package com.hedera.services.state.merkle.virtual.persistence.mmap;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * An immutable array used as the key for memory mapped data. The size of this
 * array must match the key size as configured on the MemMapStorage at the time
 * it was created.
 */
public final class Key {
    /**
     * The actual data. This will never be null.
     * This data is protected by this class, we make defensive copies rather than letting
     * this array leave this instance at any time.
     */
    private final byte[] bytes;

    /**
     * A cached hash code of the data. Since we use Key as keys in maps,
     * this gets called frequently and we want to only compute it once.
     */
    private final int hashCode;

    /**
     * Creates a new Key with the given array. Makes a safe copy of the array.
     *
     * @param source The source array which cannot be null.
     */
    public Key(byte[] source) {
        this.bytes = Arrays.copyOf(source, source.length);
        this.hashCode = Arrays.hashCode(this.bytes);
    }

    /**
     * Gets the bytes as a read-only ByteBuffer.
     *
     * @return A non-null read-only byte buffer that wraps the byte array.
     */
    public ByteBuffer getBytes() {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    /**
     * Gets a <strong>copy</strong> of the array.
     *
     * @return A 32-byte array. Never returns null.
     */
    public byte[] asByteArray() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Key that = (Key) o;
        return Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}