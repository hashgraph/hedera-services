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

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenBurnUsage extends TokenTxnUsage<TokenBurnUsage> {

	private SubType currentSubType;

	private TokenBurnUsage(TransactionBody tokenBurnOp, TxnUsageEstimator usageEstimator) {
		super(tokenBurnOp, usageEstimator);
	}

	public static TokenBurnUsage newEstimate(TransactionBody tokenBurnOp, SigUsage sigUsage) {
		return new TokenBurnUsage(tokenBurnOp, estimatorFactory.get(sigUsage, tokenBurnOp, ESTIMATOR_UTILS));
	}

	public TokenBurnUsage givenSubType(SubType subType){
		this.currentSubType = subType;
		return this;
	}

	@Override
	TokenBurnUsage self() {
		return this;
	}

	public FeeData get() {
		var op = this.op.getTokenBurn();

		if (currentSubType == SubType.TOKEN_NON_FUNGIBLE_UNIQUE) {
			var bytesToAdd = 0;
			for (Long o : op.getSerialNumbersList()) {
				bytesToAdd += o.byteValue();
			}
			usageEstimator.addBpt(bytesToAdd);
			usageEstimator.addRbs(bytesToAdd);
			var tokenSize = op.getSerialNumbersCount();
			usageEstimator.addRbs(tokenEntitySizes.bytesUsedForUniqueTokenTransfers(tokenSize));
			addTokenTransfersRecordRb(1, 0, tokenSize);
		} else if (currentSubType == SubType.TOKEN_FUNGIBLE_COMMON) {
			addAmountBpt();
			addTokenTransfersRecordRb(1, 1, 0);
		}
		addEntityBpt();
		return usageEstimator.get(currentSubType);
	}
}
