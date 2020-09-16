package com.hedera.test.factories.txns;

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

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TokenRefTransferList;
import com.hederahashgraph.api.proto.java.TokenTransfers;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TokenTransactFactory extends SignedTxnFactory<TokenTransactFactory> {
	Map<TokenID, List<AccountAmount>> adjustments = new HashMap<>();

	private TokenTransfers.Builder xfers = TokenTransfers.newBuilder();

	private TokenTransactFactory() {}

	public static TokenTransactFactory newSignedTokenTransact() {
		return new TokenTransactFactory();
	}

	public TokenTransactFactory adjusting(AccountID aId, TokenID tId, long amount) {
		adjustments.computeIfAbsent(tId, ignore -> new ArrayList<>())
				.add(AccountAmount.newBuilder()
						.setAccountID(aId)
						.setAmount(amount)
						.build());
		return this;
	}

	@Override
	protected TokenTransactFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		adjustments.entrySet().stream()
				.forEach(entry -> xfers.addTokenTransfers(TokenRefTransferList.newBuilder()
						.setToken(TokenRef.newBuilder().setTokenId(entry.getKey()).build())
						.addAllTransfers(entry.getValue())
						.build()));
		txn.setTokenTransfers(xfers);
	}
}
