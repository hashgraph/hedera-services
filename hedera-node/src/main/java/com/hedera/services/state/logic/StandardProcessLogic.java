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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.expiry.EntityAutoRenewal;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.stats.ExecutionTimeTracker;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.swirlds.common.SwirldTransaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

@Singleton
public class StandardProcessLogic implements ProcessLogic {
	private static final Logger log = LogManager.getLogger(StandardProcessLogic.class);

	private final ExpiryManager expiries;
	private final InvariantChecks invariantChecks;
	private final ExpandHandleSpan expandHandleSpan;
	private final EntityAutoRenewal autoRenewal;
	private final ServicesTxnManager txnManager;
	private final SigImpactHistorian sigImpactHistorian;
	private final TransactionContext txnCtx;
	private final ExecutionTimeTracker executionTimeTracker;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public StandardProcessLogic(
			final ExpiryManager expiries,
			final InvariantChecks invariantChecks,
			final ExpandHandleSpan expandHandleSpan,
			final EntityAutoRenewal autoRenewal,
			final ServicesTxnManager txnManager,
			final SigImpactHistorian sigImpactHistorian,
			final TransactionContext txnCtx,
			final ExecutionTimeTracker executionTimeTracker,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.expiries = expiries;
		this.invariantChecks = invariantChecks;
		this.expandHandleSpan = expandHandleSpan;
		this.executionTimeTracker = executionTimeTracker;
		this.autoRenewal = autoRenewal;
		this.txnManager = txnManager;
		this.txnCtx = txnCtx;
		this.dynamicProperties = dynamicProperties;
		this.sigImpactHistorian = sigImpactHistorian;
	}

	@Override
	public void incorporateConsensusTxn(SwirldTransaction platformTxn, Instant consensusTime, long submittingMember) {
		try {
			final var accessor = expandHandleSpan.accessorFor(platformTxn);
			Instant effectiveConsensusTime = consensusTime;
			if (accessor.canTriggerTxn()) {
				final var offset = dynamicProperties.triggerTxnWindBackNanos();
				effectiveConsensusTime = consensusTime.minusNanos(offset);
			}

			if (!invariantChecks.holdFor(accessor, effectiveConsensusTime, submittingMember)) {
				return;
			}

			sigImpactHistorian.setChangeTime(effectiveConsensusTime);
			expiries.purge(effectiveConsensusTime.getEpochSecond());
			sigImpactHistorian.purge();

			doProcess(submittingMember, consensusTime, effectiveConsensusTime, accessor);

			autoRenewal.execute(consensusTime);
		} catch (InvalidProtocolBufferException e) {
			log.warn("Consensus platform txn was not gRPC!", e);
		} catch (Exception internal) {
			log.error("Unhandled internal process failure", internal);
		}
	}

	private void doProcess(
			final long submittingMember,
			final Instant consensusTime,
			final Instant effectiveConsensusTime,
			final PlatformTxnAccessor accessor
	) {
		executionTimeTracker.start();
		txnManager.process(accessor, effectiveConsensusTime, submittingMember);
		final var triggeredAccessor = txnCtx.triggeredTxn();
		if (triggeredAccessor != null) {
			txnManager.process(triggeredAccessor, consensusTime, submittingMember);
		}
		executionTimeTracker.stop();
	}
}
