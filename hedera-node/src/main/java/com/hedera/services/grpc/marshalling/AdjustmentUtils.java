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
	static void adjustForAssessed(Id payer, Id collector, Id denom, long amount, BalanceChangeManager manager) {
		final var payerChange = adjustedChange(payer, denom, -amount, manager, false);
		payerChange.setCodeForInsufficientBalance(INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE);
		adjustedChange(collector, denom, +amount, manager, false);
	}

	static BalanceChange adjustedChange(Id account, Id denom, long amount, BalanceChangeManager manager, boolean changeSender) {
		/* Always append a new change for an HTS debit since it could trigger another assessed fee */
		if (denom != Id.MISSING_ID && amount < 0 && !changeSender) {
			return includedHtsChange(account, denom, amount, manager);
		}

		/* Otherwise, just update the existing change for this account denomination if present */
		final var extantChange = manager.changeFor(account, denom);
		if (extantChange == null) {
			if (denom == Id.MISSING_ID) {
				final var newHbarChange = BalanceChange.hbarAdjust(account, amount);
				manager.includeChange(newHbarChange);
				return newHbarChange;
			} else {
				return includedHtsChange(account, denom, amount, manager);
			}
		} else {
			extantChange.adjustUnits(amount);
			return extantChange;
		}
	}

	private static BalanceChange includedHtsChange(Id account, Id denom, long amount, BalanceChangeManager manager) {
		final var newHtsChange = BalanceChange.tokenAdjust(account, denom, amount);
		manager.includeChange(newHtsChange);
		return newHtsChange;
	}
}
