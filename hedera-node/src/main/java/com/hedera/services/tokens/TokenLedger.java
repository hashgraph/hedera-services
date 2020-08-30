package com.hedera.services.tokens;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.Optional;

public interface TokenLedger {
	ResponseCodeEnum relationshipStatus(MerkleAccount account, TokenID id);
	TokenCreationResult create(TokenCreation request, AccountID sponsor);
	Optional<MerkleToken> lookup(TokenID id);
}
