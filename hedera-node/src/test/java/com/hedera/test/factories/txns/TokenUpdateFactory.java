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

import com.hedera.test.factories.keys.KeyTree;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenManagement;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.Optional;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_REPLACE_KT;

public class TokenUpdateFactory extends SignedTxnFactory<TokenUpdateFactory> {
	private TokenRef ref;
	private Optional<KeyTree> newAdminKt = Optional.empty();
	private Optional<AccountID> newAutoRenew = Optional.empty();
	private boolean replaceFreeze, replaceSupply, replaceWipe, replaceKyc;

	private TokenUpdateFactory() { }

	public static TokenUpdateFactory newSignedTokenUpdate() {
		return new TokenUpdateFactory();
	}

	public TokenUpdateFactory updating(TokenRef ref) {
		this.ref = ref;
		return this;
	}

	public TokenUpdateFactory newAdmin(KeyTree kt) {
		newAdminKt = Optional.of(kt);
		return this;
	}

	public TokenUpdateFactory newAutoRenew(AccountID account) {
		newAutoRenew = Optional.of(account);
		return this;
	}

	public TokenUpdateFactory replacingFreeze() {
		replaceFreeze = true;
		return this;
	}

	public TokenUpdateFactory replacingSupply() {
		replaceSupply = true;
		return this;
	}

	public TokenUpdateFactory replacingWipe() {
		replaceWipe = true;
		return this;
	}

	public TokenUpdateFactory replacingKyc() {
		replaceKyc = true;
		return this;
	}

	@Override
	protected TokenUpdateFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		var op = TokenManagement.newBuilder();
		op.setToken(ref);
		newAdminKt.ifPresent(kt -> op.setAdminKey(kt.asKey()));
		if (replaceFreeze) {
			op.setFreezeKey(TOKEN_REPLACE_KT.asKey());
		}
		if (replaceKyc) {
			op.setKycKey(TOKEN_REPLACE_KT.asKey());
		}
		if (replaceSupply) {
			op.setSupplyKey(TOKEN_REPLACE_KT.asKey());
		}
		if (replaceWipe) {
			op.setWipeKey(TOKEN_REPLACE_KT.asKey());
		}
		newAutoRenew.ifPresent(a -> op.setAutoRenewAccount(a));
		txn.setTokenUpdate(op);
	}
}
