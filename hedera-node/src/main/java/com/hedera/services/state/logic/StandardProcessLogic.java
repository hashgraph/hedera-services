/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.state.logic;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.state.expiry.EntityAutoExpiry;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.stats.ExecutionTimeTracker;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.txns.schedule.ScheduleProcessing;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.swirlds.common.system.transaction.Transaction;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class StandardProcessLogic implements ProcessLogic {
    private static final Logger log = LogManager.getLogger(StandardProcessLogic.class);

    private final ExpiryManager expiries;
    private final InvariantChecks invariantChecks;
    private final ConsensusTimeTracker consensusTimeTracker;
    private final ExpandHandleSpan expandHandleSpan;
    private final EntityAutoExpiry autoRenewal;
    private final ServicesTxnManager txnManager;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext txnCtx;
    private final ExecutionTimeTracker executionTimeTracker;
    private final StateView workingView;
    private final ScheduleProcessing scheduleProcessing;
    private final RecordStreaming recordStreaming;

    @Inject
    public StandardProcessLogic(
            final ExpiryManager expiries,
            final InvariantChecks invariantChecks,
            final ExpandHandleSpan expandHandleSpan,
            final ConsensusTimeTracker consensusTimeTracker,
            final EntityAutoExpiry autoRenewal,
            final ServicesTxnManager txnManager,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext txnCtx,
            final ScheduleProcessing scheduleProcessing,
            final ExecutionTimeTracker executionTimeTracker,
            final RecordStreaming recordStreaming,
            final StateView workingView) {
        this.expiries = expiries;
        this.invariantChecks = invariantChecks;
        this.expandHandleSpan = expandHandleSpan;
        this.executionTimeTracker = executionTimeTracker;
        this.consensusTimeTracker = consensusTimeTracker;
        this.autoRenewal = autoRenewal;
        this.txnManager = txnManager;
        this.txnCtx = txnCtx;
        this.scheduleProcessing = scheduleProcessing;
        this.sigImpactHistorian = sigImpactHistorian;
        this.recordStreaming = recordStreaming;
        this.workingView = workingView;
    }

    @Override
    public void incorporateConsensusTxn(
            Transaction platformTxn, Instant consensusTime, long submittingMember) {
        try {
            final var accessor = expandHandleSpan.accessorFor(platformTxn);
            accessor.setStateView(workingView);
            if (!invariantChecks.holdFor(accessor, consensusTime, submittingMember)) {
                return;
            }

            consensusTimeTracker.reset(consensusTime);

            sigImpactHistorian.setChangeTime(consensusTime);
            expiries.purge(consensusTime.getEpochSecond());
            sigImpactHistorian.purge();
            recordStreaming.resetBlockNo();

            doProcess(
                    submittingMember,
                    consensusTimeTracker.isFirstUsed()
                            ? consensusTimeTracker.nextTransactionTime(true)
                            : consensusTimeTracker.firstTransactionTime(),
                    accessor);

            if (scheduleProcessing.shouldProcessScheduledTransactions(consensusTime)) {
                processScheduledTransactions(consensusTime, submittingMember);
            }

            autoRenewal.execute(consensusTime);
            platformTxn.clearSignatures();
        } catch (InvalidProtocolBufferException e) {
            log.warn("Consensus platform txn was not gRPC!", e);
        } catch (Exception internal) {
            log.error("Unhandled internal process failure", internal);
        }
    }

    private void processScheduledTransactions(Instant consensusTime, long submittingMember) {
        TxnAccessor triggeredAccessor = null;

        for (int i = 0; i < scheduleProcessing.getMaxProcessingLoopIterations(); ++i) {

            boolean hasMore = consensusTimeTracker.hasMoreTransactionTime(false);

            triggeredAccessor =
                    scheduleProcessing.triggerNextTransactionExpiringAsNeeded(
                            consensusTime, triggeredAccessor, !hasMore);

            if (triggeredAccessor != null) {
                doProcess(
                        submittingMember,
                        consensusTimeTracker.nextTransactionTime(false),
                        triggeredAccessor);
            } else {
                hasMore = false;
            }

            if (!hasMore) {
                return;
            }
        }

        log.warn(
                "maxProcessingLoopIterations reached in processScheduledTransactions. Waiting for"
                    + " next call to continue. Scheduled Transaction expiration may be delayed.");
    }

    private void doProcess(
            final long submittingMember, final Instant consensusTime, final TxnAccessor accessor) {
        executionTimeTracker.start();
        txnManager.process(accessor, consensusTime, submittingMember);
        final var triggeredAccessor = txnCtx.triggeredTxn();
        if (triggeredAccessor != null) {
            txnManager.process(
                    triggeredAccessor,
                    consensusTimeTracker.nextTransactionTime(false),
                    submittingMember);
        }
        executionTimeTracker.stop();
    }
}
