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

import com.hederahashgraph.api.proto.java.TokenBurnCoins;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class TokenBurnFactory extends SignedTxnFactory<TokenBurnFactory> {
	private TokenBurnFactory() {}

	private TokenRef ref;

	public static TokenBurnFactory newSignedTokenBurn() {
		return new TokenBurnFactory();
	}

	public TokenBurnFactory burning(TokenRef ref) {
		this.ref = ref;
		return this;
	}

	@Override
	protected TokenBurnFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		var op = TokenBurnCoins.newBuilder()
				.setToken(ref);
		txn.setTokenBurn(op);
	}
}
