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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TokenTransfer;
import com.hederahashgraph.api.proto.java.TokenTransfers;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class TokenTransactFactory extends SignedTxnFactory<TokenTransactFactory> {
	private TokenTransfers.Builder xfers = TokenTransfers.newBuilder();

	private TokenTransactFactory() {
	}

	public static TokenTransactFactory newSignedTokenTransact() {
		return new TokenTransactFactory();
	}

	public TokenTransactFactory adjusting(AccountID aId, TokenID tId, long amount) {
		xfers.addTransfers(TokenTransfer.newBuilder()
				.setAccount(aId)
				.setToken(TokenRef.newBuilder().setTokenId(tId))
				.setAmount(amount));
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
		txn.setTokenTransfers(xfers);
	}
}
