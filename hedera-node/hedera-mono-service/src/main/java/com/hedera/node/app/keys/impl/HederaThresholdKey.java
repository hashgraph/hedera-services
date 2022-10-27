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
import com.hedera.node.app.spi.keys.HederaKey;
import com.hedera.node.app.spi.keys.ReplHederaKey;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A HederaKey that is a threshold key.
 */
public class HederaThresholdKey implements ReplHederaKey {
	private static final long CLASS_ID = 15520365L;
	private static final int VERSION = 1;
	int threshold;
	private HederaKeyList keys;

	private boolean immutable = false;

	@VisibleForTesting
	public HederaThresholdKey(){
		this.threshold = 0;
		this.keys = new HederaKeyList();
	}

	public HederaThresholdKey(final int threshold, @Nonnull final HederaKeyList keys) {
		Objects.requireNonNull(keys);
		this.threshold = threshold;
		this.keys = keys;
	}

	public HederaThresholdKey(@Nonnull final HederaThresholdKey that) {
		Objects.requireNonNull(that);
		this.threshold = that.threshold;
		this.keys = that.keys;
	}

	public int getThreshold() {
		return threshold;
	}

	public HederaKeyList getKeys() {
		return keys;
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
	public HederaThresholdKey copy() {
		return new HederaThresholdKey(this);
	}

	@Override
	public HederaThresholdKey asReadOnly() {
		return copy();
	}

	@Override
	public void serialize(final ByteBuffer buf) throws IOException {
		buf.putInt(threshold);
		keys.serialize(buf);
	}

	@Override
	public void deserialize(final ByteBuffer buf, final int version) throws IOException {
		threshold = buf.getInt();
		keys.deserialize(buf, version);
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
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || HederaThresholdKey.class != o.getClass()) {
			return false;
		}
		final var that = (HederaThresholdKey) o;
		return new EqualsBuilder()
				.append(threshold, that.threshold)
				.append(keys, that.keys)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(threshold).append(keys).build();
	}

	@Override
	public String toString(){
		return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
				.append("threshold", threshold)
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
		keys.visitPrimitiveKeys(actionOnSimpleKey);
	}
}
