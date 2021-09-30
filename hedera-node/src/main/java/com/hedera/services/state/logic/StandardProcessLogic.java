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
import com.hedera.services.state.expiry.EntityAutoRenewal;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.stats.ExecutionTimeTracker;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.utils.PlatformTxnAccessor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

@Singleton
public class StandardProcessLogic implements ProcessLogic {
	private final ExpiryManager expiries;
	private final InvariantChecks invariantChecks;
	private final EntityAutoRenewal autoRenewal;
	private final ServicesTxnManager txnManager;
	private final TransactionContext txnCtx;
	private final ExecutionTimeTracker executionTimeTracker;

	@Inject
	public StandardProcessLogic(
			ExpiryManager expiries,
			InvariantChecks invariantChecks,
			EntityAutoRenewal autoRenewal,
			ServicesTxnManager txnManager,
			TransactionContext txnCtx,
			ExecutionTimeTracker executionTimeTracker
	) {
		this.expiries = expiries;
		this.invariantChecks = invariantChecks;
		this.executionTimeTracker = executionTimeTracker;
		this.autoRenewal = autoRenewal;
		this.txnManager = txnManager;
		this.txnCtx = txnCtx;
	}

	@Override
	public void incorporateConsensusTxn(PlatformTxnAccessor accessor, Instant consensusTime, long submittingMember) {
		Instant effectiveConsensusTime = consensusTime;
		if (accessor.canTriggerTxn()) {
			effectiveConsensusTime = consensusTime.minusNanos(1);
		}

		if (!invariantChecks.holdFor(accessor, effectiveConsensusTime, submittingMember)) {
			return;
		}

		expiries.purge(effectiveConsensusTime.getEpochSecond());

		executionTimeTracker.start();
		txnManager.process(accessor, effectiveConsensusTime, submittingMember);
		final var triggeredAccessor = txnCtx.triggeredTxn();
		if (triggeredAccessor != null) {
			txnManager.process(triggeredAccessor, consensusTime, submittingMember);
		}
		executionTimeTracker.stop();

		autoRenewal.execute(consensusTime);
	}
}
