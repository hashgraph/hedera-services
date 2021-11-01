package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;

import java.io.IOException;

public class VirtualBlobValueSupplier implements SelfSerializableSupplier<VirtualBlobValue> {
	private static final long CLASS_ID = 0xd08565ba3cf6c5bfL;
	private static final int CURRENT_VERSION = 1;

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		/* No-op */
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		/* No-op */
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
	public VirtualBlobValue get() {
		return new VirtualBlobValue();
	}
}
