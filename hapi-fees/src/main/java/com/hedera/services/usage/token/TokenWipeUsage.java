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
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

public class TokenWipeUsage extends TokenTxnUsage<TokenWipeUsage> {

	private SubType currentSubType;

	private TokenWipeUsage(TransactionBody tokenWipeOp, TxnUsageEstimator usageEstimator) {
		super(tokenWipeOp, usageEstimator);
	}

	public static TokenWipeUsage newEstimate(TransactionBody tokenWipeOp, SigUsage sigUsage) {
		return new TokenWipeUsage(tokenWipeOp, estimatorFactory.get(sigUsage, tokenWipeOp, ESTIMATOR_UTILS));
	}

	public TokenWipeUsage givenSubType(SubType subType) {
		this.currentSubType = subType;
		return this;
	}

	@Override
	TokenWipeUsage self() {
		return this;
	}

	public FeeData get() {
		var op = this.op.getTokenWipe();
		if (currentSubType == SubType.TOKEN_NON_FUNGIBLE_UNIQUE) {
			usageEstimator.addBpt((long) op.getSerialNumbersCount() * LONG_SIZE);
			addTokenTransfersRecordRb(1, 0, op.getSerialNumbersCount());
		} else if (currentSubType == SubType.TOKEN_FUNGIBLE_COMMON) {
			addAmountBpt();
			addTokenTransfersRecordRb(1, 1, 0);
		}
		addEntityBpt();
		return usageEstimator.get(currentSubType);
	}
}
