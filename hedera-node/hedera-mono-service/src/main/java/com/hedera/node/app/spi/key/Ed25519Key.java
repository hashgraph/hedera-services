package com.hedera.node.app.spi.key;

import com.google.common.base.MoreObjects;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import static com.swirlds.common.utility.CommonUtils.hex;
import static java.lang.Math.min;

public class Ed25519Key implements HederaKey {
	private static final int ED25519_BYTE_LENGTH = 32;
	private static final long CLASS_ID = 15528682L;
	private static final int VERSION = 1;
	private byte[] key;

	public Ed25519Key(@Nonnull final byte[] key) {
		Objects.requireNonNull(key);
		this.key = key;
	}

	public Ed25519Key(@Nonnull final Ed25519Key that) {
		Objects.requireNonNull(that);
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
	public Ed25519Key copy() {
		return new Ed25519Key(this);
	}

	@Override
	public VirtualValue asReadOnly() {
		return copy();
	}

	@Override
	public void serialize(final ByteBuffer buf) throws IOException {
		final var len = min(key.length, ED25519_BYTE_LENGTH);
		buf.put((byte) len);
		if(len > 0) {
			buf.put(key, 0, len);
		}
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		final var len = key.length;
		out.writeInt(len);
		if(len > 0){
			out.writeByteArray(key);
		}
	}

	@Override
	public void deserialize(final ByteBuffer buf, final int version) throws IOException {
		final var len = min(buf.getInt(), ED25519_BYTE_LENGTH);
		byte[] data = new byte[len];
		if(len > 0) {
			buf.get(data);
		}
		this.key = data;
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
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
	public int hashCode() {
		return Objects.hash(key);
	}

	@Override
	public String toString(){
		return MoreObjects.toStringHelper(Ed25519Key.class)
				.add("key", (key != null) ? hex(key) : "<N/A>")
				.toString();
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return VERSION;
	}
}
