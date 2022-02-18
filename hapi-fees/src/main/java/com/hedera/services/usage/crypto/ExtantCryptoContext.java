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

import com.hederahashgraph.api.proto.java.GrantedCryptoAllowance;
import com.hederahashgraph.api.proto.java.GrantedNftAllowance;
import com.hederahashgraph.api.proto.java.GrantedTokenAllowance;
import com.hederahashgraph.api.proto.java.Key;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToCryptoMapFromGranted;
import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToNftMapFromGranted;
import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToTokenMapFromGranted;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

public class ExtantCryptoContext {
	private final int currentNumTokenRels;
	private final Key currentKey;
	private final long currentExpiry;
	private final String currentMemo;
	private final boolean currentlyHasProxy;
	private final int currentMaxAutomaticAssociations;
	private final Map<Long, Long> currentCryptoAllowances;
	private final Map<AllowanceMapKey, Long> currentTokenAllowances;
	private final Map<AllowanceMapKey, AllowanceMapValue> currentNftAllowances;

	private ExtantCryptoContext(ExtantCryptoContext.Builder builder) {
		currentNumTokenRels = builder.currentNumTokenRels;
		currentMemo = builder.currentMemo;
		currentExpiry = builder.currentExpiry;
		currentKey = builder.currentKey;
		currentlyHasProxy = builder.currentlyHasProxy;
		currentMaxAutomaticAssociations = builder.currentMaxAutomaticAssociations;
		this.currentCryptoAllowances = builder.currentCryptoAllowances;
		this.currentTokenAllowances = builder.currentTokenAllowances;
		this.currentNftAllowances = builder.currentNftAllowances;
	}

	public long currentNonBaseRb() {
		return (long) (currentlyHasProxy ? BASIC_ENTITY_ID_SIZE : 0)
				+ currentMemo.getBytes(StandardCharsets.UTF_8).length
				+ getAccountKeyStorageSize(currentKey)
				+ (currentMaxAutomaticAssociations == 0 ? 0 : INT_SIZE);
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

	public int currentMaxAutomaticAssociations() {
		return currentMaxAutomaticAssociations;
	}

	public Map<Long, Long> currentCryptoAllowances() {
		return currentCryptoAllowances;
	}

	public Map<AllowanceMapKey, Long> currentTokenAllowances() {
		return currentTokenAllowances;
	}

	public Map<AllowanceMapKey, AllowanceMapValue> currentNftAllowances() {
		return currentNftAllowances;
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
		private static final int MAX_AUTO_ASSOCIATIONS_MASK = 1 << 5;
		private static final int CRYPTO_ALLOWANCES_MASK = 1 << 6;
		private static final int TOKEN_ALLOWANCES_MASK = 1 << 7;
		private static final int NFT_ALLOWANCES_MASK = 1 << 8;

		private static final int ALL_FIELDS_MASK =
				TOKEN_RELS_MASK | EXPIRY_MASK | MEMO_MASK | KEY_MASK | HAS_PROXY_MASK | MAX_AUTO_ASSOCIATIONS_MASK
						| CRYPTO_ALLOWANCES_MASK | TOKEN_ALLOWANCES_MASK | NFT_ALLOWANCES_MASK;

		private int mask = 0;

		private int currentNumTokenRels;
		private Key currentKey;
		private String currentMemo;
		private boolean currentlyHasProxy;
		private long currentExpiry;
		private int currentMaxAutomaticAssociations;
		private Map<Long, Long> currentCryptoAllowances;
		private Map<AllowanceMapKey, Long> currentTokenAllowances;
		private Map<AllowanceMapKey, AllowanceMapValue> currentNftAllowances;

		private Builder() {
		}

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

		public ExtantCryptoContext.Builder setCurrentMaxAutomaticAssociations(int currentMaxAutomaticAssociations) {
			this.currentMaxAutomaticAssociations = currentMaxAutomaticAssociations;
			mask |= MAX_AUTO_ASSOCIATIONS_MASK;
			return this;
		}

		public ExtantCryptoContext.Builder setCurrentCryptoAllowances(List<GrantedCryptoAllowance> currentCryptoAllowances) {
			this.currentCryptoAllowances = CryptoContextUtils.convertToCryptoMapFromGranted(currentCryptoAllowances);
			mask |= CRYPTO_ALLOWANCES_MASK;
			return this;
		}

		public ExtantCryptoContext.Builder setCurrentTokenAllowances(
				List<GrantedTokenAllowance> currentTokenAllowances) {
			this.currentTokenAllowances = CryptoContextUtils.convertToTokenMapFromGranted(currentTokenAllowances);
			mask |= TOKEN_ALLOWANCES_MASK;
			return this;
		}

		public ExtantCryptoContext.Builder setCurrentNftAllowances(List<GrantedNftAllowance> currentNftAllowances) {
			this.currentNftAllowances = CryptoContextUtils.convertToNftMapFromGranted(currentNftAllowances);
			mask |= NFT_ALLOWANCES_MASK;
			return this;
		}
	}

	record AllowanceMapKey(Long tokenNum, Long spenderNum) {
	}

	record AllowanceMapValue(Boolean approvedForAll, List<Long> serialNums) {
	}
}
