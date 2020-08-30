package com.hedera.services.tokens;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenCreation;

public enum ExceptionalTokenLedger implements TokenLedger {
	NOOP_TOKEN_LEDGER;

	@Override
	public TokenCreationResult create(TokenCreation request, AccountID sponsor) {
		throw new UnsupportedOperationException();
	}
}
