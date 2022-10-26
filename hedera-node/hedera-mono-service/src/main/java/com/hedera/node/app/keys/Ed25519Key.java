package com.hedera.node.app.keys;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.spi.keys.HederaKey;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import static com.swirlds.common.utility.CommonUtils.hex;
import static java.lang.Math.min;

public class Ed25519Key implements HederaKey {
	private static final int ED25519_BYTE_LENGTH = 32;
	private static final long CLASS_ID = 15528682L;
	private static final int VERSION = 1;
	private byte[] key;

	@VisibleForTesting
	public Ed25519Key(){
		this.key = new byte[ED25519_BYTE_LENGTH];
	}

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
		return key.length == 0;
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
		if (this == o) {
			return true;
		}

		if (!(o instanceof Ed25519Key)) {
			return false;
		}

		final Ed25519Key that = (Ed25519Key) o;
		return new EqualsBuilder()
				.append(key, that.key)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(key).build();
	}

	@Override
	public String toString(){
		return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
				.append("key", hex(key))
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
