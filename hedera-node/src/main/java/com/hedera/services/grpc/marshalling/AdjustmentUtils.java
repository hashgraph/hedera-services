package com.hedera.services.grpc.marshalling;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.Id;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;

public class AdjustmentUtils {
	static void adjust(Id account, Id collector, Id denom, long amount, BalanceChangeManager changeManager) {
		final var payerChange = adjustedChange(account, denom, -amount, changeManager);
		payerChange.setCodeForInsufficientBalance(INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE);
		adjustedChange(collector, denom, +amount, changeManager);
	}

	private static BalanceChange adjustedChange(Id account, Id denom, long amount, BalanceChangeManager changeManager) {
		final var extantChange = changeManager.changeFor(account, denom);
		if (extantChange == null) {
			if (denom == Id.MISSING_ID) {
				final var newHbarChange = BalanceChange.hbarAdjust(account, amount);
				changeManager.includeChange(newHbarChange);
				return newHbarChange;
			} else {
				throw new AssertionError("Not implemented!");
			}
		} else {
			extantChange.adjustUnits(amount);
			return extantChange;
		}
	}
}
