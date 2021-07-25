package com.hedera.services.store.tokens.views;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;

import java.util.Collections;
import java.util.List;

public enum EmptyUniqueTokenView implements UniqTokenView {
	EMPTY_UNIQUE_TOKEN_VIEW;

	@Override
	public List<TokenNftInfo> ownedAssociations(AccountID owner, long start, long end) {
		return Collections.emptyList();
	}

	@Override
	public List<TokenNftInfo> typedAssociations(TokenID type, long start, long end) {
		return Collections.emptyList();
	}
}
