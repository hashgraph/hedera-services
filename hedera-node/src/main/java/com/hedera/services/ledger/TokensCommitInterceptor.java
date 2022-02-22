package com.hedera.services.ledger;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.List;

public class TokensCommitInterceptor implements CommitInterceptor<TokenID, MerkleToken, TokenProperty> {

	// The tracker this interceptor should use for previewing changes. The interceptor is NOT
	// responsible for calling reset() on the tracker, as that will be done by the client code.
//	private final SideEffectsTracker sideEffectsTracker;

	public TokensCommitInterceptor(final SideEffectsTracker sideEffectsTracker) {
//		this.sideEffectsTracker = sideEffectsTracker;
	}

	@Override
	public void preview(List<MerkleLeafChanges<TokenID, MerkleToken, TokenProperty>> changesToCommit) {
		//to be implemented
	}
}