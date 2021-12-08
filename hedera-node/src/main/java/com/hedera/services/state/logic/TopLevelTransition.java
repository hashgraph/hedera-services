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

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TopLevelTransition implements Runnable {
	private final ScreenedTransition screenedTransition;
	private final TransactionContext txnCtx;
	private final NetworkCtxManager networkCtxManager;
	private final TxnChargingPolicyAgent chargingPolicyAgent;
	private final SignatureScreen signatureScreen;
	private final KeyActivationScreen keyActivationScreen;

	@Inject
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
		System.out.println("Final sigStatus: " + sigStatus);
		if (!chargingPolicyAgent.applyPolicyFor(accessor)) {
			return;
		}
		if (!keyActivationScreen.reqKeysAreActiveGiven(sigStatus)) {
			return;
		}

		screenedTransition.finishFor(accessor);
	}
}
