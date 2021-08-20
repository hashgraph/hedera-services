package com.hedera.services.state.logic;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;

public class TopLevelTransition implements Runnable {
	private final ScreenedTransition screenedTransition;
	private final TransactionContext txnCtx;
	private final NetworkCtxManager networkCtxManager;
	private final TxnChargingPolicyAgent chargingPolicyAgent;
	private final SignatureScreen signatureScreen;
	private final KeyActivationScreen keyActivationScreen;

	public TopLevelTransition(
			ScreenedTransition screenedTransition,
			NetworkCtxManager networkCtxManager,
			TransactionContext txnCtx,
			SignatureScreen signatureScreen,
			TxnChargingPolicyAgent chargingPolicyAgent,
			KeyActivationScreen keyActivationScreen
	) {
		this.txnCtx = txnCtx;
		this.networkCtxManager = networkCtxManager;
		this.chargingPolicyAgent = chargingPolicyAgent;
		this.signatureScreen = signatureScreen;
		this.keyActivationScreen = keyActivationScreen;
		this.screenedTransition = screenedTransition;
	}

	@Override
	public void run() {
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

		screenedTransition.finishFor(accessor);
	}
}
