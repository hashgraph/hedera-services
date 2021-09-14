package com.hedera.services.utils;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;

public class FcLong implements SelfSerializable, FastCopyable {
	private static final long CLASS_ID = 0x1d8fc60c62dc8982L;

	private long value;

	public FcLong(long value) {
		this.value = value;
	}

	@Override
	public FcLong copy() {
		return new FcLong(value);
	}

	@Override
	public void release() {
		/* No-op */
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		value = in.readLong();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(value);
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(value);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || FcLong.class != o.getClass()) {
			return false;
		}

		var that = (FcLong) o;
		return this.value == that.value;
	}
}
