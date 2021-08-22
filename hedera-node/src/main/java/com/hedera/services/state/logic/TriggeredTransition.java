package com.hedera.services.state.logic;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.charging.FeeChargingPolicy;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class TriggeredTransition implements Runnable {
	private final StateView currentView;
	private final FeeCalculator fees;
	private final FeeChargingPolicy chargingPolicy;
	private final NetworkCtxManager networkCtxManager;
	private final ScreenedTransition screenedTransition;
	private final TransactionContext txnCtx;

	public TriggeredTransition(
			StateView currentView,
			FeeCalculator fees,
			FeeChargingPolicy chargingPolicy,
			TransactionContext txnCtx,
			NetworkCtxManager networkCtxManager,
			ScreenedTransition screenedTransition
	) {
		this.currentView = currentView;
		this.fees = fees;
		this.chargingPolicy = chargingPolicy;
		this.txnCtx = txnCtx;
		this.networkCtxManager = networkCtxManager;
		this.screenedTransition = screenedTransition;
	}

	@Override
	public void run() {
		final var accessor = txnCtx.accessor();
		final var now = txnCtx.consensusTime();

		networkCtxManager.advanceConsensusClockTo(now);
		networkCtxManager.prepareForIncorporating(accessor);

		final var fee = fees.computeFee(accessor, txnCtx.activePayerKey(), currentView, now);
		final var chargingOutcome = chargingPolicy.applyForTriggered(fee);
		if (chargingOutcome != OK) {
			txnCtx.setStatus(chargingOutcome);
			return;
		}

		screenedTransition.finishFor(accessor);
	}
}
