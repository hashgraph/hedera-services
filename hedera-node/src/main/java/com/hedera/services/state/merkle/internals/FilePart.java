package com.hedera.services.state.merkle.internals;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcqueue.FCQueueElement;

import java.io.IOException;

public class FilePart implements FCQueueElement {
	private static final int CURRENT_VERSION = 1;
	private static final long CLASS_ID = 0xd1b1fc6b87447a02L;

	private Hash hash;
	private byte[] data;

	public FilePart() {
		/* RuntimeConstructable */
	}

	public byte[] getData() {
		return data;
	}

	public FilePart(byte[] data) {
		this.data = data;
	}

	@Override
	public FilePart copy() {
		return this;
	}

	@Override
	public void release() {
		/* No-op */
	}

	@Override
	public Hash getHash() {
		return hash;
	}

	@Override
	public void setHash(Hash hash) {
		this.hash = hash;
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
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
	public int getVersion() {
		return CURRENT_VERSION;
	}
}
