package com.hedera.services.utils;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */


import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;

import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;

public class GrpcTransfersBuilder {

	public static void includeTransfer(AccountID account, long amount, TransferList.Builder xfers) {
		int loc = 0, diff = -1;
		var soFar = xfers.getAccountAmountsBuilderList();
		for (; loc < soFar.size(); loc++) {
			diff = ACCOUNT_ID_COMPARATOR.compare(account, soFar.get(loc).getAccountID());
			if (diff <= 0) {
				break;
			}
		}
		if (diff == 0) {
			var aa = soFar.get(loc);
			long current = aa.getAmount();
			aa.setAmount(current + amount);
		} else {
			if (loc == soFar.size()) {
				xfers.addAccountAmounts(aaBuilderWith(account, amount));
			} else {
				xfers.addAccountAmounts(loc, aaBuilderWith(account, amount));
			}
		}
	}

	private static AccountAmount.Builder aaBuilderWith(AccountID account, long amount) {
		return AccountAmount.newBuilder().setAccountID(account).setAmount(amount);
	}
}
