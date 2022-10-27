package com.hedera.node.app.keys;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.hedera.node.app.spi.keys.HederaKey;
import com.hedera.node.app.spi.keys.ReplHederaKey;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.swirlds.common.utility.CommonUtils.hex;

/**
 * A HederaKey that is a list of HederaKeys.
 */
public class HederaKeyList implements ReplHederaKey {
	private static final long CLASS_ID = 15512048L;
	private static final int VERSION = 1;
	private List<ReplHederaKey> keys;

	@VisibleForTesting
	public HederaKeyList() {
		this.keys = new LinkedList<>();
	}

	public HederaKeyList(@Nonnull List<ReplHederaKey> keys) {
		Objects.requireNonNull(keys);
		this.keys = keys;
	}

	public HederaKeyList(@Nonnull HederaKeyList that) {
		Objects.requireNonNull(that);
		this.keys = that.keys;
	}

	public List<ReplHederaKey> getKeys() {
		return keys;
	}

	@Override
	public boolean isPrimitive() {
		return false;
	}

	@Override
	public boolean isEmpty() {
		if (keys != null) {
			for (var key : keys) {
				if ((null != key) && !key.isEmpty()) {
					return false;
				}
			}
		}
		return true;
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
	public HederaKeyList copy() {
		return new HederaKeyList(this);
	}

	@Override
	public HederaKeyList asReadOnly() {
		return copy();
	}

	@Override
	public void serialize(final ByteBuffer buf) throws IOException {
		try (final var baos = new ByteArrayOutputStream()) {
			try (final var out = new SerializableDataOutputStream(baos)) {
				this.serialize(out);
			}
			baos.flush();
			buf.put(baos.toByteArray());
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
		try (final var bais = new ByteArrayInputStream(buf.array())) {
			try (final var in = new SerializableDataInputStream(bais)) {
				this.deserialize(in, version);
			}
		}
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		final var len = in.readInt();
		for (int i = 0 ; i < len ; i++){
			final ReplHederaKey childKey = in.readSerializable();
			keys.add(childKey);
		}
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || HederaKeyList.class != o.getClass()) {
			return false;
		}
		final var that = (HederaKeyList) o;
		return new EqualsBuilder()
				.append(keys, that.keys)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(keys).build();
	}

	@Override
	public String toString(){
		return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
				.append("keys", keys)
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
