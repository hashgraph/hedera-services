package com.hedera.services.state.virtual;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.merkle.internals.BitPackUtils.signedLowOrder32From;
import static com.hedera.services.state.merkle.internals.BitPackUtils.unsignedHighOrder32From;
import static java.lang.Math.min;

/**
 * Represents the information stored in the virtualized merkle node associated with a unique token (NFT).
 */
public class UniqueTokenValue implements VirtualValue {
	private static final long CLASS_ID = 0xefa8762aa03ce697L;
	// Maximum amount of metadata bytes allowed.
	private static final int MAX_METADATA_BYTES = 100;
	/** Current version of the encoding scheme. */
	/* package */ static final int CURRENT_VERSION = 1;

	/** The account number field of the owner's account id. */
	private long ownerAccountNum;

	/**
	 * The compressed creation time of the token:
	 *  - the higher 32-bits represents an unsigned integer value containing the number of seconds since the epoch
	 *    representing the consensus time at which the token was created.
	 *  - the lower 32-bits represents a signed integer containing nanosecond resolution to the timestamp in which the
	 *    token was created.
	 */
	private long packedCreationTime;
	/** The metadata associated with the unique token, the maximum number of bytes for this field is 100 bytes. */
	private byte[] metadata = new byte[0];
	/** Whether this instance is immutable (e.g., the ownerAccountNum field may only be mutated when this is false). */
	private boolean isImmutable = false;

	public UniqueTokenValue() {}

	public UniqueTokenValue(
			long ownerAccountNum,
			RichInstant creationTime,
			byte[] metadata) {
		this.ownerAccountNum = ownerAccountNum;
		this.packedCreationTime = packedTime(creationTime.getSeconds(), creationTime.getNanos());
		this.metadata = metadata;
	}

	private UniqueTokenValue(UniqueTokenValue other) {
		ownerAccountNum = other.ownerAccountNum;
		packedCreationTime = other.packedCreationTime;
		metadata = other.metadata;
		// Do not copy over the isImmutable field.
	}

	/**
	 * Returns the size in bytes of the class (if fixed) or {@link DataFileCommon#VARIABLE_DATA_SIZE} if variable sized.
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
		UniqueTokenValue copy = new UniqueTokenValue(this);
		copy.isImmutable = true;
		return copy;
	}

	@Override
	public void release() { /* no-op */ }

	interface CheckedConsumer<T> {
		void accept(T t) throws IOException;
	}

	interface CheckedConsumer2<T, U> {
		void accept(T t, U u) throws IOException;
	}

	/* package */ void serializeTo(
			CheckedConsumer<Byte> writeByteFn,
			CheckedConsumer<Long> writeLongFn,
			CheckedConsumer2<byte[], Integer> writeBytesFn) throws IOException {
		writeLongFn.accept(ownerAccountNum);
		writeLongFn.accept(packedCreationTime);

		// Cap the maximum metadata bytes to avoid malformed inputs with too many metadata bytes.
		int len = min(MAX_METADATA_BYTES, metadata.length);
		writeByteFn.accept((byte) len);
		if (len > 0) {
			writeBytesFn.accept(metadata, len);
		}
	}

	interface CheckedSupplier<T> {
		T get() throws IOException;
	}

	/* package */ void deserializeFrom(
			CheckedSupplier<Byte> readByteFn,
			CheckedSupplier<Long> readLongFn,
			CheckedConsumer<byte[]> readBytesFn,
			int dataVersion) throws IOException {
		throwIfImmutable();
		assert dataVersion == CURRENT_VERSION : "dataVersion=" + dataVersion + " != getVersion()=" + CURRENT_VERSION;

		ownerAccountNum = readLongFn.get();
		packedCreationTime = readLongFn.get();
		int len =  readByteFn.get();

		// Guard against mal-formed data by capping the max length.
		len = min(len, MAX_METADATA_BYTES);
		// Create a new byte array everytime. This way, copies can be references to
		// previously allocated content without worrying about mutation.
		metadata = new byte[len];

		if (len > 0) {
			readBytesFn.accept(metadata);
		}
	}

	@Override
	public void deserialize(SerializableDataInputStream inputStream, int version) throws IOException {
		deserializeFrom(inputStream::readByte, inputStream::readLong, inputStream::readFully, version);
	}

	@Override
	public void deserialize(ByteBuffer byteBuffer, int version) throws IOException {
		deserializeFrom(byteBuffer::get, byteBuffer::getLong, byteBuffer::get, version);
	}

	@Override
	public void serialize(SerializableDataOutputStream output) throws IOException {
		serializeTo(output::writeByte, output::writeLong, (data, len) -> output.write(data, 0, len));
	}

	@Override
	public void serialize(ByteBuffer byteBuffer) throws IOException {
		serializeTo(byteBuffer::put, byteBuffer::putLong, (data, len) -> byteBuffer.put(data, 0, len));
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
		return Objects.hash(ownerAccountNum, packedCreationTime, Arrays.hashCode(metadata));
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(UniqueTokenValue.class)
				.add("owner", EntityId.fromNum(ownerAccountNum).toAbbrevString())
				.add("creationTime", Instant.ofEpochSecond(
						unsignedHighOrder32From(packedCreationTime),
						signedLowOrder32From(packedCreationTime)))
				.add("metadata", metadata)
				.add("isImmutable", isImmutable)
				.toString();
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof UniqueTokenValue other) {
			return other.ownerAccountNum == this.ownerAccountNum
					&& other.packedCreationTime == this.packedCreationTime
					&& Arrays.equals(other.metadata, this.metadata);
		}
		return false;
	}

	public long getOwnerAccountNum() {
		return ownerAccountNum;
	}

	public Instant getCreationTime() {
		return Instant.ofEpochSecond(
				unsignedHighOrder32From(packedCreationTime),
				signedLowOrder32From(packedCreationTime));
	}

	public byte[] getMetadata() {
		return metadata;
	}
}
