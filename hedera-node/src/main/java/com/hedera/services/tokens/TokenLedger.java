package com.hedera.services.tokens;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenCreation;

public interface TokenLedger {
	TokenCreationResult create(TokenCreation request, AccountID sponsor);
}
