package com.hedera.services.store.tokens;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;

import java.util.List;

public interface UniqTokenView {
	List<TokenNftInfo> ownedAssociations(AccountID owner, long start, long end);
	List<TokenNftInfo> typedAssociations(TokenID type, long start, long end);
}
