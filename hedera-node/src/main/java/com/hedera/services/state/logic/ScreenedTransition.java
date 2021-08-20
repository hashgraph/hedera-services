package com.hedera.services.state.logic;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.txns.TransitionRunner;
import com.hedera.services.utils.TxnAccessor;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ScreenedTransition {
	private final TransitionRunner transitionRunner;
	private final SystemOpPolicies opPolicies;
	private final TransactionContext txnCtx;
	private final NetworkCtxManager networkCtxManager;

	public ScreenedTransition(
			TransitionRunner transitionRunner,
			SystemOpPolicies opPolicies,
			TransactionContext txnCtx,
			NetworkCtxManager networkCtxManager
	) {
		this.transitionRunner = transitionRunner;
		this.opPolicies = opPolicies;
		this.txnCtx = txnCtx;
		this.networkCtxManager = networkCtxManager;
	}

	void finishFor(TxnAccessor accessor) {
		final var sysAuthStatus = opPolicies.check(accessor).asStatus();
		if (sysAuthStatus != OK) {
			txnCtx.setStatus(sysAuthStatus);
			return;
		}
		if (transitionRunner.tryTransition(accessor)) {
			networkCtxManager.finishIncorporating(accessor.getFunction());
		}
	}
}
