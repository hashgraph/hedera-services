package com.hedera.test.factories.txns;

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

import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.Optional;

public class TokenCreateFactory extends SignedTxnFactory<TokenCreateFactory> {
	private boolean frozen = false;
	private boolean omitAdmin = false;
	private boolean omitTreasury = false;
	private Optional<AccountID> autoRenew = Optional.empty();

	private TokenCreateFactory() {}

	public static TokenCreateFactory newSignedTokenCreate() {
		return new TokenCreateFactory();
	}

	public TokenCreateFactory frozen() {
		frozen = true;
		return this;
	}

	public TokenCreateFactory autoRenew(AccountID account) {
		autoRenew = Optional.of(account);
		return this;
	}

	public TokenCreateFactory missingTreasury() {
		omitTreasury = true;
		return this;
	}

	public TokenCreateFactory missingAdmin() {
		omitAdmin = true;
		return this;
	}

	@Override
	protected TokenCreateFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		var op = TokenCreateTransactionBody.newBuilder();
		if (!omitAdmin) {
			op.setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey());
		}
		if (!omitTreasury) {
			op.setTreasury(TxnHandlingScenario.TOKEN_TREASURY);
		}
		if (frozen) {
			op.setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey());
		}
		autoRenew.ifPresent(a -> op.setAutoRenewAccount(a));
		txn.setTokenCreation(op);
	}
}
