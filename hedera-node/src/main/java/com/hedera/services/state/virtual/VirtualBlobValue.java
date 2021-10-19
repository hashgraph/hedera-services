package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VirtualBlobValue implements VirtualValue {
	private static final int CURRENT_VERSION = 1;
	private static final long CLASS_ID = 0x7eb72381159d8402L;

	private byte[] data;

	public VirtualBlobValue() {
		/* Required by deserialization facility */
	}

	public VirtualBlobValue(byte[] data) {
		this.data = data;
	}


	public VirtualBlobValue(final VirtualBlobValue that) {
		this.data = that.data;
	}

	@Override
	public VirtualBlobValue copy() {
		return new VirtualBlobValue(this);
	}

	@Override
	public VirtualValue asReadOnly() {
		return copy();
	}

	@Override
	public void release() {
		/* No-op */
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		data = in.readByteArray(Integer.MAX_VALUE);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeByteArray(data);
	}

	@Override
	public void serialize(ByteBuffer buffer) throws IOException {
		buffer.putInt(data.length);
		buffer.put(data);
	}

	@Override
	public void deserialize(ByteBuffer buffer, int version) throws IOException {
		final var n = buffer.getInt();
		data = new byte[n];
		buffer.get(data);
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	public static int sizeInBytes() {
		return DataFileCommon.VARIABLE_DATA_SIZE;
	}
}
