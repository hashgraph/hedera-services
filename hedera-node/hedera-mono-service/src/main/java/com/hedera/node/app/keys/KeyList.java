package com.hedera.node.app.keys;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.spi.keys.HederaKey;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class KeyList implements HederaKey {
	private static final long CLASS_ID = 15512048L;
	private static final int VERSION = 1;
	private List<HederaKey> keys;

	public KeyList() {
		this.keys = new LinkedList<>();
	}

	public KeyList(@Nonnull List<HederaKey> keys) {
		Objects.requireNonNull(keys);
		this.keys = keys;
	}

	public KeyList(@Nonnull KeyList that) {
		Objects.requireNonNull(that);
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
		for (var key : keys) {
			if ((null == key) || !key.isValid()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public KeyList copy() {
		return new KeyList(this);
	}

	@Override
	public KeyList asReadOnly() {
		return copy();
	}

	@Override
	public void serialize(final ByteBuffer buf) throws IOException {
		final var len = keys.size();
		buf.put((byte) len);
		for(final var key : keys){
			key.serialize(buf);
		}
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		final var len = keys.size();
		out.writeInt(len);
		for (final var key : keys){
			key.serialize(out);
		}
	}

	@Override
	public void deserialize(final ByteBuffer buf, final int version) throws IOException {
		final var len = buf.getInt();
		final List<HederaKey> keys = new LinkedList<>();
		for(int i = 0; i < len ; i++){
//			final HederaKey key = deserialize(buf, version);
//			keys.add(key);
		}
		this.keys = keys;
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {

	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || KeyList.class != o.getClass()) {
			return false;
		}
		final var that = (KeyList) o;
		return keys.equals(that.keys);
	}

	@Override
	public int hashCode() {
		return Objects.hash(keys);
	}

	@Override
	public String toString(){
		return MoreObjects.toStringHelper(KeyList.class)
				.add("keys", keys)
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

	@Override
	public void visitPrimitiveKeys(final Consumer<HederaKey> actionOnSimpleKey) {
		keys.forEach(k -> k.visitPrimitiveKeys(actionOnSimpleKey));
	}
}
