/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual;

import com.google.common.base.MoreObjects;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NftNumPair;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;

/** Represents a key for a unique token (NFT). */
public class UniqueTokenKey implements VirtualKey<UniqueTokenKey> {
    private static final long CLASS_ID = 0x17f77b311f6L;

    /** Current version of the encoding scheme. */
    /* package */ static final int CURRENT_VERSION = 1;

    /**
     * Expected maximum number of bytes this class will serialize to. Serialization format will be:
     * - (1 byte) number of bytes for next two fields: - The higher 4-bits will contain a number (0
     * - 8) indicating number of bytes needed for the entity number. - The lower 4-bits will contain
     * a number (0 - 8) indicating number of bytes needed for the serial number. - (variable, 0 to 8
     * bytes) the non-leading zero-bytes representing the entity number. - (variable, 0 to 8 bytes)
     * the non-leading zero-bytes representing the serial number.
     */
    public static final int ESTIMATED_SIZE_BYTES = Long.BYTES + Long.BYTES + 1;

    private static final long LARGE_PRIME = 2000000011L;

    /**
     * Constructs a UniqueTokenKey from an NftId instance.
     *
     * @param nftId the NftId to create a UniqueTokenKey from.
     * @return a new instance of a UniqueTokenKey corresponding to NftId.
     */
    public static UniqueTokenKey from(final NftId nftId) {
        return new UniqueTokenKey(nftId.num(), nftId.serialNo());
    }

    public static UniqueTokenKey from(final NftNumPair nftNumPair) {
        return new UniqueTokenKey(nftNumPair.tokenNum(), nftNumPair.serialNum());
    }

    /** The entity number of the token. */
    private long entityNum;

    /** Serial number of the token. */
    private long tokenSerial;

    /** Hashcode will be updated whenever tokenNum changes. */
    private int hashCode;

    public UniqueTokenKey() {}

    public UniqueTokenKey(final long entityNum, final long tokenSerial) {
        setTokenId(entityNum, tokenSerial);
    }

    public long getNum() {
        return entityNum;
    }

    public long getTokenSerial() {
        return tokenSerial;
    }

    private void setTokenId(final long entityNum, final long tokenSerial) {
        this.entityNum = entityNum;
        this.tokenSerial = tokenSerial;
        // Consider using NonCryptographicHashing.hash64(long1, long2) when made available.
        this.hashCode = Long.hashCode(entityNum * LARGE_PRIME + tokenSerial);
    }

    private static int computeNonZeroBytes(final long value) {
        // The value returned from this will range in [0, 8].
        if (value == 0) {
            return 0;
        }

        // Max value here is (64 - 0)/8 = 8
        // Min value here is ceil((64 - 63)/8) = 1
        final var nonZeroBits = Long.SIZE - Long.numberOfLeadingZeros(value);
        return (nonZeroBits / 8) + Math.min(1, nonZeroBits % 8);
    }

    @Override
    public int compareTo(@NotNull final UniqueTokenKey other) {
        if (this == other) {
            return 0;
        }
        // Sort by entity num first, followed by token serial number.
        if (this.entityNum == other.entityNum) {
            return Long.compare(this.tokenSerial, other.tokenSerial);
        }
        return Long.compare(this.entityNum, other.entityNum);
    }

    /* package */ interface ByteConsumer {
        void accept(byte b) throws IOException;
    }

    private static byte packLengths(final int upper, final int lower) {
        return (byte) ((upper << 4) | (lower & 0x0F));
    }

    private static int unpackUpperLength(final int packed) {
        return (packed >> 4) & 0x0F;
    }

    private static int unpackLowerLength(final int packed) {
        return packed & 0x0F;
    }

    private static void writePartial(
            final long value, final int numBytes, final ByteConsumer output) throws IOException {
        for (int b = numBytes - 1; b >= 0; b--) {
            output.accept((byte) (value >> (b * 8)));
        }
    }

    /**
     * Fetch the key size (including the stored byte length) from a ByteBuffer containing the
     * serialized bytes.
     *
     * <p>Note: This method will update the position of the provided byteBuffer.
     *
     * @param byteBuffer the ByteBuffer to fetch data from.
     * @return the number of bytes the key occupies (including the byte for the length field).
     */
    /* package */ static int deserializeKeySize(final ByteBuffer byteBuffer) {
        final byte packedLength = byteBuffer.get();
        return 1 + unpackLowerLength(packedLength) + unpackUpperLength(packedLength);
    }

    /**
     * Serializes the instance into a stream of bytes and write to the provided output.
     *
     * @param output provides a function that is called to write an output byte.
     * @return the number of bytes written out.
     * @throws IOException if an error is encountered while trying to write to output.
     */
    /* package */ int serializeTo(final ByteConsumer output) throws IOException {
        final int entityLen = computeNonZeroBytes(entityNum);
        final int tokenSerialLen = computeNonZeroBytes(tokenSerial);

        // packed format: nnnnssss
        // - nnnn contains bits representing the entity length
        // - ssss contains bits representing the token serial length
        final byte packedLengths = packLengths(entityLen, tokenSerialLen);

        output.accept(packedLengths);
        writePartial(entityNum, entityLen, output);
        writePartial(tokenSerial, tokenSerialLen, output);

        return entityLen + tokenSerialLen + 1;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer) throws IOException {
        serializeTo(byteBuffer::put);
    }

    @Override
    public void serialize(final SerializableDataOutputStream outputStream) throws IOException {
        serializeTo(outputStream::write);
    }

    private interface ByteSupplier {
        byte get() throws IOException;
    }

    private static long decodeVariableField(final ByteSupplier input, final int numBytes)
            throws IOException {
        long value = 0;
        for (int n = Math.min(8, numBytes), shift = 8 * (n - 1); n > 0; n--, shift -= 8) {
            value |= ((long) input.get() & 0xFF) << shift;
        }
        return value;
    }

    private void deserializeFrom(final ByteSupplier input) throws IOException {
        final byte packedLengths = input.get();
        final int numEntityBytes = unpackUpperLength(packedLengths);
        final int numSerialBytes = unpackLowerLength(packedLengths);
        final long num = decodeVariableField(input, numEntityBytes);
        final long serial = decodeVariableField(input, numSerialBytes);
        setTokenId(num, serial);
    }

    @Override
    public void deserialize(final ByteBuffer byteBuffer, final int dataVersion) throws IOException {
        deserializeFrom(byteBuffer::get);
    }

    @Override
    public void deserialize(final SerializableDataInputStream inputStream, final int dataVersion)
            throws IOException {
        deserializeFrom(inputStream::readByte);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof UniqueTokenKey other) {
            return this.entityNum == other.entityNum && this.tokenSerial == other.tokenSerial;
        }
        return false;
    }

    public boolean equalsTo(final ByteBuffer byteBuffer) throws IOException {
        final byte packedLengths = byteBuffer.get();
        final int numEntityBytes = unpackUpperLength(packedLengths);
        final int numSerialBytes = unpackLowerLength(packedLengths);
        final long num = decodeVariableField(byteBuffer::get, numEntityBytes);
        if (num != this.entityNum) return false;
        final long serial = decodeVariableField(byteBuffer::get, numSerialBytes);
        return serial == this.tokenSerial;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return 1;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(UniqueTokenKey.class)
                .add("entityNum", entityNum)
                .add("tokenSerial", tokenSerial)
                .toString();
    }

    /**
     * @return a corresponding {@link EntityNumPair} from this instance.
     */
    public EntityNumPair toEntityNumPair() {
        return EntityNumPair.fromLongs(entityNum, tokenSerial);
    }

    /**
     * @return a corresponding {@link NftNumPair} from this instance.
     */
    public NftNumPair toNftNumPair() {
        return NftNumPair.fromLongs(entityNum, tokenSerial);
    }
}
