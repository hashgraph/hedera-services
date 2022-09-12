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

import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.merkle.internals.BitPackUtils.signedLowOrder32From;
import static com.hedera.services.state.merkle.internals.BitPackUtils.unsignedHighOrder32From;
import static com.hedera.services.utils.NftNumPair.MISSING_NFT_NUM_PAIR;
import static java.lang.Math.min;

import com.google.common.base.MoreObjects;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.NftNumPair;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents the information stored in the virtualized merkle node associated with a unique token
 * (NFT).
 */
public class UniqueTokenValue implements VirtualValue {
    private static final long CLASS_ID = 0xefa8762aa03ce697L;
    // Maximum amount of metadata bytes allowed.
    private static final int MAX_METADATA_BYTES = 100;
    /** Current version of the encoding scheme. */
    /* package */ static final int CURRENT_VERSION = 1;

    /** The account number field of the owner's account id. */
    private long ownerAccountNum;

    /** The account number field of the spender's account id. */
    private long spenderAccountNum;

    /**
     * The compressed creation time of the token: - the higher 32-bits represents an unsigned
     * integer value containing the number of seconds since the epoch representing the consensus
     * time at which the token was created. - the lower 32-bits represents a signed integer
     * containing nanosecond resolution to the timestamp in which the token was created.
     */
    private long packedCreationTime;
    /**
     * The metadata associated with the unique token, the maximum number of bytes for this field is
     * 100 bytes.
     */
    private byte[] metadata = new byte[0];
    // the NFT the owner has acquired after this UniqueToken
    private NftNumPair prev = MISSING_NFT_NUM_PAIR;
    // the NFT the owner has acquired before this UniqueToken
    private NftNumPair next = MISSING_NFT_NUM_PAIR;
    /**
     * Whether this instance is immutable (e.g., the ownerAccountNum field may only be mutated when
     * this is false).
     */
    private boolean isImmutable = false;

    public UniqueTokenValue() {}

    public UniqueTokenValue(
            final long ownerAccountNum,
            final long spenderAccountNum,
            final byte[] metadata,
            final RichInstant creationTime) {
        this.ownerAccountNum = ownerAccountNum;
        this.spenderAccountNum = spenderAccountNum;
        this.packedCreationTime = packedTime(creationTime.getSeconds(), creationTime.getNanos());
        this.metadata = metadata;
    }

    public UniqueTokenValue(final UniqueTokenValue other) {
        this.ownerAccountNum = other.ownerAccountNum;
        this.spenderAccountNum = other.spenderAccountNum;
        this.metadata = other.metadata;
        this.packedCreationTime = other.packedCreationTime;
        this.prev = other.prev;
        this.next = other.next;
        // Do not copy over the isImmutable field.
    }

    private UniqueTokenValue(final MerkleUniqueToken legacyToken) {
        this.ownerAccountNum = legacyToken.getOwner().num();
        this.spenderAccountNum = legacyToken.getSpender().num();
        this.packedCreationTime = legacyToken.getPackedCreationTime();
        this.metadata = legacyToken.getMetadata();
        this.prev = legacyToken.getPrev();
        this.next = legacyToken.getNext();
    }

    public static UniqueTokenValue from(final MerkleUniqueToken token) {
        return new UniqueTokenValue(token);
    }

    /**
     * Returns the size in bytes of the class (if fixed) or {@link
     * DataFileCommon#VARIABLE_DATA_SIZE} if variable sized.
     */
    public static int sizeInBytes() {
        return DataFileCommon.VARIABLE_DATA_SIZE;
    }

    @Override
    public VirtualValue copy() {
        // Make parent immutable as defined by the FastCopyable contract.
        this.isImmutable = true;
        return new UniqueTokenValue(this);
    }

    @Override
    public VirtualValue asReadOnly() {
        final UniqueTokenValue copy = new UniqueTokenValue(this);
        copy.isImmutable = true;
        return copy;
    }

    interface CheckedConsumer<T> {
        void accept(T t) throws IOException;
    }

    interface CheckedConsumer2<T, U> {
        void accept(T t, U u) throws IOException;
    }

    /* package */ void serializeTo(
            final CheckedConsumer<Byte> writeByteFn,
            final CheckedConsumer<Long> writeLongFn,
            final CheckedConsumer2<byte[], Integer> writeBytesFn)
            throws IOException {
        writeLongFn.accept(ownerAccountNum);
        writeLongFn.accept(spenderAccountNum);
        writeLongFn.accept(packedCreationTime);
        writeBytes(metadata, MAX_METADATA_BYTES, writeByteFn, writeBytesFn);
        writeNftNumPair(prev, writeLongFn);
        writeNftNumPair(next, writeLongFn);
    }

    interface CheckedSupplier<T> {
        T get() throws IOException;
    }

    private static NftNumPair readNftNumPair(final CheckedSupplier<Long> readLongFn)
            throws IOException {
        final var tokenNum = readLongFn.get();
        final var tokenSerial = readLongFn.get();
        return new NftNumPair(tokenNum, tokenSerial);
    }

    private static void writeNftNumPair(
            final NftNumPair nftNumPair, final CheckedConsumer<Long> writeLongFn)
            throws IOException {
        writeLongFn.accept(nftNumPair.tokenNum());
        writeLongFn.accept(nftNumPair.serialNum());
    }

    private static byte[] readBytes(
            final CheckedSupplier<Byte> readByteFn,
            final CheckedConsumer<byte[]> readBytesFn,
            final int maxBytes)
            throws IOException {
        // Guard against mal-formed data by capping the max length.
        final int len = min(readByteFn.get(), maxBytes);

        // Create a new byte array everytime. This way, copies can be references to
        // previously allocated content without worrying about mutation.
        final byte[] data = new byte[len];
        if (len > 0) {
            readBytesFn.accept(data);
        }
        return data;
    }

    private static void writeBytes(
            final byte[] data,
            final int maxBytes,
            final CheckedConsumer<Byte> writeByteFn,
            final CheckedConsumer2<byte[], Integer> writeBytesFn)
            throws IOException {
        // Cap the maximum metadata bytes to avoid malformed inputs with too many metadata bytes.
        final int len = min(maxBytes, data.length);
        writeByteFn.accept((byte) len);
        if (len > 0) {
            writeBytesFn.accept(data, len);
        }
    }

    /* package */ void deserializeFrom(
            final CheckedSupplier<Byte> readByteFn,
            final CheckedSupplier<Long> readLongFn,
            final CheckedConsumer<byte[]> readBytesFn)
            throws IOException {
        throwIfImmutable();
        ownerAccountNum = readLongFn.get();
        spenderAccountNum = readLongFn.get();
        packedCreationTime = readLongFn.get();
        metadata = readBytes(readByteFn, readBytesFn, MAX_METADATA_BYTES);
        prev = readNftNumPair(readLongFn);
        next = readNftNumPair(readLongFn);
    }

    @Override
    public void deserialize(final SerializableDataInputStream inputStream, final int version)
            throws IOException {
        deserializeFrom(inputStream::readByte, inputStream::readLong, inputStream::readFully);
    }

    @Override
    public void deserialize(final ByteBuffer byteBuffer, final int version) throws IOException {
        deserializeFrom(byteBuffer::get, byteBuffer::getLong, byteBuffer::get);
    }

    @Override
    public void serialize(final SerializableDataOutputStream output) throws IOException {
        serializeTo(
                output::writeByte, output::writeLong, (data, len) -> output.write(data, 0, len));
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer) throws IOException {
        serializeTo(
                byteBuffer::put, byteBuffer::putLong, (data, len) -> byteBuffer.put(data, 0, len));
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
    public boolean isImmutable() {
        return isImmutable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                ownerAccountNum,
                spenderAccountNum,
                packedCreationTime,
                Arrays.hashCode(metadata),
                prev,
                next);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(UniqueTokenValue.class)
                .add("owner", getOwner().toAbbrevString())
                .add("spender", getSpender().toAbbrevString())
                .add("creationTime", getCreationTime())
                .add("metadata", metadata)
                .add("prev", prev)
                .add("next", next)
                .add("isImmutable", isImmutable)
                .toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof UniqueTokenValue other) {
            return other.ownerAccountNum == this.ownerAccountNum
                    && other.spenderAccountNum == this.spenderAccountNum
                    && other.packedCreationTime == this.packedCreationTime
                    && Arrays.equals(other.metadata, this.metadata)
                    && other.prev.equals(this.prev)
                    && other.next.equals(this.next);
        }
        return false;
    }

    public EntityId getOwner() {
        return EntityId.fromNum(this.ownerAccountNum);
    }

    public long getOwnerAccountNum() {
        return this.ownerAccountNum;
    }

    public EntityId getSpender() {
        return EntityId.fromNum(this.spenderAccountNum);
    }

    public NftNumPair getPrev() {
        return prev;
    }

    public NftNumPair getNext() {
        return next;
    }

    public RichInstant getCreationTime() {
        return new RichInstant(
                unsignedHighOrder32From(packedCreationTime),
                signedLowOrder32From(packedCreationTime));
    }

    public long getPackedCreationTime() {
        return packedCreationTime;
    }

    public byte[] getMetadata() {
        return metadata;
    }

    public void setOwner(final EntityId owner) {
        throwIfImmutable("Cannot set owner since immutable.");
        this.ownerAccountNum = owner.num();
    }

    public void setSpender(final EntityId spender) {
        throwIfImmutable("Cannot set spender since immutable.");
        this.spenderAccountNum = spender.num();
    }

    public void setPrev(final NftNumPair prev) {
        throwIfImmutable("Cannot set prev since immutable.");
        this.prev = prev;
    }

    public void setNext(final NftNumPair next) {
        throwIfImmutable("Cannot set next since immutable.");
        this.next = next;
    }

    public void setPackedCreationTime(final long packedCreationTime) {
        throwIfImmutable("Cannot set creation time since immutable.");
        this.packedCreationTime = packedCreationTime;
    }

    public void setMetadata(final byte[] metadata) {
        throwIfImmutable("Cannot set metadata since immutable.");
        this.metadata = metadata;
    }
}
