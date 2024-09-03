/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.BUSY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_RECORD_CUSTOMIZER;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.ReversingBehavior.REVERSIBLE;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartEvent;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartRound;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransaction;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP2;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP3;
import static com.hedera.node.app.state.merkle.VersionUtils.isSoOrdered;
import static com.hedera.node.app.workflows.handle.TransactionType.GENESIS_TRANSACTION;
import static com.hedera.node.app.workflows.handle.TransactionType.POST_UPGRADE_TRANSACTION;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static com.swirlds.state.spi.HapiUtils.SEMANTIC_VERSION_COMPARATOR;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.input.EventHeader;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.node.app.blocks.impl.BlockStreamBuilder;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.cache.CacheWarmer;
import com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.RecordStreamBuilder;
import com.hedera.node.app.workflows.handle.record.SystemSetup;
import com.hedera.node.app.workflows.handle.steps.HollowAccountCompletions;
import com.hedera.node.app.workflows.handle.steps.NodeStakeUpdates;
import com.hedera.node.app.workflows.handle.steps.UserTxn;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.State;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The handle workflow that is responsible for handling the next {@link Round} of transactions.
 */
@Singleton
public class HandleWorkflow {
    private static final Logger logger = LogManager.getLogger(HandleWorkflow.class);

    public static final String ALERT_MESSAGE = "Possibly CATASTROPHIC failure";
    private final NetworkInfo networkInfo;
    private final NodeStakeUpdates nodeStakeUpdates;
    private final Authorizer authorizer;
    private final FeeManager feeManager;
    private final DispatchProcessor dispatchProcessor;
    private final ServiceScopeLookup serviceScopeLookup;
    private final ChildDispatchFactory childDispatchFactory;
    private final TransactionDispatcher dispatcher;
    private final NetworkUtilizationManager networkUtilizationManager;
    private final ConfigProvider configProvider;
    private final StoreMetricsService storeMetricsService;
    private final BlockRecordManager blockRecordManager;
    private final BlockStreamManager blockStreamManager;
    private final CacheWarmer cacheWarmer;
    private final HandleWorkflowMetrics handleWorkflowMetrics;
    private final ThrottleServiceManager throttleServiceManager;
    private final SemanticVersion version;
    private final InitTrigger initTrigger;
    private final HollowAccountCompletions hollowAccountCompletions;
    private final SystemSetup systemSetup;
    private final HederaRecordCache recordCache;
    private final ExchangeRateManager exchangeRateManager;
    private final PreHandleWorkflow preHandleWorkflow;
    private final KVStateChangeListener kvStateChangeListener;
    private final BoundaryStateChangeListener boundaryStateChangeListener;
    private final List<StateChanges.Builder> migrationStateChanges;

    @Inject
    public HandleWorkflow(
            @NonNull final NetworkInfo networkInfo,
            @NonNull final NodeStakeUpdates nodeStakeUpdates,
            @NonNull final Authorizer authorizer,
            @NonNull final FeeManager feeManager,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final ChildDispatchFactory childDispatchFactory,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final NetworkUtilizationManager networkUtilizationManager,
            @NonNull final ConfigProvider configProvider,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final BlockStreamManager blockStreamManager,
            @NonNull final CacheWarmer cacheWarmer,
            @NonNull final HandleWorkflowMetrics handleWorkflowMetrics,
            @NonNull final ThrottleServiceManager throttleServiceManager,
            @NonNull final SemanticVersion version,
            @NonNull final InitTrigger initTrigger,
            @NonNull final HollowAccountCompletions hollowAccountCompletions,
            @NonNull final SystemSetup systemSetup,
            @NonNull final HederaRecordCache recordCache,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final PreHandleWorkflow preHandleWorkflow,
            @NonNull final KVStateChangeListener kvStateChangeListener,
            @NonNull final BoundaryStateChangeListener boundaryStateChangeListener,
            @NonNull final List<StateChanges.Builder> migrationStateChanges) {
        this.networkInfo = requireNonNull(networkInfo);
        this.nodeStakeUpdates = requireNonNull(nodeStakeUpdates);
        this.authorizer = requireNonNull(authorizer);
        this.feeManager = requireNonNull(feeManager);
        this.dispatchProcessor = requireNonNull(dispatchProcessor);
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup);
        this.childDispatchFactory = requireNonNull(childDispatchFactory);
        this.dispatcher = requireNonNull(dispatcher);
        this.networkUtilizationManager = requireNonNull(networkUtilizationManager);
        this.configProvider = requireNonNull(configProvider);
        this.storeMetricsService = requireNonNull(storeMetricsService);
        this.blockRecordManager = requireNonNull(blockRecordManager);
        this.blockStreamManager = requireNonNull(blockStreamManager);
        this.cacheWarmer = requireNonNull(cacheWarmer);
        this.handleWorkflowMetrics = requireNonNull(handleWorkflowMetrics);
        this.throttleServiceManager = requireNonNull(throttleServiceManager);
        this.version = requireNonNull(version);
        this.initTrigger = requireNonNull(initTrigger);
        this.hollowAccountCompletions = requireNonNull(hollowAccountCompletions);
        this.systemSetup = requireNonNull(systemSetup);
        this.recordCache = requireNonNull(recordCache);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
        this.preHandleWorkflow = requireNonNull(preHandleWorkflow);
        this.kvStateChangeListener = requireNonNull(kvStateChangeListener);
        this.boundaryStateChangeListener = requireNonNull(boundaryStateChangeListener);
        this.migrationStateChanges = new ArrayList<>(migrationStateChanges);
    }

    /**
     * Handles the next {@link Round}
     *
     * @param state the writable {@link State} that this round will work on
     * @param round the next {@link Round} that needs to be processed
     */
    public void handleRound(@NonNull final State state, @NonNull final Round round) {
        // We only close the round with the block record manager after user transactions
        logStartRound(round);
        cacheWarmer.warm(state, round);
        final var blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        if (blockStreamConfig.streamBlocks()) {
            blockStreamManager.startRound(round, state);
        }
        recordCache.resetRoundReceipts();
        try {
            handleEvents(state, round);
        } finally {
            // Even if there is an exception somewhere, we need to commit the receipts of any handled transactions
            // to the state so these transactions cannot be replayed in future rounds
            recordCache.commitRoundReceipts(state, round.getConsensusTimestamp());
        }
    }

    private void handleEvents(@NonNull final State state, @NonNull final Round round) {
        final var userTransactionsHandled = new AtomicBoolean(false);
        final var blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        for (final var event : round) {
            if (blockStreamConfig.streamBlocks()) {
                streamMetadata(event);
            }
            final var creator = networkInfo.nodeInfo(event.getCreatorId().id());
            if (creator == null) {
                if (!isSoOrdered(event.getSoftwareVersion(), version)) {
                    // We were given an event for a node that does not exist in the address book and was not from
                    // a strictly earlier software upgrade. This will be logged as a warning, as this should never
                    // happen, and we will skip the event. The platform should guarantee that we never receive an event
                    // that isn't associated with the address book, and every node in the address book must have an
                    // account ID, since you cannot delete an account belonging to a node, and you cannot change the
                    // address book non-deterministically.
                    logger.warn(
                            "Received event (version {} vs current {}) from node {} which is not in the address book",
                            com.hedera.hapi.util.HapiUtils.toString(event.getSoftwareVersion()),
                            com.hedera.hapi.util.HapiUtils.toString(version),
                            event.getCreatorId());
                }
                continue;
            }
            // log start of event to transaction state log
            logStartEvent(event, creator);
            // handle each transaction of the event
            for (final var it = event.consensusTransactionIterator(); it.hasNext(); ) {
                final var platformTxn = it.next();
                try {
                    // skip system transactions
                    if (!platformTxn.isSystem()) {
                        userTransactionsHandled.set(true);
                        handlePlatformTransaction(state, event, creator, platformTxn, blockStreamConfig);
                    } else {
                        // TODO - handle block and signature transactions here?
                    }
                } catch (final Exception e) {
                    logger.fatal(
                            "Possibly CATASTROPHIC failure while running the handle workflow. "
                                    + "While this node may not die right away, it is in a bad way, most likely fatally.",
                            e);
                }
            }
        }
        // Update all throttle metrics once per round
        throttleServiceManager.updateAllMetrics();
        // Inform the BlockRecordManager that the round is complete, so it can update running-hashes in state
        // that have been being computed in background threads. The running hash has to be included in
        // state, but we want to synchronize with background threads as infrequently as possible. So once per
        // round is the minimum we can do.
        if (userTransactionsHandled.get() && blockStreamConfig.streamRecords()) {
            blockRecordManager.endRound(state);
        }
    }

    private void streamMetadata(@NonNull final ConsensusEvent event) {
        final var metadataItem = BlockItem.newBuilder()
                .eventHeader(new EventHeader(event.getEventCore(), event.getSignature()))
                .build();
        blockStreamManager.writeItem(metadataItem);
    }

    /**
     * Handles a platform transaction. This method is responsible for creating a {@link UserTxn} and
     * executing the workflow for the transaction. This produces a stream of records that are then passed to the
     * {@link BlockRecordManager} to be externalized.
     *
     * @param state the writable {@link State} that this transaction will work on
     * @param event the {@link ConsensusEvent} that this transaction belongs to
     * @param creator the {@link NodeInfo} of the creator of the transaction
     * @param txn the {@link ConsensusTransaction} to be handled
     * @param blockStreamConfig the block stream configuration
     */
    private void handlePlatformTransaction(
            @NonNull final State state,
            @NonNull final ConsensusEvent event,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction txn,
            @NonNull final BlockStreamConfig blockStreamConfig) {
        final var handleStart = System.nanoTime();

        // Always use platform-assigned time for user transaction, c.f. https://hips.hedera.com/hip/hip-993
        final var consensusNow = txn.getConsensusTimestamp();
        final var userTxn = newUserTxn(state, event, creator, txn, consensusNow);

        if (blockStreamConfig.streamRecords()) {
            blockRecordManager.startUserTransaction(consensusNow, state);
        }
        // Because synthetic account creation records are only externalized on the first user transaction, we
        // also postpone externalizing migration state changes until that same transactional unit
        if (blockStreamConfig.streamBlocks() && !migrationStateChanges.isEmpty()) {
            migrationStateChanges.forEach(builder -> blockStreamManager.writeItem(BlockItem.newBuilder()
                    .stateChanges(builder.consensusTimestamp(blockStreamManager.blockTimestamp())
                            .build())
                    .build()));
            migrationStateChanges.clear();
        }
        final var handleOutput = execute(userTxn);
        if (blockStreamConfig.streamRecords()) {
            blockRecordManager.endUserTransaction(handleOutput.recordsOrThrow().stream(), state);
        }
        if (blockStreamConfig.streamBlocks()) {
            handleOutput.blocksItemsOrThrow().forEach(blockStreamManager::writeItem);
        }
        handleWorkflowMetrics.updateTransactionDuration(
                userTxn.functionality(), (int) (System.nanoTime() - handleStart));
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
    private HandleOutput execute(@NonNull final UserTxn userTxn) {
        final var blockStreamConfig = userTxn.config().getConfigData(BlockStreamConfig.class);
        try {
            if (isOlderSoftwareEvent(userTxn)) {
                initializeBuilderInfo(userTxn.baseBuilder(), userTxn.txnInfo(), exchangeRateManager.exchangeRates())
                        .status(BUSY);
                // Flushes the BUSY builder to the stream, no other side effects
                userTxn.stack().commitTransaction(userTxn.baseBuilder());
            } else {
                if (userTxn.type() == GENESIS_TRANSACTION) {
                    // (FUTURE) Once all genesis setup is done via dispatch, remove this method
                    systemSetup.externalizeInitSideEffects(
                            userTxn.tokenContextImpl(), exchangeRateManager.exchangeRates());
                }
                updateNodeStakes(userTxn);
                if (blockStreamConfig.streamRecords()) {
                    blockRecordManager.advanceConsensusClock(userTxn.consensusNow(), userTxn.state());
                }
                expireSchedules(userTxn);
                logPreDispatch(userTxn);
                final var dispatch = dispatchFor(userTxn, blockStreamConfig);
                if (userTxn.type() == GENESIS_TRANSACTION) {
                    systemSetup.doGenesisSetup(dispatch);
                } else if (userTxn.type() == POST_UPGRADE_TRANSACTION) {
                    systemSetup.doPostUpgradeSetup(dispatch);
                }
                hollowAccountCompletions.completeHollowAccounts(userTxn, dispatch);
                dispatchProcessor.processDispatch(dispatch);
                updateWorkflowMetrics(userTxn);
            }
            final var handleOutput = userTxn.stack().buildHandleOutput(userTxn.consensusNow());
            // Note that we don't yet support producing ONLY blocks, because we haven't integrated
            // translators from block items to records for answering queries
            if (blockStreamConfig.streamRecords()) {
                recordCache.add(
                        userTxn.creatorInfo().nodeId(), userTxn.txnInfo().payerID(), handleOutput.recordsOrThrow());
            } else {
                throw new IllegalStateException("Records must be produced directly without block item translators");
            }
            return handleOutput;
        } catch (final Exception e) {
            logger.error("{} - exception thrown while handling user transaction", ALERT_MESSAGE, e);
            return failInvalidStreamItems(userTxn);
        }
    }

    /**
     * Returns a stream of a single {@link ResponseCodeEnum#FAIL_INVALID} record
     * for the given user transaction.
     *
     * @return the failure record
     */
    private HandleOutput failInvalidStreamItems(@NonNull final UserTxn userTxn) {
        userTxn.stack().rollbackFullStack();
        // The stack for the user txn should never be committed
        final List<BlockItem> blockItems = new LinkedList<>();
        final var blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        if (blockStreamConfig.streamBlocks()) {
            final var failInvalidBuilder = new BlockStreamBuilder(REVERSIBLE, NOOP_RECORD_CUSTOMIZER, USER);
            initializeBuilderInfo(failInvalidBuilder, userTxn.txnInfo(), exchangeRateManager.exchangeRates())
                    .status(FAIL_INVALID)
                    .consensusTimestamp(userTxn.consensusNow());
            blockItems.addAll(failInvalidBuilder.build());
        }

        final var failInvalidBuilder = new RecordStreamBuilder(REVERSIBLE, NOOP_RECORD_CUSTOMIZER, USER);
        initializeBuilderInfo(failInvalidBuilder, userTxn.txnInfo(), exchangeRateManager.exchangeRates())
                .status(FAIL_INVALID)
                .consensusTimestamp(userTxn.consensusNow());
        final var failInvalidRecord = failInvalidBuilder.build();
        recordCache.add(
                userTxn.creatorInfo().nodeId(),
                requireNonNull(userTxn.txnInfo().payerID()),
                List.of(failInvalidRecord));
        return new HandleOutput(blockItems, List.of(failInvalidRecord));
    }

    /**
     * Returns true if the software event is older than the current software version.
     *
     * @return true if the software event is older than the current software version
     */
    private boolean isOlderSoftwareEvent(@NonNull final UserTxn userTxn) {
        return this.initTrigger != EVENT_STREAM_RECOVERY
                && SEMANTIC_VERSION_COMPARATOR.compare(version, userTxn.event().getSoftwareVersion()) > 0;
    }

    /**
     * Updates the metrics for the handle workflow.
     */
    private void updateWorkflowMetrics(@NonNull final UserTxn userTxn) {
        if (userTxn.type() == GENESIS_TRANSACTION
                || userTxn.consensusNow().getEpochSecond()
                        > userTxn.lastHandledConsensusTime().getEpochSecond()) {
            handleWorkflowMetrics.switchConsensusSecond();
        }
    }

    /**
     * Returns the user dispatch for the given user transaction.
     *
     * @param userTxn the user transaction
     * @param blockStreamConfig
     * @return the user dispatch
     */
    private Dispatch dispatchFor(@NonNull final UserTxn userTxn, @NonNull final BlockStreamConfig blockStreamConfig) {
        final var baseBuilder =
                initializeBuilderInfo(userTxn.baseBuilder(), userTxn.txnInfo(), exchangeRateManager.exchangeRates());
        return userTxn.newDispatch(
                authorizer,
                networkInfo,
                feeManager,
                dispatchProcessor,
                blockRecordManager,
                serviceScopeLookup,
                storeMetricsService,
                exchangeRateManager,
                childDispatchFactory,
                dispatcher,
                networkUtilizationManager,
                baseBuilder,
                blockStreamConfig);
    }

    /**
     * Initializes the base builder of the given user transaction initialized with its transaction
     * information. The record builder is initialized with the transaction, transaction bytes, transaction ID,
     * exchange rate, and memo.
     *
     * @param builder the base builder
     * @param txnInfo the transaction information
     * @param exchangeRateSet the active exchange rate set
     * @return the initialized base builder
     */
    public static StreamBuilder initializeBuilderInfo(
            @NonNull final StreamBuilder builder,
            @NonNull final TransactionInfo txnInfo,
            @NonNull final ExchangeRateSet exchangeRateSet) {
        final var transaction = txnInfo.transaction();
        // If the transaction uses the legacy body bytes field instead of explicitly
        // setting its signed bytes, the record will have the hash of its bytes as
        // serialized by PBJ
        final Bytes transactionBytes;
        if (transaction.signedTransactionBytes().length() > 0) {
            transactionBytes = transaction.signedTransactionBytes();
        } else {
            transactionBytes = Transaction.PROTOBUF.toBytes(transaction);
        }
        return builder.transaction(txnInfo.transaction())
                .functionality(txnInfo.functionality())
                .serializedTransaction(txnInfo.serializedTransaction())
                .transactionBytes(transactionBytes)
                .transactionID(txnInfo.txBody().transactionIDOrThrow())
                .exchangeRate(exchangeRateSet)
                .memo(txnInfo.txBody().memo());
    }

    private void updateNodeStakes(@NonNull final UserTxn userTxn) {
        try {
            nodeStakeUpdates.process(
                    userTxn.stack(), userTxn.tokenContextImpl(), userTxn.type() == GENESIS_TRANSACTION);
        } catch (final Exception e) {
            // We don't propagate a failure here to avoid a catastrophic scenario
            // where we are "stuck" trying to process node stake updates and never
            // get back to user transactions
            logger.error("Failed to process staking period time hook", e);
        }
    }

    private static void logPreDispatch(@NonNull final UserTxn userTxn) {
        if (logger.isDebugEnabled()) {
            logStartUserTransaction(
                    userTxn.platformTxn(),
                    userTxn.txnInfo().txBody(),
                    requireNonNull(userTxn.txnInfo().payerID()));
            logStartUserTransactionPreHandleResultP2(userTxn.preHandleResult());
            logStartUserTransactionPreHandleResultP3(userTxn.preHandleResult());
        }
    }

    /**
     * Expire schedules that are due to be executed between the last handled
     * transaction time and the current consensus time.
     *
     * @param userTxn the user transaction
     */
    private void expireSchedules(@NonNull UserTxn userTxn) {
        if (userTxn.type() == GENESIS_TRANSACTION) {
            return;
        }
        final var lastHandledTxnTime = userTxn.lastHandledConsensusTime();
        if (userTxn.consensusNow().getEpochSecond() > lastHandledTxnTime.getEpochSecond()) {
            final var firstSecondToExpire = lastHandledTxnTime.getEpochSecond();
            final var lastSecondToExpire = userTxn.consensusNow().getEpochSecond() - 1;
            final var scheduleStore = new WritableStoreFactory(
                            userTxn.stack(), ScheduleService.NAME, userTxn.config(), storeMetricsService)
                    .getStore(WritableScheduleStore.class);
            scheduleStore.purgeExpiredSchedulesBetween(firstSecondToExpire, lastSecondToExpire);
            userTxn.stack().commitSystemStateChanges();
        }
    }

    /**
     * Constructs a new {@link UserTxn} with the scope defined by the
     * current state, platform context, creator, and consensus time.
     *
     * @param state the current state
     * @param event the current consensus event
     * @param creator the creator of the transaction
     * @param txn the consensus transaction
     * @param consensusNow the consensus time
     * @return the new user transaction
     */
    private UserTxn newUserTxn(
            @NonNull final State state,
            @NonNull final ConsensusEvent event,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction txn,
            @NonNull final Instant consensusNow) {
        return UserTxn.from(
                state,
                event,
                creator,
                txn,
                consensusNow,
                blockRecordManager.consTimeOfLastHandledTxn(),
                configProvider,
                storeMetricsService,
                kvStateChangeListener,
                boundaryStateChangeListener,
                preHandleWorkflow);
    }
}
