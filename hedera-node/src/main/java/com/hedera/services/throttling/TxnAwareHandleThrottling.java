package com.hedera.services.throttling;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;

import java.util.List;

public class TxnAwareHandleThrottling implements FunctionalityThrottling {
	private final TransactionContext txnCtx;
	private final TimedFunctionalityThrottling delegate;

	public TxnAwareHandleThrottling(TransactionContext txnCtx, TimedFunctionalityThrottling delegate) {
		this.txnCtx = txnCtx;
		this.delegate = delegate;
	}

	@Override
	public boolean shouldThrottle(HederaFunctionality function) {
		return delegate.shouldThrottle(function, txnCtx.consensusTime());
	}

	@Override
	public List<DeterministicThrottle> activeThrottlesFor(HederaFunctionality function) {
		return delegate.activeThrottlesFor(function);
	}

	@Override
	public List<DeterministicThrottle> allActiveThrottles() {
		return delegate.allActiveThrottles();
	}

	@Override
	public void rebuildFor(ThrottleDefinitions defs) {
		delegate.rebuildFor(defs);
	}
}
