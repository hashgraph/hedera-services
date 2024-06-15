/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.txn;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.node.app.workflows.handle.flow.util.FlowUtils.ALERT_MESSAGE;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.logic.UserRecordInitializer;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.GenesisWorkflow;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@UserTxnScope
public class UserTxnWorkflow {
    private static final Logger logger = LogManager.getLogger(UserTxnWorkflow.class);

    private final SoftwareVersion version;
    private final InitTrigger initTrigger;
    private final SkipHandleWorkflow skipHandleWorkflow;
    private final DefaultHandleWorkflow defaultHandleWorkflow;
    private final GenesisWorkflow genesisWorkflow;
    private final UserTransactionComponent userTxn;
    private final BlockRecordManager blockRecordManager;
    private final HederaRecordCache recordCache;
    private final NetworkUtilizationManager networkUtilizationManager;
    private final HandleWorkflowMetrics handleWorkflowMetrics;
    private final ThrottleServiceManager throttleServiceManager;
    private final NetworkInfo networkInfo;
    private final UserRecordInitializer userRecordInitializer;

    @Inject
    public UserTxnWorkflow(
            @NonNull final SoftwareVersion version,
            @NonNull final InitTrigger initTrigger,
            @NonNull final SkipHandleWorkflow skipHandleWorkflow,
            @NonNull final DefaultHandleWorkflow defaultHandleWorkflow,
            @NonNull final GenesisWorkflow genesisWorkflow,
            @NonNull final UserTransactionComponent userTxn,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final HederaRecordCache recordCache,
            @NonNull final NetworkUtilizationManager networkUtilizationManager,
            @NonNull final HandleWorkflowMetrics handleWorkflowMetrics,
            @NonNull final ThrottleServiceManager throttleServiceManager,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final UserRecordInitializer userRecordInitializer) {
        this.version = requireNonNull(version);
        this.initTrigger = requireNonNull(initTrigger);
        this.skipHandleWorkflow = requireNonNull(skipHandleWorkflow);
        this.defaultHandleWorkflow = requireNonNull(defaultHandleWorkflow);
        this.genesisWorkflow = requireNonNull(genesisWorkflow);
        this.userTxn = requireNonNull(userTxn);
        this.blockRecordManager = requireNonNull(blockRecordManager);
        this.recordCache = requireNonNull(recordCache);
        this.networkUtilizationManager = requireNonNull(networkUtilizationManager);
        this.handleWorkflowMetrics = requireNonNull(handleWorkflowMetrics);
        this.throttleServiceManager = requireNonNull(throttleServiceManager);
        this.networkInfo = requireNonNull(networkInfo);
        this.userRecordInitializer = requireNonNull(userRecordInitializer);
    }

    /**
     * Executes the user transaction and returns a stream of records that capture all
     * side effects on state that are stipulated by the pre-block-stream contract with
     * mirror nodes.
     *
     * <p>Never throws an exception without a fundamental breakdown in the integrity
     * of the system invariants. If there is an internal error when executing the
     * transaction, returns a stream of a single {@link ResponseCodeEnum#FAIL_INVALID}
     * record with no other side effects.
     *
     * <p><b>IMPORTANT:</b> With block streams, this contract will expand to include
     * all side effects on state, no exceptions.
     *
     * @return the stream of records
     */
    public Stream<SingleTransactionRecord> execute() {
        try {
            return sideEffectsOfExecution();
        } catch (final Exception e) {
            logger.error("{} - exception thrown while handling user transaction", ALERT_MESSAGE, e);
            return failInvalidRecordStream();
        }
    }

    /**
     * Executes the user transaction and returns a stream of records that capture all
     * side effects on state that are stipulated by the pre-block-stream contract with
     * mirror nodes.
     *
     * @return the stream of records
     */
    private Stream<SingleTransactionRecord> sideEffectsOfExecution() {
        if (isOlderSoftwareEvent()) {
            skipHandleWorkflow.execute(userTxn);
        } else {
            if (userTxn.isGenesisTxn()) {
                genesisWorkflow.executeIn(userTxn.tokenContext());
            }
            defaultHandleWorkflow.execute(userTxn);
            updateWorkflowMetrics();
        }
        return allSideEffectsFrom(userTxn.recordListBuilder());
    }

    /**
     * Returns a stream of a single {@link ResponseCodeEnum#FAIL_INVALID} record.
     *
     * @return the failure record
     */
    private Stream<SingleTransactionRecord> failInvalidRecordStream() {
        final var failInvalidRecordListBuilder = new RecordListBuilder(userTxn.consensusNow());
        final var recordBuilder = failInvalidRecordListBuilder.userTransactionRecordBuilder();
        userRecordInitializer.initializeUserRecord(recordBuilder, userTxn.txnInfo());
        recordBuilder.status(FAIL_INVALID);
        userTxn.stack().rollbackFullStack();
        return allSideEffectsFrom(failInvalidRecordListBuilder);
    }

    /**
     * Builds and caches the result of the user transaction.
     *
     * @param builder the record list builder
     * @return the stream of records
     */
    private Stream<SingleTransactionRecord> allSideEffectsFrom(@NonNull final RecordListBuilder builder) {
        final var result = builder.build();
        recordCache.add(
                userTxn.creator().nodeId(), requireNonNull(userTxn.txnInfo().payerID()), result.records());
        return result.records().stream();
    }

    /**
     * Updates the metrics for the handle workflow.
     */
    private void updateWorkflowMetrics() {
        if (userTxn.isGenesisTxn()
                || userTxn.consensusNow().getEpochSecond()
                        > blockRecordManager.consTimeOfLastHandledTxn().getEpochSecond()) {
            handleWorkflowMetrics.switchConsensusSecond();
        }
    }

    /**
     * Returns true if the software event is older than the current software version.
     *
     * @return true if the software event is older than the current software version
     */
    private boolean isOlderSoftwareEvent() {
        return this.initTrigger != EVENT_STREAM_RECOVERY
                && version.compareTo(userTxn.platformEvent().getSoftwareVersion()) > 0;
    }
}
