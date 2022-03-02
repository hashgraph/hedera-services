package com.hedera.services.ledger;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.models.NftId;

import java.util.List;

public class UniqueTokensCommitInterceptor implements CommitInterceptor<NftId, MerkleUniqueToken, NftProperty> {

	// The tracker this interceptor should use for previewing changes. The interceptor is NOT
	// responsible for calling reset() on the tracker, as that will be done by the client code.
//	private SideEffectsTracker sideEffectsTracker;

	public UniqueTokensCommitInterceptor(final SideEffectsTracker sideEffectsTracker) {
//		this.sideEffectsTracker = sideEffectsTracker;
	}

	public void setSideEffectsTracker(final SideEffectsTracker sideEffectsTracker) {
//		this.sideEffectsTracker = sideEffectsTracker;
	}

	@Override
	public void preview(List<MerkleLeafChanges<NftId, MerkleUniqueToken, NftProperty>> changesToCommit) {
		//to be implemented
	}
}