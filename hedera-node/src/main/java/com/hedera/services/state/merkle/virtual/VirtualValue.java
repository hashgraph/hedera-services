package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 *
 */
public final class VirtualValue implements Hashable {
    /**
     * The actual data. This will never be null and will always be 32 elements in length.
     * This data is protected by this class, we make defensive copies rather than letting
     * this array leave this instance at any time.
     */
    private final byte[] data;

    /**
     * A cached Swirlds Hash of the data contents. Since we use ByteChunks as
     * values, we need this hash to compute the hash of the merkle tree.
     * If we separated out Keys and Values as two different types, then
     * we'd only have this for values, not for keys.
     */
    private final Hash hash;

    /**
     * Creates a new ByteChunk with the given array. Makes a safe copy of the array.
     *
     * @param source The source array which cannot be null and must be 32 bytes long.
     */
    public VirtualValue(byte[] source) {
        if (source.length != 32) {
            throw new IllegalArgumentException("We only store 32 byte blocks.");
        }
        this.data = Arrays.copyOf(source, 32);
        this.hash = new Hash(Arrays.copyOf(data, 48));
    }

    /**
     * Gets the data as a read-only ByteBuffer.
     *
     * @return A non-null read-only byte buffer that wraps the byte array.
     */
    public ByteBuffer getData() {
        return ByteBuffer.wrap(data).asReadOnlyBuffer();
    }

    /**
     * Gets a <strong>copy</strong> of the array.
     *
     * @return A 32-byte array. Never returns null.
     */
    public byte[] asByteArray() {
        return Arrays.copyOf(data, 32);
    }


    /**
     * Write the data bytes to the current position in byte buffer. This saves a copy.
     *
     * @param buffer The buffer to write to
     */
    public void writeToByteBuffer(ByteBuffer buffer) {
        buffer.put(data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VirtualValue that = (VirtualValue) o;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    @Override
    public Hash getHash() {
        return hash;
    }

    @Override
    public void invalidateHash() {
        throw new UnsupportedOperationException("Cannot invalidate an VirtualValue's hash");
    }

    @Override
    public void setHash(Hash hash) {
        throw new UnsupportedOperationException("Cannot set an VirtualValue's hash");
    }
}