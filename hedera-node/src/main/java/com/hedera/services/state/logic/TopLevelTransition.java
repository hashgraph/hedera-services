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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public class TopLevelTransition implements Runnable {
	private final ScreenedTransition screenedTransition;
	private final TransactionContext txnCtx;
	private final NetworkCtxManager networkCtxManager;
	private final TxnChargingPolicyAgent chargingPolicyAgent;
	private final SignatureScreen signatureScreen;
	private final ThrottleScreen throttleScreen;
	private final KeyActivationScreen keyActivationScreen;

	@Inject
	public TopLevelTransition(
			ScreenedTransition screenedTransition,
			NetworkCtxManager networkCtxManager,
			TransactionContext txnCtx,
			SignatureScreen signatureScreen,
			TxnChargingPolicyAgent chargingPolicyAgent,
			KeyActivationScreen keyActivationScreen,
			ThrottleScreen throttleScreen
	) {
		this.txnCtx = txnCtx;
		this.networkCtxManager = networkCtxManager;
		this.chargingPolicyAgent = chargingPolicyAgent;
		this.signatureScreen = signatureScreen;
		this.keyActivationScreen = keyActivationScreen;
		this.screenedTransition = screenedTransition;
		this.throttleScreen = throttleScreen;
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

		final var throttleScreenStatus = throttleScreen.applyTo(accessor);
		if (throttleScreenStatus != OK) {
			txnCtx.setStatus(throttleScreenStatus);
			return;
		}

		screenedTransition.finishFor(accessor);
	}
}
