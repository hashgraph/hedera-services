/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.logic;

import static com.hedera.node.app.service.mono.context.properties.SemanticVersions.SEMANTIC_VERSIONS;
import static com.hedera.node.app.service.mono.utils.Units.MIN_TRANS_TIMESTAMP_INCR_NANOS;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.records.ConsensusTimeTracker;
import com.hedera.node.app.service.mono.records.RecordCache;
import com.hedera.node.app.service.mono.state.expiry.EntityAutoExpiry;
import com.hedera.node.app.service.mono.state.expiry.ExpiryManager;
import com.hedera.node.app.service.mono.stats.ExecutionTimeTracker;
import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.txns.schedule.ScheduleProcessing;
import com.hedera.node.app.service.mono.txns.span.ExpandHandleSpan;
import com.hedera.node.app.service.mono.txns.span.SpanMapManager;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
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
    private final RecordCache recordCache;
    private final InitTrigger initTrigger;
    private final SpanMapManager spanMapManager;

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
            final StateView workingView,
            final RecordCache recordCache,
            @NonNull final InitTrigger initTrigger,
            SpanMapManager spanMapManager) {
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
        this.recordCache = recordCache;
        this.initTrigger = requireNonNull(initTrigger);
        this.spanMapManager = spanMapManager;
    }

    @Override
    public void incorporateConsensusTxn(
            @NonNull final ConsensusTransaction platformTxn,
            final long submittingMember,
            @NonNull final SoftwareVersion softwareVersion) {

        try {
            final var accessor = expandHandleSpan.accessorFor(platformTxn);
            // Check if the transaction is submitted by a version before the deployed version.
            // If so, return and set the status on the receipt to BUSY
            if (initTrigger != EVENT_STREAM_RECOVERY
                    && SEMANTIC_VERSIONS.deployedSoftwareVersion().isAfter(softwareVersion)) {
                recordCache.setStaleTransaction(
                        accessor.getPayer(), accessor, platformTxn.getConsensusTimestamp(), submittingMember);
                return;
            }
            incorporate(accessor, platformTxn.getConsensusTimestamp(), submittingMember);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Consensus platform txn was not gRPC!", e);
        } catch (Exception internal) {
            log.error("Unhandled internal process failure", internal);
        }
    }

    void incorporate(final SwirldsTxnAccessor accessor, Instant consensusTime, final long submittingMember) {
        // Deduct 1000 nanos from the consensusTime allotted by platform, to accommodate the
        // preceding,
        // following child records and any long term scheduled transactions triggered by the
        // current transaction
        // in the balance file with consensus timestamp X to include all transactions whose
        // consensus time T <= X.
        consensusTime = consensusTime.minusNanos(MIN_TRANS_TIMESTAMP_INCR_NANOS);

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
    }

    private void processScheduledTransactions(Instant consensusTime, long submittingMember) {
        TxnAccessor triggeredAccessor = null;

        for (int i = 0; i < scheduleProcessing.getMaxProcessingLoopIterations(); ++i) {

            boolean hasMore = consensusTimeTracker.hasMoreTransactionTime(false);

            triggeredAccessor = scheduleProcessing.triggerNextTransactionExpiringAsNeeded(
                    consensusTime, triggeredAccessor, !hasMore);

            if (triggeredAccessor != null) {
                doProcess(submittingMember, consensusTimeTracker.nextTransactionTime(false), triggeredAccessor);
            } else {
                hasMore = false;
            }

            if (!hasMore) {
                return;
            }
        }

        log.warn("maxProcessingLoopIterations reached in processScheduledTransactions. Waiting for"
                + " next call to continue. Scheduled Transaction expiration may be delayed.");
    }

    private void doProcess(final long submittingMember, final Instant consensusTime, final TxnAccessor accessor) {
        executionTimeTracker.start();
        txnManager.process(accessor, consensusTime, submittingMember);
        final var triggeredAccessor = txnCtx.triggeredTxn();
        if (triggeredAccessor != null) {
            // Ensure we take custom fees into account when charging fees
            if (triggeredAccessor.getFunction() == HederaFunctionality.CryptoTransfer) {
                spanMapManager.expandImpliedTransfers(triggeredAccessor);
            }
            txnManager.process(triggeredAccessor, consensusTimeTracker.nextTransactionTime(false), submittingMember);
        }
        executionTimeTracker.stop();
    }
}
