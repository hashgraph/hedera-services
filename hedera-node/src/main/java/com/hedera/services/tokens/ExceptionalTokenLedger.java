package com.hedera.services.tokens;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.Optional;

public enum ExceptionalTokenLedger implements TokenLedger {
	NOOP_TOKEN_LEDGER;

	@Override
	public ResponseCodeEnum relationshipStatus(MerkleAccount account, TokenID id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public TokenCreationResult create(TokenCreation request, AccountID sponsor) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Optional<MerkleToken> lookup(TokenID id) {
		throw new UnsupportedOperationException();
	}
}
