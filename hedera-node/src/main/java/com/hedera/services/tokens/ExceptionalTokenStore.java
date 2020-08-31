package com.hedera.services.tokens;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.Optional;

public enum ExceptionalTokenStore implements TokenStore {
	NOOP_TOKEN_LEDGER;



	@Override
	public ResponseCodeEnum relationshipStatus(MerkleAccount account, TokenID id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum checkThawability(AccountID aId, TokenID tId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum checkFreezability(AccountID aId, TokenID tId) {
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
	public boolean exists(TokenID id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public MerkleToken get(TokenID id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Optional<MerkleToken> lookup(TokenID id) {
		throw new UnsupportedOperationException();
	}
}
