package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Represents a key for a unique token (NFT).
 */
public class UniqueTokenKey implements VirtualKey<UniqueTokenKey> {
	private static final long CLASS_ID = 0x17f77b311f6L;

	/** Current version of the encoding scheme. */
	/* package */ static final int CURRENT_VERSION = 1;

	/**
	 * Expected maximum number of bytes this class will serialize to. Serialization will format will be:
	 *  - (1 byte) number of bytes, 1-255, following this byte that will contain the token number.
	 *  - (variable, up to 8 bytes)  the non-leading zero-bytes representing the token number.
	 */
	public static final int ESTIMATED_SIZE_BYTES = Long.BYTES + 1;


	/** The token number from the address will be used as the key.*/
	private long tokenNum;
	/** Hashcode will be updated whenever tokenNum changes.*/
	private int hashCode;

	public UniqueTokenKey() {}

	public UniqueTokenKey(long tokenNum) {
		setTokenNum(tokenNum);
	}

	public long getTokenNum() {
		return tokenNum;
	}

	private void setTokenNum(long tokenNum) {
		this.tokenNum = tokenNum;
		this.hashCode = Long.hashCode(tokenNum);
	}

	private static int computeNonZeroBytes(long value) {
		// The value returned from this will range in [1, 8].
		if (value == 0) {
			return 1;
		}

		// Max value here is (64 - 0)/8 = 8
		// Min value here is ceil((64 - 63)/8) = 1
		return (int) Math.ceil((double) (Long.SIZE - Long.numberOfLeadingZeros(value)) / 8D);
	}

	@Override
	public int compareTo(@NotNull UniqueTokenKey other) {
		if (this == other) {
			return 0;
		}
		return Long.compare(this.tokenNum, other.tokenNum);
	}

	/* package */ static interface ByteConsumer {
		void accept(byte b) throws IOException;
	}

	/* package */ int serializeTo(ByteConsumer output) throws IOException {
		int numOfBytes = computeNonZeroBytes(tokenNum);
		output.accept((byte) numOfBytes);
		for (int b = numOfBytes - 1; b >= 0; b--) {
			output.accept((byte) (tokenNum >> (b * 8)));
		}
		return numOfBytes + 1;
	}

	@Override
	public void serialize(ByteBuffer byteBuffer) throws IOException {
		serializeTo(byteBuffer::put);
	}

	@Override
	public void serialize(SerializableDataOutputStream outputStream) throws IOException {
		serializeTo(outputStream::write);
	}

	/* package */ static interface ByteSupplier {
		byte get() throws IOException;
	}

	/* package */ static long deserializeFrom(ByteSupplier input, int dataVersion) throws IOException {
		assert dataVersion == CURRENT_VERSION : "dataVersion=" + dataVersion + " != getVersion()=" + CURRENT_VERSION;
		byte numOfBytes = input.get();
		long value = 0;
		if (numOfBytes >= 8) value |= ((long) input.get() & 255) << 56;
		if (numOfBytes >= 7) value |= ((long) input.get() & 255) << 48;
		if (numOfBytes >= 6) value |= ((long) input.get() & 255) << 40;
		if (numOfBytes >= 5) value |= ((long) input.get() & 255) << 32;
		if (numOfBytes >= 4) value |= ((long) input.get() & 255) << 24;
		if (numOfBytes >= 3) value |= ((long) input.get() & 255) << 16;
		if (numOfBytes >= 2) value |= ((long) input.get() & 255) << 8;
		if (numOfBytes >= 1) value |= ((long) input.get() & 255);
		return value;
	}

	@Override
	public void deserialize(ByteBuffer byteBuffer, int dataVersion) throws IOException {
		setTokenNum(deserializeFrom(byteBuffer::get, dataVersion));
	}


	@Override
	public void deserialize(SerializableDataInputStream inputStream, int dataVersion) throws IOException {
		setTokenNum(deserializeFrom(inputStream::readByte, dataVersion));
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
	public boolean equals(Object obj) {
		if (obj != null && obj instanceof UniqueTokenKey) {
			return ((UniqueTokenKey) obj).tokenNum == tokenNum;
		}
		return false;
	}

	@Override
	public int getMinimumSupportedVersion() {
		return 1;
	}

	@Override
	public String toString() {
		return "UniqueTokenKey{tokenNum=" + tokenNum + "}";
	}
}
