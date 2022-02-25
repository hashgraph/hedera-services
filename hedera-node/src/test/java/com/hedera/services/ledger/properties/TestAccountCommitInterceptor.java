package com.hedera.services.ledger.properties;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.CommitInterceptor;
import com.hedera.services.ledger.MerkleLeafChanges;
import com.hedera.services.ledger.accounts.TestAccount;

import java.util.List;

public class TestAccountCommitInterceptor implements CommitInterceptor<Long, TestAccount, TestAccountProperty> {

	// The tracker this interceptor should use for previewing changes. The interceptor is NOT
	// responsible for calling reset() on the tracker, as that will be done by the client code.
	private SideEffectsTracker sideEffectsTracker;

	public TestAccountCommitInterceptor() {
	}

	public void setSideEffectsTracker(final SideEffectsTracker sideEffectsTracker) {
		this.sideEffectsTracker = sideEffectsTracker;
	}

	@Override
	public void preview(List<MerkleLeafChanges<Long, TestAccount, TestAccountProperty>> changesToCommit) {

	}
}