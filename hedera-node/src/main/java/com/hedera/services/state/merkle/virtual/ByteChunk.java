package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * An immutable 32-byte array.
 *
 * <p>The Ethereum API uses a 32-byte array as both the key and the value for a
 * single data element. By wrapping that array in this class, we can safely
 * encapsulate the logic for protection against null arrays or malformed (short,
 * or long) arrays. We can also cache the hash code to improve performance.</p>
 */
public class ByteChunk implements Hashable {
    /**
     * The actual data. This will never be null and will always be 32 elements in length.
     * This data is protected by this class, we make defensive copies rather than letting
     * this array leave this instance at any time.
     */
    private final byte[] data;

    /**
     * A cached hash code of the data. Since we use ByteChunks as keys in maps,
     * this gets called frequently and we want to only compute it once.
     */
    private final int hashCode;

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
    public ByteChunk(byte[] source) {
        if (source.length != 32) {
            throw new IllegalArgumentException("We only store 32 byte blocks.");
        }
        this.data = Arrays.copyOf(source, 32);
        this.hashCode = Arrays.hashCode(this.data);
        this.hash = CryptoFactory.getInstance().digestSync(data);
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
    public Hash getHash() {
        return hash;
    }

    @Override
    public void invalidateHash() {
        throw new UnsupportedOperationException("Cannot invalidate a ByteChunk's hash");
    }

    @Override
    public void setHash(Hash hash) {
        throw new UnsupportedOperationException("Cannot set a ByteChunk's hash");
    }
}