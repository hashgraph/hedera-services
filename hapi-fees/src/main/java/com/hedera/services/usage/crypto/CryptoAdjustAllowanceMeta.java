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
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Map;

import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToCryptoMapFromGranted;
import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToNftMapFromGranted;
import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToTokenMapFromGranted;
import static com.hedera.services.usage.crypto.CryptoContextUtils.countSerials;
import static com.hederahashgraph.fee.FeeBuilder.CRYPTO_ALLOWANCE_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.NFT_ALLOWANCE_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.TOKEN_ALLOWANCE_SIZE;

/**
 * Metadata for CryptoAdjustAllowance
 */
public class CryptoAdjustAllowanceMeta {
	private final long effectiveNow;
	private final long msgBytesUsed;
	private final Map<Long, Long> cryptoAllowances;
	private final Map<ExtantCryptoContext.AllowanceMapKey, Long> tokenAllowances;
	private final Map<ExtantCryptoContext.AllowanceMapKey, ExtantCryptoContext.AllowanceMapValue> nftAllowances;

	public CryptoAdjustAllowanceMeta(Builder builder) {
		effectiveNow = builder.effectiveNow;
		msgBytesUsed = builder.msgBytesUsed;
		cryptoAllowances = builder.cryptoAllowances;
		tokenAllowances = builder.tokenAllowances;
		nftAllowances = builder.nftAllowances;
	}

	public CryptoAdjustAllowanceMeta(CryptoAdjustAllowanceTransactionBody cryptoAdjustTxnBody,
			long transactionValidStartSecs) {
		effectiveNow = transactionValidStartSecs;
		msgBytesUsed = bytesUsedInTxn(cryptoAdjustTxnBody);
		cryptoAllowances = CryptoContextUtils.convertToCryptoMap(cryptoAdjustTxnBody.getCryptoAllowancesList());
		tokenAllowances = CryptoContextUtils.convertToTokenMap(cryptoAdjustTxnBody.getTokenAllowancesList());
		nftAllowances = CryptoContextUtils.convertToNftMap(cryptoAdjustTxnBody.getNftAllowancesList());
	}

	private int bytesUsedInTxn(CryptoAdjustAllowanceTransactionBody op) {
		return op.getCryptoAllowancesCount() * CRYPTO_ALLOWANCE_SIZE
				+ op.getTokenAllowancesCount() * TOKEN_ALLOWANCE_SIZE
				+ op.getNftAllowancesCount() * NFT_ALLOWANCE_SIZE
				+ countSerials(op.getNftAllowancesList()) * LONG_SIZE;
	}

	public static Builder newBuilder() {
		return new CryptoAdjustAllowanceMeta.Builder();
	}

	public long getEffectiveNow() {
		return effectiveNow;
	}

	public long getMsgBytesUsed() {
		return msgBytesUsed;
	}

	public Map<Long, Long> getCryptoAllowances() {
		return cryptoAllowances;
	}

	public Map<ExtantCryptoContext.AllowanceMapKey, Long> getTokenAllowances() {
		return tokenAllowances;
	}

	public Map<ExtantCryptoContext.AllowanceMapKey, ExtantCryptoContext.AllowanceMapValue> getNftAllowances() {
		return nftAllowances;
	}

	public static class Builder {
		private long effectiveNow;
		private long msgBytesUsed;
		private Map<Long, Long> cryptoAllowances;
		private Map<ExtantCryptoContext.AllowanceMapKey, Long> tokenAllowances;
		private Map<ExtantCryptoContext.AllowanceMapKey, ExtantCryptoContext.AllowanceMapValue> nftAllowances;

		public CryptoAdjustAllowanceMeta.Builder cryptoAllowances(Map<Long, Long> cryptoAllowances) {
			this.cryptoAllowances = cryptoAllowances;
			return this;
		}

		public CryptoAdjustAllowanceMeta.Builder tokenAllowances(
				Map<ExtantCryptoContext.AllowanceMapKey, Long> tokenAllowances) {
			this.tokenAllowances = tokenAllowances;
			return this;
		}

		public CryptoAdjustAllowanceMeta.Builder nftAllowances(
				Map<ExtantCryptoContext.AllowanceMapKey, ExtantCryptoContext.AllowanceMapValue> nftAllowances) {
			this.nftAllowances = nftAllowances;
			return this;
		}

		public CryptoAdjustAllowanceMeta.Builder effectiveNow(long now) {
			this.effectiveNow = now;
			return this;
		}

		public CryptoAdjustAllowanceMeta.Builder msgBytesUsed(long msgBytesUsed) {
			this.msgBytesUsed = msgBytesUsed;
			return this;
		}

		public Builder() {
			// empty here on purpose.
		}

		public CryptoAdjustAllowanceMeta build() {
			return new CryptoAdjustAllowanceMeta(this);
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
				.add("effectiveNow", effectiveNow)
				.add("msgBytesUsed", msgBytesUsed)
				.toString();
	}
}
