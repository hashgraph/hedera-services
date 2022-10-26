package com.hedera.node.app.keys;

import com.hedera.node.app.spi.keys.HederaKey;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ThresholdKey implements HederaKey {
	private static final long CLASS_ID = 15528682L;
	private static final int VERSION = 1;

	int threshold;
	private KeyList keys;

	public ThresholdKey(final int threshold, final KeyList keys) {
		this.threshold = threshold;
		this.keys = keys;
	}

	@Override
	public boolean isPrimitive() {
		return false;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public boolean isValid() {
		return false;
	}

	@Override
	public VirtualValue copy() {
		return null;
	}

	@Override
	public VirtualValue asReadOnly() {
		return null;
	}

	@Override
	public void serialize(final ByteBuffer byteBuffer) throws IOException {

	}

	@Override
	public void deserialize(final ByteBuffer byteBuffer, final int i) throws IOException {

	}

	@Override
	public void deserialize(final SerializableDataInputStream serializableDataInputStream,
			final int i) throws IOException {

	}

	@Override
	public void serialize(final SerializableDataOutputStream serializableDataOutputStream) throws IOException {

	}

	@Override
	public long getClassId() {
		return 0;
	}

	@Override
	public int getVersion() {
		return 0;
	}
}
