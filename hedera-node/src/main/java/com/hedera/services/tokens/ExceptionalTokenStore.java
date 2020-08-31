package com.hedera.services.tokens;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;

public enum ExceptionalTokenStore implements TokenStore {
	NOOP_TOKEN_STORE;

	@Override
	public ResponseCodeEnum unfreeze(AccountID aId, TokenID tId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum freeze(AccountID aId, TokenID tId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum adjustBalance(AccountID aId, TokenID tId, long adjustment) {
		throw new UnsupportedOperationException();
	}

	@Override
	public TokenCreationResult createProvisionally(TokenCreation request, AccountID sponsor) {
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
	public void setLedger(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger) { }

	@Override
	public boolean exists(TokenID id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public MerkleToken get(TokenID id) {
		throw new UnsupportedOperationException();
	}


}
