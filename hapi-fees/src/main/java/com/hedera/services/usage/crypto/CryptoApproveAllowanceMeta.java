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
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import static com.hedera.services.usage.crypto.CryptoContextUtils.countSerials;
import static com.hederahashgraph.fee.FeeBuilder.CRYPTO_ALLOWANCE_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.NFT_ALLOWANCE_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.TOKEN_ALLOWANCE_SIZE;

/**
 * Metadata for CryptoApproveAllowance
 */
public class CryptoApproveAllowanceMeta {
	private int aggregatedNftAllowancesWithSerials;
	private final long effectiveNow;
	private final long msgBytesUsed;

	public CryptoApproveAllowanceMeta(Builder builder) {
		aggregatedNftAllowancesWithSerials = builder.aggregatedNftAllowancesWithSerials;
		effectiveNow = builder.effectiveNow;
		msgBytesUsed = builder.msgBytesUsed;
	}

	public CryptoApproveAllowanceMeta(CryptoApproveAllowanceTransactionBody cryptoApproveTxnBody,
			long transactionValidStartSecs) {
		aggregatedNftAllowancesWithSerials = countSerials(cryptoApproveTxnBody.getNftAllowancesList());
		effectiveNow = transactionValidStartSecs;
		msgBytesUsed = bytesUsedInTxn(cryptoApproveTxnBody);
	}

	private int bytesUsedInTxn(CryptoApproveAllowanceTransactionBody op) {
		return op.getCryptoAllowancesCount() * CRYPTO_ALLOWANCE_SIZE
				+ op.getTokenAllowancesCount() * TOKEN_ALLOWANCE_SIZE
				+ op.getNftAllowancesCount() * NFT_ALLOWANCE_SIZE
				+ countSerials(op.getNftAllowancesList()) * LONG_SIZE;
	}

	public static Builder newBuilder() {
		return new CryptoApproveAllowanceMeta.Builder();
	}

	public int getAggregatedNftAllowancesWithSerials() {
		return aggregatedNftAllowancesWithSerials;
	}

	public long getEffectiveNow() {
		return effectiveNow;
	}

	public long getMsgBytesUsed() {
		return msgBytesUsed;
	}

	public static class Builder {
		private int aggregatedNftAllowancesWithSerials;
		private long effectiveNow;
		private long msgBytesUsed;

		public CryptoApproveAllowanceMeta.Builder aggregatedNftAllowancesWithSerials(
				int aggregatedNftAllowancesWithSerials) {
			this.aggregatedNftAllowancesWithSerials = aggregatedNftAllowancesWithSerials;
			return this;
		}

		public CryptoApproveAllowanceMeta.Builder effectiveNow(long now) {
			this.effectiveNow = now;
			return this;
		}

		public CryptoApproveAllowanceMeta.Builder msgBytesUsed(long msgBytesUsed) {
			this.msgBytesUsed = msgBytesUsed;
			return this;
		}

		public Builder() {
			// empty here on purpose.
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
				.add("aggregatedNftAllowancesWithSerials", aggregatedNftAllowancesWithSerials)
				.add("effectiveNow", effectiveNow)
				.add("msgBytesUsed", msgBytesUsed)
				.toString();
	}
}
