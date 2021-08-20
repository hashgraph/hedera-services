package com.hedera.services.state.logic;


import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.txns.TransitionRunner;
import com.hedera.services.utils.TxnAccessor;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class HandleTransaction {
	private final TransitionRunner transitionRunner;
	private final SystemOpPolicies opPolicies;
	private final TransactionContext txnCtx;
	private final NetworkCtxManager networkCtxManager;
	private final TxnChargingPolicyAgent chargingPolicyAgent;
	private final SignatureScreen signatureScreen;
	private final KeyActivationScreen keyActivationScreen;

	public HandleTransaction(
			TransitionRunner transitionRunner,
			SystemOpPolicies opPolicies,
			NetworkCtxManager networkCtxManager,
			TransactionContext txnCtx,
			SignatureScreen signatureScreen,
			TxnChargingPolicyAgent chargingPolicyAgent,
			KeyActivationScreen keyActivationScreen
	) {
		this.txnCtx = txnCtx;
		this.opPolicies = opPolicies;
		this.networkCtxManager = networkCtxManager;
		this.chargingPolicyAgent = chargingPolicyAgent;
		this.transitionRunner = transitionRunner;
		this.signatureScreen = signatureScreen;
		this.keyActivationScreen = keyActivationScreen;
	}

	public void runTopLevelProcess() {
		final var accessor = txnCtx.accessor();
		final var now = txnCtx.consensusTime();

		networkCtxManager.advanceConsensusClockTo(now);

		final var sigStatus = signatureScreen.applyTo(accessor);
		if (!chargingPolicyAgent.applyPolicyFor(accessor)) {
			return;
		}
		if (!keyActivationScreen.reqKeysAreActiveGiven(sigStatus)) {
			return;
		}

		finishTransition(accessor);
	}

	void finishTransition(TxnAccessor accessor) {
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
