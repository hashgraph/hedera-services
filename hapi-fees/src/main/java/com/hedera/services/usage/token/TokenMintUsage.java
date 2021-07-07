package com.hedera.services.usage.token;

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

import com.google.protobuf.ByteString;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenMintUsage extends TokenTxnUsage<TokenMintUsage> {
	private long expectedNftLifetime = 0L;

	private SubType currentSubType;

	private TokenMintUsage(TransactionBody tokenMintOp, TxnUsageEstimator usageEstimator) {
		super(tokenMintOp, usageEstimator);
	}

	public TokenMintUsage givenSubType(SubType subType) {
		this.currentSubType = subType;
		return this;
	}

	public TokenMintUsage givenExpectedLifetime(long secs) {
		expectedNftLifetime = secs;
		return this;
	}

	public static TokenMintUsage newEstimate(TransactionBody tokenMintOp, SigUsage sigUsage) {
		return new TokenMintUsage(tokenMintOp, estimatorFactory.get(sigUsage, tokenMintOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenMintUsage self() {
		return this;
	}

	public FeeData get() {
		var op = this.op.getTokenMint();

		if (currentSubType == SubType.TOKEN_NON_FUNGIBLE_UNIQUE) {
			var metadataBytes = 0;
			for (ByteString o : op.getMetadataList()) {
				metadataBytes += o.size();
			}
			usageEstimator.addBpt(metadataBytes);
			usageEstimator.addRbs(expectedNftLifetime * metadataBytes);
			addTokenTransfersRecordRb(1, 0, op.getMetadataCount());
		} else if (currentSubType == SubType.TOKEN_FUNGIBLE_COMMON) {
			addAmountBpt();
			addTokenTransfersRecordRb(1, 1, 0);
		}

		addEntityBpt();
		return usageEstimator.get(currentSubType);
	}
}
