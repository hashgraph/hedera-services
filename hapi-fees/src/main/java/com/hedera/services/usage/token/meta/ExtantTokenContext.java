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

public class ExtantTokenContext {
	private final int existingSymLen;
	private final int existingNameLen;
	private final int existingMemoLen;
	private final int existingAdminKeyLen;
	private final int existingKycKeyLen;
	private final int existingFreezeKeyLen;
	private final int existingWipeKeyLen;
	private final int nexistingSupplyKeyLen;
	private final int existingFeeScheduleKeyLen;

	private final boolean hasAutoRenewAccount;
	private final long existingExpiry;

	private ExtantTokenContext(ExtantTokenContext.Builder builder) {
		this.existingSymLen = builder.existingSymLen;
		this.existingNameLen = builder.existingNameLen;
		this.existingMemoLen = builder.existingMemoLen;
		this.existingAdminKeyLen = builder.existingAdminKeyLen;
		this.existingKycKeyLen = builder.existingKycKeyLen;
		this.existingFreezeKeyLen = builder.existingFreezeKeyLen;
		this.existingWipeKeyLen = builder.existingWipeKeyLen;
		this.nexistingSupplyKeyLen = builder.existingSupplyKeyLen;
		this.existingFeeScheduleKeyLen = builder.existingFeeScheduleKeyLen;
		this.existingExpiry = builder.existingExpiry;
		this.hasAutoRenewAccount = builder.hasAutoRenewAccount;
	}


	public long getExistingRbSize() {
		return existingNameLen + existingSymLen + existingMemoLen + existingAdminKeyLen
				+ existingKycKeyLen + existingFreezeKeyLen + existingWipeKeyLen
				+ existingFreezeKeyLen;
	}

	public int getExistingSymLen() {
		return existingSymLen;
	}
	public int getExistingNameLen() {
		return existingNameLen;
	}
	public int getExistingMemoLen() {
		return existingMemoLen;
	}
	public int getExistingAdminKeyLen() {
		return existingAdminKeyLen;
	}
	public int getExistingKycKeyLen() {
		return existingKycKeyLen;
	}
	public int getExistingWipeKeyLen() {
		return existingWipeKeyLen;
	}
	public int getExistingFeeScheduleKeyLen() {
		return existingFeeScheduleKeyLen;
	}
	public int getExistingFreezeKeyLen() {
		return existingFreezeKeyLen;
	}
	public long getExistingExpiry() {
		return existingExpiry;
	}

	public boolean getHashasAutoRenewAccount () {
		return hasAutoRenewAccount;
	}

	public static ExtantTokenContext.Builder newBuilder() {
		return new ExtantTokenContext.Builder();
	}

	public static class Builder {
		private int existingSymLen;
		private int existingNameLen;
		private int existingMemoLen;
		private boolean hasAutoRenewAccount;
		private long existingExpiry;
		private int existingAdminKeyLen;
		private int existingKycKeyLen;
		private int existingFreezeKeyLen;
		private int existingWipeKeyLen;
		private int existingSupplyKeyLen;
		private int existingFeeScheduleKeyLen;

		private Builder() {
		}
		public Builder newBuilder() {
			return new Builder();
		}


		public ExtantTokenContext build() {
			return new ExtantTokenContext(this);
		}

		public Builder setExistingSymLen(final int existingSymLen) {
			this.existingSymLen = existingSymLen;
			return this;
		}
		public Builder setExistingNameLen(final int existingNameLen) {
			this.existingNameLen = existingNameLen;
			return this;
		}

		public Builder setExistingMemoLen(final int existingMemoLen) {
			this.existingMemoLen = existingMemoLen;
			return this;
		}

		public Builder setExistingExpiry(final long existingExpiry) {
			this.existingExpiry = existingExpiry;
			return this;
		}

		public Builder setExistingAdminKeyLen(final int existingAdminKeyLen) {
			this.existingAdminKeyLen = existingAdminKeyLen;
			return this;
		}
		public Builder setExistingKycKeyLen(final int existingKycKeyLen) {
			this.existingKycKeyLen = existingKycKeyLen;
			return this;
		}
		public Builder setExistingFreezeKeyLen(final int existingFreezeKeyLen) {
			this.existingFreezeKeyLen = existingFreezeKeyLen;
			return this;
		}
		public Builder setExistingWipeKeyLen(final int existingWipeKeyLen) {
			this.existingWipeKeyLen = existingWipeKeyLen;
			return this;
		}
		public Builder setExistingSupplyKeyLen(final int existingSupplyKeyLen) {
			this.existingSupplyKeyLen = existingSupplyKeyLen;
			return this;
		}
		public Builder setExistingFeeScheduleKeyLen(final int existingFeeScheduleKeyLen) {
			this.existingFeeScheduleKeyLen = existingFeeScheduleKeyLen;
			return this;
		}
		public Builder setHasAutoRenewalAccount(final boolean hasAutoRenewalAccount) {
			this.hasAutoRenewAccount = hasAutoRenewalAccount;
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
				.add("existingNameLen", existingNameLen)
				.add("existingSymLen", existingSymLen)
				.add("existingMemoLen", existingMemoLen)
				.add("existingAdminKeyLen", existingAdminKeyLen)
				.add("existingKycKeyLen", existingKycKeyLen)
				.add("existingFreezeKeyLen", existingFreezeKeyLen)
				.add("existingWipeKeyLen", existingWipeKeyLen)
				.add("nexistingSupplyKeyLen", nexistingSupplyKeyLen)
				.add("existingFeeScheduleKeyLen", existingFeeScheduleKeyLen)
				.add("existingExpiry", existingExpiry)
				.add("hasAutoRenewAccount", hasAutoRenewAccount)
				.toString();
	}
}
