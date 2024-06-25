package com.hedera.node.app.workflows.handle.flow.txn;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.TokenContextImpl;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.PreHandleResultManager;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserRecordInitializer;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.GenesisWorkflow;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.config.ConfigProvider;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record UserTxn(
        boolean isGenesisTxn,
        @NonNull HederaFunctionality functionality,
        @NonNull Instant consensusNow,
        @NonNull HederaState state,
        @NonNull PlatformState platformState,
        @NonNull ConsensusEvent event,
        @NonNull ConsensusTransaction platformTxn,
        @NonNull RecordListBuilder recordListBuilder,
        @NonNull TransactionInfo txnInfo,
        @NonNull TokenContextImpl tokenContext,
        @NonNull SavepointStackImpl stack,
        @NonNull PreHandleResult preHandleResult,
        @NonNull ReadableStoreFactory readableStoreFactory,
        @NonNull Configuration configuration,
        @NonNull Instant lastHandledConsensusTime,
        @NonNull NodeInfo creatorInfo) {

    public static UserTxn from(
           // @UserTxnScope
           @NonNull final HederaState state,
           @NonNull final PlatformState platformState,
           @NonNull final ConsensusEvent event,
           @NonNull final NodeInfo creatorInfo,
           @NonNull final ConsensusTransaction platformTxn,
           @NonNull final Instant consensusNow,
           @NonNull final Instant lastHandledConsensusTime,
           // @Singleton
           @NonNull final ConfigProvider configProvider,
           @NonNull final StoreMetricsService storeMetricsService,
           @NonNull final BlockRecordManager blockRecordManager,
           @NonNull final PreHandleResultManager preHandleResultManager) {
        final var config = configProvider.getConfiguration();
        final var stack = new SavepointStackImpl(state);
        final var readableStoreFactory = new ReadableStoreFactory(stack);
        final var preHandleResult = preHandleResultManager.getCurrentPreHandleResult(creatorInfo, platformTxn, readableStoreFactory);
        final var txnInfo = requireNonNull(preHandleResult.txInfo());
        final var recordListBuilder = new RecordListBuilder(consensusNow);
        return new UserTxn(
                lastHandledConsensusTime.equals(Instant.EPOCH),
                txnInfo.functionality(),
                consensusNow,
                state,
                platformState,
                event,
                platformTxn,
                recordListBuilder,
                txnInfo,
                new TokenContextImpl(config, state, storeMetricsService, stack, recordListBuilder, blockRecordManager),
                stack,
                preHandleResult,
                readableStoreFactory,
                config,
                lastHandledConsensusTime,
                creatorInfo);
    }

    public UserTxnWorkflow workflowWith(
            @NonNull final SoftwareVersion version,
            @NonNull final InitTrigger initTrigger,
            @NonNull final DefaultHandleWorkflow defaultHandleWorkflow,
            @NonNull final GenesisWorkflow genesisWorkflow,
            @NonNull final HederaRecordCache recordCache,
            @NonNull final HandleWorkflowMetrics handleWorkflowMetrics,
            @NonNull final UserRecordInitializer userRecordInitializer,
            @NonNull final ExchangeRateManager exchangeRateManager) {
        throw new AssertionError("Not implemented");
    }
}
