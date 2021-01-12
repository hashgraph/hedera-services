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

public class TokenDissociateUsage extends TokenTxnUsage<TokenDissociateUsage> {
	private TokenDissociateUsage(TransactionBody tokenOp, TxnUsageEstimator usageEstimator) {
		super(tokenOp, usageEstimator);
	}

	public static TokenDissociateUsage newEstimate(TransactionBody tokenOp, SigUsage sigUsage) {
		return new TokenDissociateUsage(tokenOp, estimatorFactory.get(sigUsage, tokenOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenDissociateUsage self() {
		return this;
	}

	public FeeData get() {
		var op = this.op.getTokenDissociate();
		addEntityBpt();
		op.getTokensList().forEach(t -> addEntityBpt());
		return usageEstimator.get();
	}
}
