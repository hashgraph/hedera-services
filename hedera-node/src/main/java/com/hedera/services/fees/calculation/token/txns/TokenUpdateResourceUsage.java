package com.hedera.services.fees.calculation.token.txns;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.token.TokenTransactUsage;
import com.hedera.services.usage.token.TokenUpdateUsage;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;

import java.util.function.BiFunction;

import static com.hedera.services.fees.calculation.token.queries.GetTokenInfoResourceUsage.ifPresent;
import static com.hedera.services.queries.token.GetTokenInfoAnswer.TOKEN_INFO_CTX_KEY;

public class TokenUpdateResourceUsage implements TxnResourceUsageEstimator {
	static BiFunction<TransactionBody, SigUsage, TokenUpdateUsage> factory = TokenUpdateUsage::newEstimate;

	@Override
	public boolean applicableTo(TransactionBody txn) {
		return txn.hasTokenUpdate();
	}

	@Override
	public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view) throws InvalidTxBodyException {
		var op = txn.getTokenUpdate();
		var sigUsage = new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
		var optionalInfo = view.infoForToken(op.getToken());
		if (optionalInfo.isPresent()) {
			var info = optionalInfo.get();
			var estimate = factory.apply(txn, sigUsage)
					.givenCurrentExpiry(info.getExpiry())
					.givenCurrentAdminKey(ifPresent(info, TokenInfo::hasAdminKey, TokenInfo::getAdminKey))
					.givenCurrentFreezeKey(ifPresent(info, TokenInfo::hasFreezeKey, TokenInfo::getFreezeKey))
					.givenCurrentWipeKey(ifPresent(info, TokenInfo::hasWipeKey, TokenInfo::getWipeKey))
					.givenCurrentSupplyKey(ifPresent(info, TokenInfo::hasSupplyKey, TokenInfo::getSupplyKey))
					.givenCurrentKycKey(ifPresent(info, TokenInfo::hasKycKey, TokenInfo::getKycKey))
					.givenCurrentName(info.getName())
					.givenCurrentSymbol(info.getSymbol());
			if (info.hasAutoRenewAccount()) {
				estimate.givenCurrentlyUsingAutoRenewAccount();
			}
			return estimate.get();
		} else {
			return FeeData.getDefaultInstance();
		}
	}
}
