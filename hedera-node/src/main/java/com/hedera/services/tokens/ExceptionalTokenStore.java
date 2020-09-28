package com.hedera.services.tokens;

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

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;

import java.util.List;
import java.util.function.Consumer;

public enum ExceptionalTokenStore implements TokenStore {
	NOOP_TOKEN_STORE;

	@Override
	public ResponseCodeEnum unfreeze(AccountID aId, TokenID tId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum grantKyc(AccountID aId, TokenID tId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum revokeKyc(AccountID aId, TokenID tId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum associate(AccountID aId, List<TokenID> tokens) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum dissociate(AccountID aId, List<TokenID> tokens) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum freeze(AccountID aId, TokenID tId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum update(TokenUpdateTransactionBody changes, long now) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum adjustBalance(AccountID aId, TokenID tId, long adjustment) {
		throw new UnsupportedOperationException();
	}

	@Override
	public TokenCreationResult createProvisionally(TokenCreateTransactionBody request, AccountID sponsor, long now) {
		throw new UnsupportedOperationException();
	}



	@Override
	public void commitCreation() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void rollbackCreation() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isCreationPending() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAccountsLedger(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		/* No-op */
	}

	@Override
	public void setHederaLedger(HederaLedger ledger) {
		/* No-op */
	}

	@Override
	public void apply(TokenID id, Consumer<MerkleToken> change) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean exists(TokenID id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public MerkleToken get(TokenID id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum burn(TokenID tId, long amount) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum mint(TokenID tId, long amount) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum wipe(AccountID aId, TokenID tId, long wipingAmount, boolean skipKeyCheck) {
		throw new UnsupportedOperationException();
	}
}
