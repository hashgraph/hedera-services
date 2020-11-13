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
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public class TokenCreateUsage extends TokenTxnUsage<TokenCreateUsage> {
	private TokenCreateUsage(TransactionBody tokenCreationOp, TxnUsageEstimator usageEstimator) {
		super(tokenCreationOp, usageEstimator);
	}

	public static TokenCreateUsage newEstimate(TransactionBody tokenCreationOp, SigUsage sigUsage) {
		return new TokenCreateUsage(tokenCreationOp, estimatorFactory.get(sigUsage, tokenCreationOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenCreateUsage self() {
		return this;
	}

	public FeeData get() {
		var op = this.op.getTokenCreation();

		var baseSize = tokenEntitySizes.totalBytesInfTokenReprGiven(op.getSymbol(), op.getName());
		baseSize += keySizeIfPresent(op, TokenCreateTransactionBody::hasKycKey, TokenCreateTransactionBody::getKycKey);
		baseSize += keySizeIfPresent(op, TokenCreateTransactionBody::hasWipeKey, TokenCreateTransactionBody::getWipeKey);
		baseSize += keySizeIfPresent(op, TokenCreateTransactionBody::hasAdminKey, TokenCreateTransactionBody::getAdminKey);
		baseSize += keySizeIfPresent(op, TokenCreateTransactionBody::hasSupplyKey, TokenCreateTransactionBody::getSupplyKey);
		baseSize += keySizeIfPresent(op, TokenCreateTransactionBody::hasFreezeKey, TokenCreateTransactionBody::getFreezeKey);
		if (op.hasAutoRenewAccount()) {
			baseSize += BASIC_ENTITY_ID_SIZE;
		}
		var lifetime = op.hasAutoRenewAccount()
				? op.getAutoRenewPeriod().getSeconds()
				: ESTIMATOR_UTILS.relativeLifetime(this.op, op.getExpiry().getSeconds());

		usageEstimator.addBpt(baseSize);
		usageEstimator.addRbs(baseSize * lifetime);
		addNetworkRecordRb(BASIC_ENTITY_ID_SIZE);
		addTokenTransfersRecordRb(1, 1);

		return usageEstimator.get();
	}
}
