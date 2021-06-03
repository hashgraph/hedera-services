package com.hedera.services.state.merkle.virtual;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 *
 */
public final class VirtualKey {
    /** Site of an account when serialized to bytes */
    private static final int BYTES = Long.BYTES * 3;
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
    public VirtualKey(byte[] source) {
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

    /**
     * Write the keys bytes to the current position in byte buffer. This saves a copy.
     *
     * @param buffer The buffer to write to
     */
    public void writeToByteBuffer(ByteBuffer buffer) {
        buffer.put(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VirtualKey that = (VirtualKey) o;
        return Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}