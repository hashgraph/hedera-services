/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.crypto;

import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.common.utility.Mnemonics.generateMnemonic;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.io.SerializableWithKnownLength;
import com.swirlds.common.io.exceptions.BadIOException;
import com.swirlds.common.io.streams.AugmentedDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * A cryptographic hash of some data.
 */
public class Hash implements Comparable<Hash>, SerializableWithKnownLength, Serializable {
    public static final long CLASS_ID = 0xf422da83a251741eL;
    public static final int CLASS_VERSION = 1;

    private byte[] value;
    private Bytes bytes;
    private DigestType digestType;

    /**
     * Zero arg constructor. Creates a hash without any data using the default digest type ({@link DigestType#SHA_384}).
     */
    public Hash() {
        this(DigestType.SHA_384);
    }

    /**
     * Create a hash with a specific digest type but without any hash data.
     *
     * @param digestType
     * 		the digest type
     */
    public Hash(@NonNull final DigestType digestType) {
        this(new byte[digestType.digestLength()], digestType);
    }

    /**
     * Instantiate a hash with data from a byte array. Uses the default digest type ({@link DigestType#SHA_384}).
     *
     * @param value
     * 		the hash bytes
     */
    public Hash(@NonNull final byte[] value) {
        this(value, DigestType.SHA_384);
    }

    /**
     * Instantiate a hash with data from a byte array with a specific digest type.
     *
     * @param value
     * 		the hash bytes
     * @param digestType
     * 		the digest type
     */
    public Hash(@NonNull final byte[] value, @NonNull final DigestType digestType) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(digestType, "digestType");

        if (value.length != digestType.digestLength()) {
            throw new IllegalArgumentException("value: " + value.length);
        }

        this.digestType = digestType;
        this.value = value;
        this.bytes = Bytes.wrap(this.value);
    }

    /**
     * Instantiate a hash with a byte buffer and a digest type.
     *
     * @param byteBuffer
     * 		a buffer that contains the data for this hash
     * @param digestType
     * 		the digest type of this hash
     */
    public Hash(@NonNull final ByteBuffer byteBuffer, @NonNull final DigestType digestType) {
        this(digestType);
        byteBuffer.get(this.value);
    }

    /**
     * Create a hash making a deep copy of another hash.
     *
     * @param other
     * 		the hash to copy
     */
    public Hash(@NonNull final Hash other) {
        if (other == null) {
            throw new IllegalArgumentException("other");
        }

        this.digestType = other.digestType;
        this.value = Arrays.copyOf(other.value, other.value.length);
        this.bytes = other.bytes;
    }

    /**
     * Get the byte array representing the value of the hash.
     *
     * @return the hash value
     * @deprecated in order to make hash immutable, this method should be removed
     */
    @Deprecated(forRemoval = true)
    public @NonNull byte[] getValue() {
        return value;
    }

    /**
     * @return a copy of the hash value as a byte array
     */
    public @NonNull byte[] copyToByteArray() {
        return bytes.toByteArray();
    }

    /**
     * @return the hash value as an immutable {@link Bytes} object
     */
    public @NonNull Bytes getBytes() {
        return bytes;
    }

    /**
     * Get a deep copy of this hash.
     *
     * @return a new hash
     */
    public @NonNull Hash copy() {
        return new Hash(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        requireNonNull(digestType, "digestType");
        requireNonNull(value, "value");
        out.writeInt(digestType.id());
        out.writeByteArray(value);
    }

    @Override
    public int getSerializedLength() {
        return Integer.BYTES + AugmentedDataOutputStream.getArraySerializedLength(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        final DigestType digestType = DigestType.valueOf(in.readInt());

        if (digestType == null) {
            throw new BadIOException("Invalid DigestType identifier read from the stream");
        }

        this.digestType = digestType;
        this.value = in.readByteArray(digestType.digestLength());

        if (value == null) {
            throw new BadIOException("Invalid hash value read from the stream");
        }
        this.bytes = Bytes.wrap(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof final Hash that)) {
            return false;
        }

        return digestType.id() == that.digestType.id() && Arrays.equals(value, that.value);
    }

    /**
     * Check if the bytes of this hash are equal to the bytes supplied.
     *
     * @param bytes
     * 		the bytes to compare
     * @return true if the bytes are equal, false otherwise
     */
    public boolean equalBytes(@Nullable final Bytes bytes) {
        return bytes != null && value.length == bytes.length() && bytes.contains(0, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return ((value[0] << 24) + (value[1] << 16) + (value[2] << 8) + (value[3]));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(@NonNull final Hash that) {
        if (this == that) {
            return 0;
        }

        if (that == null) {
            throw new NullPointerException("that");
        }

        final int ret = Integer.compare(digestType.id(), that.digestType.id());

        if (ret != 0) {
            return ret;
        }

        return Arrays.compare(value, that.value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull String toString() {
        return (value == null) ? "null" : hex(value);
    }

    /**
     * Create a hexadecimal string representation of this hash.
     */
    public @NonNull String toHex() {
        return toString();
    }

    /**
     * Create a short string representation of this hash.
     *
     * @param length
     * 		the number of characters to include in the short string
     */
    public @NonNull String toHex(final int length) {
        return (value == null) ? "null" : hex(value, length);
    }

    /**
     * Get a four word mnemonic for this hash. Helpful in situations where a human needs to read a hash.
     *
     * @return a mnemonic for this hash
     */
    public @NonNull String toMnemonic() {
        return generateMnemonic(value, 4);
    }

    /**
     * Get the digest type of this hash.
     *
     * @return the digest type
     */
    public @NonNull DigestType getDigestType() {
        return this.digestType;
    }
}
