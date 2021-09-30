package com.hedera.services.state.logic;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TopLevelTransition implements Runnable {
	private final ScreenedTransition screenedTransition;
	private final TransactionContext txnCtx;
	private final NetworkCtxManager networkCtxManager;
	private final TxnChargingPolicyAgent chargingPolicyAgent;

	@Inject
	public TopLevelTransition(
			ScreenedTransition screenedTransition,
			NetworkCtxManager networkCtxManager,
			TransactionContext txnCtx,
			TxnChargingPolicyAgent chargingPolicyAgent
	) {
		this.txnCtx = txnCtx;
		this.networkCtxManager = networkCtxManager;
		this.chargingPolicyAgent = chargingPolicyAgent;
		this.screenedTransition = screenedTransition;
	}

	@Override
	public void run() {
		final var accessor = txnCtx.accessor();
		final var now = txnCtx.consensusTime();

		networkCtxManager.advanceConsensusClockTo(now);

		// Validation is done upstream either during pre-consensus or consensus processing.
		ResponseCodeEnum sigStatus = accessor.getValidationStatus();
		if (sigStatus != ResponseCodeEnum.OK) {
			txnCtx.setStatus(sigStatus);
			return;
		}

		// Payer signature check is done in validator, side-effect is flag is set from within
		if (accessor.hasActivePayerSig()) {
			txnCtx.payerSigIsKnownActive();
			networkCtxManager.prepareForIncorporating(accessor);
		}

		if (!chargingPolicyAgent.applyPolicyFor(accessor)) {
			return;
		}

		screenedTransition.finishFor(accessor);
	}
}
