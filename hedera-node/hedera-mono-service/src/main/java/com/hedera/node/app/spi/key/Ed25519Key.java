package com.hedera.node.app.spi.key;

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import static com.hedera.services.state.serdes.IoUtils.writeNullable;
import static com.swirlds.common.utility.CommonUtils.hex;

public class Ed25519Key implements HederaKey {
	private static final int ED25519_BYTE_LENGTH = 32;
	private byte[] key;

	public Ed25519Key(final byte[] key) {
		this.key = key;
	}

	public Ed25519Key(final Ed25519Key that) {
		this.key = that.key;
	}

	@Override
	public boolean isPrimitive() {
		return true;
	}

	@Override
	public boolean isEmpty() {
		return key == null || key.length == 0;
	}

	@Override
	public boolean isValid() {
		if (isEmpty()) {
			return false;
		}
		return key.length == ED25519_BYTE_LENGTH;
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeInt(key.length);
		writeNullable(key, out, (hashOut, dout) -> dout.writeByteArray(hashOut));
	}

	@Override
	public void deserialize(final SerializableDataInputStream in) throws IOException {
		final var length = in.readInt();
		this.key = in.readByteArray(length);
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || Ed25519Key.class != o.getClass()) {
			return false;
		}
		final var that = (Ed25519Key) o;
		return Arrays.equals(this.key, that.key);
	}

	@Override
	public Ed25519Key copy() {
		return new Ed25519Key(this);
	}

	@Override
	public String toString(){
		return MoreObjects.toStringHelper(JEd25519Key.class)
				.add("key", (key != null) ? hex(key) : "<N/A>")
				.toString();
	}

	@Override
	public int hashCode() {
		return Objects.hash(key);
	}
}
