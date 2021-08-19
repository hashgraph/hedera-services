package com.hedera.services.state.logic;


import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.txns.TransitionRunner;
import com.hedera.services.utils.TxnAccessor;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class HandleTransaction {
	private final Rationalization rationalization;
	private final TransitionRunner transitionRunner;
	private final SystemOpPolicies opPolicies;
	private final TransactionContext txnCtx;
	private final NetworkCtxManager networkCtxManager;
	private final TxnChargingPolicyAgent chargingPolicyAgent;
	private final InHandleActivationHelper activationHelper;

	public HandleTransaction(
			Rationalization rationalization,
			TransitionRunner transitionRunner,
			SystemOpPolicies opPolicies,
			NetworkCtxManager networkCtxManager,
			TransactionContext txnCtx,
			TxnChargingPolicyAgent chargingPolicyAgent,
			InHandleActivationHelper activationHelper
	) {
		this.rationalization = rationalization;
		this.txnCtx = txnCtx;
		this.networkCtxManager = networkCtxManager;
		this.chargingPolicyAgent = chargingPolicyAgent;
		this.activationHelper = activationHelper;
		this.transitionRunner = transitionRunner;
		this.opPolicies = opPolicies;
	}

	void finishTransition(TxnAccessor accessor) {
		final var sysAuthStatus = opPolicies.check(accessor).asStatus();
		if (sysAuthStatus != OK) {

		}
	}
}
