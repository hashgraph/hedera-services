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

	private final int newAdminKeyLen;
	private final int newKycKeyLen;
	private final int newFreezeKeyLen;
	private final int newWipeKeyLen;
	private final int newSupplyKeyLen;
	private final int newFeeScheduleKeyLen;

	private final int newKeysLen;

	private final boolean hasTreasure;
	private final boolean removeAutoRenewAccount;
	private final boolean hasAutoRenewAccount;
	private final long newExpiry;

	private final long newEffectiveLifeTime;
	private long newEffectiveTxnStartTime;
	private final long newAutoRenewPeriod;

	private TokenUpdateMeta(TokenUpdateMeta.Builder builder) {
		this.newSymLen = builder.newSymLen;
		this.newNameLen = builder.newNameLen;
		this.newMemoLen = builder.newMemoLen;

		this.newAdminKeyLen = builder.newAdminKeyLen;
		this.newKycKeyLen = builder.newKycKeyLen;
		this.newFreezeKeyLen = builder.newFreezeKeyLen;
		this.newWipeKeyLen = builder.newWipeKeyLen;
		this.newSupplyKeyLen = builder.newSupplyKeyLen;
		this.newFeeScheduleKeyLen = builder.newFeeScheduleKeyLen;

		this.newKeysLen = builder.newKeysLen;

		this.newExpiry = builder.newExpiry;
		this.removeAutoRenewAccount = builder.removeAutoRenewAccount;
		this.hasAutoRenewAccount = builder.hasAutoRenewAccount;
		this.hasTreasure = builder.hasTreasure;
		this.newAutoRenewPeriod = builder.newAutoRenewPeriod;
		this.newEffectiveLifeTime = builder.newEffectiveLifeTime;
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
	public int getNewAdminKeyLen() {
		return newAdminKeyLen;
	}
	public int getNewKeysLen() {
		return newKeysLen;
	}


	public int getNewKycKeyLen() {
		return newKycKeyLen;
	}
	public int getNewWipeKeyLen() {
		return newWipeKeyLen;
	}
	public int getNewFeeScheduleKeyLen() {
		return newFeeScheduleKeyLen;
	}
	public int getNewFreezeKeyLen() {
		return newFreezeKeyLen;
	}
	public long getNewSupplyKeyLen() {
		return newSupplyKeyLen;
	}

	public long getNewExpiry() {
		return newExpiry;
	}

	public long getNewEffectiveLifeTime() {
		return newEffectiveLifeTime;
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
	public boolean hasTreasure() {
		return hasTreasure;
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
		private long newEffectiveLifeTime;
		private long newEffectiveTxnStartTime;
		private long newAutoRenewPeriod;

		private int newKeysLen;

		private int newAdminKeyLen;
		private int newKycKeyLen;
		private int newFreezeKeyLen;
		private int newWipeKeyLen;
		private int newSupplyKeyLen;
		private int newFeeScheduleKeyLen;

		private boolean hasTreasure;

		// builder
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

		public TokenUpdateMeta.Builder setNewEffectiveLifeTime(final long newEffectiveLifeTime) {
			this.newEffectiveLifeTime = newEffectiveLifeTime;
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

		public TokenUpdateMeta.Builder setNewAdminKeyLen(final int newAdminKeyLen) {
			this.newAdminKeyLen = newAdminKeyLen;
			return this;
		}
		public TokenUpdateMeta.Builder setNewKycKeyLen(final int newKycKeyLen) {
			this.newKycKeyLen = newKycKeyLen;
			return this;
		}
		public TokenUpdateMeta.Builder setNewFreezeKeyLen(final int newFreezeKeyLen) {
			this.newFreezeKeyLen = newFreezeKeyLen;
			return this;
		}
		public TokenUpdateMeta.Builder setNewWipeKeyLen(final int newWipeKeyLen) {
			this.newWipeKeyLen = newWipeKeyLen;
			return this;
		}
		public TokenUpdateMeta.Builder setNewSupplyKeyLen(final int newSupplyKeyLen) {
			this.newSupplyKeyLen = newSupplyKeyLen;
			return this;
		}
		public TokenUpdateMeta.Builder setNewFeeScheduleKeyLen(final int newFeeScheduleKeyLen) {
			this.newFeeScheduleKeyLen = newFeeScheduleKeyLen;
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

				.add("newAdminKeyLen", newAdminKeyLen)
				.add("newKycKeyLen", newKycKeyLen)
				.add("newFreezeKeyLen", newFreezeKeyLen)
				.add("newWipeKeyLen", newWipeKeyLen)
				.add("newSupplyKeyLen", newSupplyKeyLen)
				.add("newFeeScheduleKeyLen", newFeeScheduleKeyLen)

				.add("newExpiry", newExpiry)
				.add("newAuroRenewPeriod", newAutoRenewPeriod)
				.add("newEffectiveLifeTime", newEffectiveLifeTime)
				.add("removeAutoRenewAccount", removeAutoRenewAccount)
				.add("hasAutoRenewAccount", hasAutoRenewAccount)
				.add("hasTreasure", hasTreasure)
				.toString();
	}
}
