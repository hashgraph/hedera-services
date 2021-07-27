package com.hedera.services.store.tokens.views;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * A {@link UniqTokenView} that always returns empty lists.
 */
public enum EmptyUniqueTokenView implements UniqTokenView {
	EMPTY_UNIQUE_TOKEN_VIEW;

	@Override
	public List<TokenNftInfo> ownedAssociations(@Nonnull AccountID owner, long start, long end) {
		return Collections.emptyList();
	}

	@Override
	public List<TokenNftInfo> typedAssociations(@Nonnull TokenID type, long start, long end) {
		return Collections.emptyList();
	}
}
