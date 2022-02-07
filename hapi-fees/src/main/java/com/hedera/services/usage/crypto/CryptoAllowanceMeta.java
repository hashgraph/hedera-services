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
import com.hederahashgraph.api.proto.java.CryptoAdjustAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;

import static com.hederahashgraph.fee.FeeBuilder.CRYPTO_ALLOWANCE_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.NFT_ALLOWANCE_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.TOKEN_ALLOWANCE_SIZE;

/**
 * Metadata for both CryptoApproveAllowance and CryptoAdjustAllowance
 */
public class CryptoAllowanceMeta {
	private int numOfCryptoAllowances;
	private int numOfTokenAllowances;
	private int numOfNftAllowances;
	private int aggregatedNftAllowancesWithSerials;
	private final long effectiveNow;
	private final long msgBytesUsed;

	public CryptoAllowanceMeta(Builder builder) {
		numOfCryptoAllowances = builder.numOfCryptoAllowances;
		numOfTokenAllowances = builder.numOfTokenAllowances;
		numOfNftAllowances = builder.numOfNftAllowances;
		aggregatedNftAllowancesWithSerials = builder.aggregatedNftAllowancesWithSerials;
		effectiveNow = builder.effectiveNow;
		msgBytesUsed = builder.msgBytesUsed;
	}

	public CryptoAllowanceMeta(CryptoApproveAllowanceTransactionBody cryptoApproveTxnBody,
			long transactionValidStartSecs) {
		numOfCryptoAllowances = cryptoApproveTxnBody.getCryptoAllowancesCount();
		numOfTokenAllowances = cryptoApproveTxnBody.getTokenAllowancesCount();
		numOfNftAllowances = cryptoApproveTxnBody.getNftAllowancesCount();
		aggregatedNftAllowancesWithSerials = countSerials(cryptoApproveTxnBody.getNftAllowancesList());
		effectiveNow = transactionValidStartSecs;
		msgBytesUsed = bytesUsedInTxn(cryptoApproveTxnBody);
	}

	public CryptoAllowanceMeta(CryptoAdjustAllowanceTransactionBody cryptoAdjustTxnBody,
			long transactionValidStartSecs) {
		numOfCryptoAllowances = cryptoAdjustTxnBody.getCryptoAllowancesCount();
		numOfTokenAllowances = cryptoAdjustTxnBody.getTokenAllowancesCount();
		numOfNftAllowances = cryptoAdjustTxnBody.getNftAllowancesCount();
		aggregatedNftAllowancesWithSerials = countSerials(cryptoAdjustTxnBody.getNftAllowancesList());
		effectiveNow = transactionValidStartSecs;
		msgBytesUsed = bytesUsedInTxn(cryptoAdjustTxnBody);
	}

	private int bytesUsedInTxn(CryptoAdjustAllowanceTransactionBody op) {
		return op.getCryptoAllowancesCount() * CRYPTO_ALLOWANCE_SIZE
				+ op.getTokenAllowancesCount() * TOKEN_ALLOWANCE_SIZE
				+ op.getNftAllowancesCount() * NFT_ALLOWANCE_SIZE;
	}

	private int bytesUsedInTxn(CryptoApproveAllowanceTransactionBody op) {
		return op.getCryptoAllowancesCount() * CRYPTO_ALLOWANCE_SIZE
				+ op.getTokenAllowancesCount() * TOKEN_ALLOWANCE_SIZE
				+ op.getNftAllowancesCount() * NFT_ALLOWANCE_SIZE;
	}

	public static Builder newBuilder() {
		return new CryptoAllowanceMeta.Builder();
	}

	public int getNumOfCryptoAllowances() {
		return numOfCryptoAllowances;
	}

	public int getNumOfTokenAllowances() {
		return numOfTokenAllowances;
	}

	public int getNumOfNftAllowances() {
		return numOfNftAllowances;
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
		private int numOfCryptoAllowances;
		private int numOfTokenAllowances;
		private int numOfNftAllowances;
		private int aggregatedNftAllowancesWithSerials;
		private long effectiveNow;
		private long msgBytesUsed;

		public CryptoAllowanceMeta.Builder numOfCryptoAllowances(int numOfCryptoAllowances) {
			this.numOfCryptoAllowances = numOfCryptoAllowances;
			return this;
		}

		public CryptoAllowanceMeta.Builder numOfTokenAllowances(int numOfTokenAllowances) {
			this.numOfTokenAllowances = numOfTokenAllowances;
			return this;
		}

		public CryptoAllowanceMeta.Builder numOfNftAllowances(int numOfNftAllowances) {
			this.numOfNftAllowances = numOfNftAllowances;
			return this;
		}

		public CryptoAllowanceMeta.Builder aggregatedNftAllowancesWithSerials(
				int aggregatedNftAllowancesWithSerials) {
			this.aggregatedNftAllowancesWithSerials = aggregatedNftAllowancesWithSerials;
			return this;
		}

		public CryptoAllowanceMeta.Builder effectiveNow(long now) {
			this.effectiveNow = now;
			return this;
		}

		public CryptoAllowanceMeta.Builder msgBytesUsed(long msgBytesUsed) {
			this.msgBytesUsed = msgBytesUsed;
			return this;
		}

		public Builder() {
			// empty here on purpose.
		}

		public CryptoAllowanceMeta build() {
			return new CryptoAllowanceMeta(this);
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
				.add("numOfCryptoAllowances", numOfCryptoAllowances)
				.add("numOfTokenAllowances", numOfTokenAllowances)
				.add("numOfNftAllowances", numOfNftAllowances)
				.add("aggregatedNftAllowancesWithSerials", aggregatedNftAllowancesWithSerials)
				.add("effectiveNow", effectiveNow)
				.add("msgBytesUsed", msgBytesUsed)
				.toString();
	}

	public static int countSerials(final List<NftAllowance> nftAllowancesList) {
		int totalSerials = 0;
		for (var allowance : nftAllowancesList) {
			totalSerials += allowance.getSerialNumbersCount();
		}
		return totalSerials;
	}
}
