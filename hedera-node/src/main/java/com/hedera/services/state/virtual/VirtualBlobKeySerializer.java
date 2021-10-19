package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VirtualBlobKeySerializer implements KeySerializer<VirtualBlobKey> {
	@Override
	public int deserializeKeySize(ByteBuffer byteBuffer) {
		return VirtualBlobKey.sizeInBytes();
	}

	@Override
	public int getSerializedSize() {
		return VirtualBlobKey.sizeInBytes();
	}

	@Override
	public long getCurrentDataVersion() {
		return 1;
	}

	@Override
	public VirtualBlobKey deserialize(ByteBuffer byteBuffer, long version) throws IOException {
		final var key = new VirtualBlobKey();
		key.deserialize(byteBuffer, (int) version);
		return key;
	}

	@Override
	public int serialize(VirtualBlobKey key, SerializableDataOutputStream out) throws IOException {
		key.serialize(out);
		return VirtualBlobKey.sizeInBytes();
	}
}
