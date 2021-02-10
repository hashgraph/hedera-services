package com.hedera.services.usage.crypto;

/*-
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.Key;

import java.nio.charset.StandardCharsets;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

public class ExtantCryptoContext {
	private final int currentNumTokenRels;
	private final Key currentKey;
	private final long currentExpiry;
	private final String currentMemo;
	private final boolean currentlyHasProxy;

	private ExtantCryptoContext(ExtantCryptoContext.Builder builder) {
		currentNumTokenRels = builder.currentNumTokenRels;
		currentMemo = builder.currentMemo;
		currentExpiry = builder.currentExpiry;
		currentKey = builder.currentKey;
		currentlyHasProxy = builder.currentlyHasProxy;
	}

	public long currentNonBaseRb() {
		return (currentlyHasProxy ? BASIC_ENTITY_ID_SIZE : 0)
				+ currentMemo.getBytes(StandardCharsets.UTF_8).length
				+ getAccountKeyStorageSize(currentKey);
	}

	public Key currentKey() {
		return currentKey;
	}

	public int currentNumTokenRels() {
		return currentNumTokenRels;
	}

	public long currentExpiry() {
		return currentExpiry;
	}

	public String currentMemo() {
		return currentMemo;
	}

	public boolean currentlyHasProxy() {
		return currentlyHasProxy;
	}

	public static ExtantCryptoContext.Builder newBuilder() {
		return new ExtantCryptoContext.Builder();
	}

	public static class Builder {
		private static final int HAS_PROXY_MASK = 1 << 0;
		private static final int EXPIRY_MASK = 1 << 1;
		private static final int MEMO_MASK = 1 << 2;
		private static final int KEY_MASK = 1 << 3;
		private static final int TOKEN_RELS_MASK = 1 << 4;

		private static final int ALL_FIELDS_MASK = TOKEN_RELS_MASK | EXPIRY_MASK | MEMO_MASK | KEY_MASK | HAS_PROXY_MASK;

		private int mask = 0;

		private int currentNumTokenRels;
		private Key currentKey;
		private String currentMemo;
		private boolean currentlyHasProxy;
		private long currentExpiry;

		private Builder() {}

		public ExtantCryptoContext build() {
			if (mask != ALL_FIELDS_MASK) {
				throw new IllegalStateException(String.format("Field mask is %d, not %d!", mask, ALL_FIELDS_MASK));
			}
			return new ExtantCryptoContext(this);
		}

		public ExtantCryptoContext.Builder setCurrentNumTokenRels(int currentNumTokenRels) {
			this.currentNumTokenRels = currentNumTokenRels;
			mask |= TOKEN_RELS_MASK;
			return this;
		}

		public ExtantCryptoContext.Builder setCurrentExpiry(long currentExpiry) {
			this.currentExpiry = currentExpiry;
			mask |= EXPIRY_MASK;
			return this;
		}

		public ExtantCryptoContext.Builder setCurrentMemo(String currentMemo) {
			this.currentMemo = currentMemo;
			mask |= MEMO_MASK;
			return this;
		}

		public ExtantCryptoContext.Builder setCurrentKey(Key currentKey) {
			this.currentKey = currentKey;
			mask |= KEY_MASK;
			return this;
		}

		public ExtantCryptoContext.Builder setCurrentlyHasProxy(boolean currentlyHasProxy) {
			this.currentlyHasProxy = currentlyHasProxy;
			mask |= HAS_PROXY_MASK;
			return this;
		}
	}
}
