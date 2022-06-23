package com.hedera.services.usage.util;

/*-
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.RandomGenerateTransactionBody;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class RandomGenerateMeta {
	private final long msgBytesUsed;
	public RandomGenerateMeta(RandomGenerateTransactionBody txn) {
		msgBytesUsed = txn.getSerializedSize(); // Is this correct ?
	}

	public RandomGenerateMeta(RandomGenerateMeta.Builder builder) {
		msgBytesUsed = builder.msgBytes;
	}

	public long getMsgBytesUsed() {
		return msgBytesUsed;
	}

	public static class Builder {
		private long msgBytes;

		public RandomGenerateMeta.Builder msgBytesUsed(long msgBytes) {
			this.msgBytes = msgBytes;
			return this;
		}

		public Builder() {
			// empty here on purpose.
		}

		public RandomGenerateMeta build() {
			return new RandomGenerateMeta(this);
		}
	}

	public static RandomGenerateMeta.Builder newBuilder() {
		return new RandomGenerateMeta.Builder();
	}

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("baseSize", msgBytesUsed)
				.toString();
	}
}
