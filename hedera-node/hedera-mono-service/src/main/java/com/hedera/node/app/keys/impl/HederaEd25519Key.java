/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.node.app.keys.impl;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.spi.keys.ReplHederaKey;
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

/**
 * A HederaKey that is an Ed25519 Key.
 */
public class HederaEd25519Key implements ReplHederaKey {
	private static final int ED25519_BYTE_LENGTH = 32;
	private static final long CLASS_ID = 15528682L;
	private static final int VERSION = 1;
	private byte[] key;
	private boolean immutable = false;

	@VisibleForTesting
	public HederaEd25519Key(){
		this.key = new byte[ED25519_BYTE_LENGTH];
	}

	public HederaEd25519Key(@Nonnull final byte[] key) {
		Objects.requireNonNull(key);
		this.key = key;
	}

	public HederaEd25519Key(@Nonnull final HederaEd25519Key that) {
		Objects.requireNonNull(that);
		this.key = that.key;
	}

	public byte[] getKey() {
		return key;
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
	public HederaEd25519Key copy() {
		return new HederaEd25519Key(this);
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

		if (!(o instanceof HederaEd25519Key)) {
			return false;
		}

		final HederaEd25519Key that = (HederaEd25519Key) o;
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
