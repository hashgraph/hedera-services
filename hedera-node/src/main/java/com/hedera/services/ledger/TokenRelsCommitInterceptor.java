package com.hedera.services.ledger;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class TokenRelsCommitInterceptor implements CommitInterceptor<Pair<AccountID, TokenID>, MerkleTokenRelStatus, TokenRelProperty> {

	// The tracker this interceptor should use for previewing changes. The interceptor is NOT
	// responsible for calling reset() on the tracker, as that will be done by the client code.
//	private SideEffectsTracker sideEffectsTracker;

	public TokenRelsCommitInterceptor(final SideEffectsTracker sideEffectsTracker) {
//		this.sideEffectsTracker = sideEffectsTracker;
	}

	public void setSideEffectsTracker(final SideEffectsTracker sideEffectsTracker) {
//		this.sideEffectsTracker = sideEffectsTracker;
	}

	/**
	 * Accepts a pending change set, including creations and removals.
	 *
	 * @throws IllegalStateException if these changes are invalid
	 */
	@Override
	public void preview(final List<MerkleLeafChanges<Pair<AccountID, TokenID>, MerkleTokenRelStatus,
			TokenRelProperty>> changesToCommit) {
		//to be implemented
	}
}