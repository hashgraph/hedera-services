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

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

public class CryptoApproveAllowanceMeta {
	private final List<CryptoAllowance> cryptoAllowances;
	private final List<TokenAllowance> tokenAllowances;
	private final List<NftAllowance> nftAllowances;

	public CryptoApproveAllowanceMeta(Builder builder) {
		cryptoAllowances = builder.getCryptoAllowances();
		tokenAllowances = builder.getTokenAllowances();
		nftAllowances = builder.getNftAllowances();
	}

	public CryptoApproveAllowanceMeta(CryptoApproveAllowanceTransactionBody cryptoApproveTxnBody) {
		cryptoAllowances = cryptoApproveTxnBody.getCryptoAllowancesList();
		tokenAllowances = cryptoApproveTxnBody.getTokenAllowancesList();
		nftAllowances = cryptoApproveTxnBody.getNftAllowancesList();
	}

	private int bytesUsedInTxn(CryptoUpdateTransactionBody op) {
		return BASIC_ENTITY_ID_SIZE
				+ op.getMemo().getValueBytes().size()
				+ (op.hasExpirationTime() ? LONG_SIZE : 0)
				+ (op.hasAutoRenewPeriod() ? LONG_SIZE : 0)
				+ (op.hasProxyAccountID() ? BASIC_ENTITY_ID_SIZE : 0)
				+ (op.hasMaxAutomaticTokenAssociations() ? INT_SIZE : 0);
	}

	public static class Builder {
		private List<CryptoAllowance> cryptoAllowances;
		private List<TokenAllowance> tokenAllowances;
		private List<NftAllowance> nftAllowances;

		public Builder() {
			// empty here on purpose.
		}

		public List<CryptoAllowance> getCryptoAllowances() {
			return cryptoAllowances;
		}

		public void setCryptoAllowances(final List<CryptoAllowance> cryptoAllowances) {
			this.cryptoAllowances = cryptoAllowances;
		}

		public List<TokenAllowance> getTokenAllowances() {
			return tokenAllowances;
		}

		public void setTokenAllowances(final List<TokenAllowance> tokenAllowances) {
			this.tokenAllowances = tokenAllowances;
		}

		public List<NftAllowance> getNftAllowances() {
			return nftAllowances;
		}

		public void setNftAllowances(final List<NftAllowance> nftAllowances) {
			this.nftAllowances = nftAllowances;
		}

		public CryptoApproveAllowanceMeta build() {
			return new CryptoApproveAllowanceMeta(this);
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
				.add("cryptoAllowances", cryptoAllowances)
				.add("tokenAllowances", tokenAllowances)
				.add("nftAllowances", nftAllowances)
				.toString();
	}
}
