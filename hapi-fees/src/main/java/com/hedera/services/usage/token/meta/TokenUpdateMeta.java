package com.hedera.services.usage.token.meta;

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

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class TokenUpdateMeta {
	private final int newSymLen;
	private final int newNameLen;
	private final int newMemoLen;
	private final int newKeysLen;
	private final boolean hasTreasury;
	private final boolean removeAutoRenewAccount;
	private final boolean hasAutoRenewAccount;
	private final long newExpiry;
	private long newEffectiveTxnStartTime;
	private final long newAutoRenewPeriod;

	private TokenUpdateMeta(TokenUpdateMeta.Builder builder) {
		this.newSymLen = builder.newSymLen;
		this.newNameLen = builder.newNameLen;
		this.newMemoLen = builder.newMemoLen;
		this.newKeysLen = builder.newKeysLen;
		this.newExpiry = builder.newExpiry;
		this.removeAutoRenewAccount = builder.removeAutoRenewAccount;
		this.hasAutoRenewAccount = builder.hasAutoRenewAccount;
		this.hasTreasury = builder.hasTreasure;
		this.newAutoRenewPeriod = builder.newAutoRenewPeriod;
		this.newEffectiveTxnStartTime = builder.newEffectiveTxnStartTime;
	}

	public int getNewSymLen() {
		return newSymLen;
	}
	public int getNewNameLen() {
		return newNameLen;
	}
	public int getNewMemoLen() {
		return newMemoLen;
	}
	public int getNewKeysLen() {
		return newKeysLen;
	}
	public long getNewExpiry() {
		return newExpiry;
	}

	public long getNewEffectiveTxnStartTime() {
		return newEffectiveTxnStartTime;
	}
	public long getNewAutoRenewPeriod() {
		return newAutoRenewPeriod;
	}
	public boolean getRemoveAutoRenewAccount() {
		return removeAutoRenewAccount;
	}
	public boolean hasAutoRenewAccount() {
		return hasAutoRenewAccount;
	}
	public boolean hasTreasury() {
		return hasTreasury;
	}

	public static TokenUpdateMeta.Builder newBuilder() {
		return new TokenUpdateMeta.Builder();
	}

	public static class Builder {
		private int newSymLen;
		private int newNameLen;
		private int newMemoLen;
		private boolean removeAutoRenewAccount;
		private boolean hasAutoRenewAccount;
		private long newExpiry;
		private long newEffectiveTxnStartTime;
		private long newAutoRenewPeriod;

		private int newKeysLen;

		private boolean hasTreasure;

		private Builder() {
		}
		public TokenUpdateMeta.Builder newBuilder() {
			return new TokenUpdateMeta.Builder();
		}


		public TokenUpdateMeta build() {
			return new TokenUpdateMeta(this);
		}

		public TokenUpdateMeta.Builder setNewSymLen(final int newSymLen) {
			this.newSymLen = newSymLen;
			return this;
		}
		public TokenUpdateMeta.Builder setNewNameLen(final int newNameLen) {
			this.newNameLen = newNameLen;
			return this;
		}

		public TokenUpdateMeta.Builder setNewMemoLen(final int newMemoLen) {
			this.newMemoLen = newMemoLen;
			return this;
		}

		public TokenUpdateMeta.Builder setNewKeysLen(final int newKeysLen) {
			this.newKeysLen = newKeysLen;
			return this;
		}

		public TokenUpdateMeta.Builder setNewExpiry(final long newExpiry) {
			this.newExpiry = newExpiry;
			return this;
		}

		public TokenUpdateMeta.Builder setNewEffectiveTxnStartTime(final long newEffectiveTxnStartTime) {
			this.newEffectiveTxnStartTime = newEffectiveTxnStartTime;
			return this;
		}

		public TokenUpdateMeta.Builder setNewAutoRenewPeriod(final long newAutoRenewPeriod) {
			this.newAutoRenewPeriod = newAutoRenewPeriod;
			return this;
		}

		public TokenUpdateMeta.Builder setRemoveAutoRenewAccount(final boolean removeAutoRenewAccount) {
			this.removeAutoRenewAccount = removeAutoRenewAccount;
			return this;
		}
		public TokenUpdateMeta.Builder setHasAutoRenewAccount(final boolean hasAutoRenewAccount) {
			this.hasAutoRenewAccount = hasAutoRenewAccount;
			return this;
		}

		public TokenUpdateMeta.Builder setHasTreasure(final boolean hasTreasure) {
			this.hasTreasure = hasTreasure;
			return this;
		}
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
				.add("newNameLen", newNameLen)
				.add("newSymLen", newSymLen)
				.add("newMemoLen", newMemoLen)
				.add("newKeysLen", newKeysLen)
				.add("newExpiry", newExpiry)
				.add("newAuroRenewPeriod", newAutoRenewPeriod)
				.add("removeAutoRenewAccount", removeAutoRenewAccount)
				.add("hasAutoRenewAccount", hasAutoRenewAccount)
				.add("hasTreasure", hasTreasury)
				.toString();
	}
}
