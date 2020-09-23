package com.hedera.services.usage.token;

/*-
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenBurnUsage extends TokenUsage<TokenBurnUsage> {
	private TokenBurnUsage(TransactionBody tokenBurnOp, TxnUsageEstimator usageEstimator) {
		super(tokenBurnOp, usageEstimator);
	}

	public static TokenBurnUsage newEstimate(TransactionBody tokenBurnOp, SigUsage sigUsage) {
		return new TokenBurnUsage(tokenBurnOp, estimatorFactory.get(sigUsage, tokenBurnOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenBurnUsage self() {
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenBurn();
		addRefBpt(op.getToken());
		addAmountBpt();
		addTransfersRecordRb(1, 1);
		return usageEstimator.get();
	}
}
