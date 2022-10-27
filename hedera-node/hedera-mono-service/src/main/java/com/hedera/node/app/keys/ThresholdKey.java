package com.hedera.node.app.keys;

import com.hedera.node.app.spi.keys.ReplHederaKey;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class ThresholdKey implements ReplHederaKey {
	private static final long CLASS_ID = 15520365L;
	private static final int VERSION = 1;
	int threshold;
	private KeyList keys;

	public ThresholdKey(final int threshold, @Nonnull final KeyList keys) {
		Objects.requireNonNull(keys);
		this.threshold = threshold;
		this.keys = keys;
	}

	public ThresholdKey(@Nonnull final ThresholdKey that) {
		Objects.requireNonNull(that);
		this.threshold = that.threshold;
		this.keys = that.keys;
	}

	@Override
	public boolean isPrimitive() {
		return false;
	}

	@Override
	public boolean isEmpty() {
		return keys.isEmpty();
	}

	@Override
	public boolean isValid() {
		if (isEmpty()) {
			return false;
		}
		int length = keys.getKeys().size();
		return (threshold >= 1 && threshold <= length && keys.isValid());
	}

	@Override
	public ThresholdKey copy() {
		return new ThresholdKey(this);
	}

	@Override
	public ThresholdKey asReadOnly() {
		return copy();
	}

	@Override
	public void serialize(final ByteBuffer buf) throws IOException {
		buf.putInt(threshold);
		keys.serialize(buf);
	}

	@Override
	public void deserialize(final ByteBuffer buf, final int i) throws IOException {
		threshold = buf.getInt();
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		threshold = in.readInt();
		keys.deserialize(in, version);
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeInt(threshold);
		keys.serialize(out);
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
