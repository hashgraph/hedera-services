package com.hedera.services.tokens;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.Optional;

public interface TokenStore {
	boolean exists(TokenID id);
	MerkleToken get(TokenID id);

	ResponseCodeEnum checkThawability(AccountID aId, TokenID tId);
	ResponseCodeEnum checkFreezability(AccountID aId, TokenID tId);
	ResponseCodeEnum adjustBalance(AccountID aId, TokenID tId, long adjustment);

	Optional<MerkleToken> lookup(TokenID id);

	TokenCreationResult createProvisionally(TokenCreation request, AccountID sponsor);
	void commitCreation();
	void rollbackCreation();

	ResponseCodeEnum relationshipStatus(MerkleAccount account, TokenID id);
}
