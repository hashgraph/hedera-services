package com.hedera.services.state.logic;


import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.sigs.factories.ReusableBodySigningFactory;

public class ScopedProcessing implements Runnable {
	private final Rationalization rationalization;
	private final TransactionContext txnCtx;
	private final NetworkCtxManager networkCtxManager;
	private final TxnChargingPolicyAgent chargingPolicyAgent;
	private final ReusableBodySigningFactory bodySigningFactory;

	public ScopedProcessing(
			Rationalization rationalization,
			TransactionContext txnCtx,
			NetworkCtxManager networkCtxManager,
			TxnChargingPolicyAgent chargingPolicyAgent,
			ReusableBodySigningFactory bodySigningFactory
	) {
		this.rationalization = rationalization;
		this.txnCtx = txnCtx;
		this.networkCtxManager = networkCtxManager;
		this.chargingPolicyAgent = chargingPolicyAgent;
		this.bodySigningFactory = bodySigningFactory;
	}

	@Override
	public void run() {

	}
}
