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
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.app.service.file.impl.schemas.V0490FileSchema.BLOBS_KEY;
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
import static com.hedera.node.app.workflows.handle.TransactionType.ORDINARY_TRANSACTION;
import static com.hedera.node.app.workflows.handle.TransactionType.POST_UPGRADE_TRANSACTION;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.NODE_DUE_DILIGENCE_FAILURE;
import static com.hedera.node.config.types.StreamMode.BLOCKS;
import static com.hedera.node.config.types.StreamMode.RECORDS;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static com.swirlds.state.spi.HapiUtils.SEMANTIC_VERSION_COMPARATOR;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.input.EventHeader;
import com.hedera.hapi.block.stream.input.RoundHeader;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.roster.LedgerId;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.node.app.blocks.impl.BlockStreamBuilder;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeInfoHelper;
import com.hedera.node.app.service.token.impl.handlers.staking.StakePeriodManager;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.RecordSource;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.HederaRecordCache.DueDiligenceFailure;
import com.hedera.node.app.state.recordcache.BlockRecordSource;
import com.hedera.node.app.state.recordcache.LegacyListRecordSource;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.workflows.OpWorkflowMetrics;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.cache.CacheWarmer;
import com.hedera.node.app.workflows.handle.record.RecordStreamBuilder;
import com.hedera.node.app.workflows.handle.record.SystemSetup;
import com.hedera.node.app.workflows.handle.steps.HollowAccountCompletions;
import com.hedera.node.app.workflows.handle.steps.StakePeriodChanges;
import com.hedera.node.app.workflows.handle.steps.UserTxn;
import com.hedera.node.app.workflows.handle.steps.UserTxnFactory;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.types.StreamMode;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.RosterStateId;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.CesEvent;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.State;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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

    private final StreamMode streamMode;
    private final NetworkInfo networkInfo;
    private final StakePeriodChanges stakePeriodChanges;
    private final DispatchProcessor dispatchProcessor;
    private final StoreMetricsService storeMetricsService;
    private final BlockRecordManager blockRecordManager;
    private final BlockStreamManager blockStreamManager;
    private final CacheWarmer cacheWarmer;
    private final OpWorkflowMetrics opWorkflowMetrics;
    private final ThrottleServiceManager throttleServiceManager;
    private final SemanticVersion version;
    private final InitTrigger initTrigger;
    private final HollowAccountCompletions hollowAccountCompletions;
    private final SystemSetup systemSetup;
    private final StakeInfoHelper stakeInfoHelper;
    private final HederaRecordCache recordCache;
    private final ExchangeRateManager exchangeRateManager;
    private final StakePeriodManager stakePeriodManager;
    private final List<StateChanges.Builder> migrationStateChanges;
    private final UserTxnFactory userTxnFactory;
    private final TssBaseService tssBaseService;

    // The last second since the epoch at which the metrics were updated; this does not affect transaction handling
    private long lastMetricUpdateSecond;

    @Inject
    public HandleWorkflow(
            @NonNull final NetworkInfo networkInfo,
            @NonNull final StakePeriodChanges stakePeriodChanges,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final BlockStreamManager blockStreamManager,
            @NonNull final CacheWarmer cacheWarmer,
            @NonNull final OpWorkflowMetrics opWorkflowMetrics,
            @NonNull final ThrottleServiceManager throttleServiceManager,
            @NonNull final SemanticVersion version,
            @NonNull final InitTrigger initTrigger,
            @NonNull final HollowAccountCompletions hollowAccountCompletions,
            @NonNull final SystemSetup systemSetup,
            @NonNull final StakeInfoHelper stakeInfoHelper,
            @NonNull final HederaRecordCache recordCache,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final StakePeriodManager stakePeriodManager,
            @NonNull final List<StateChanges.Builder> migrationStateChanges,
            @NonNull final UserTxnFactory userTxnFactory,
            @NonNull final TssBaseService tssBaseService) {
        this.networkInfo = requireNonNull(networkInfo);
        this.stakePeriodChanges = requireNonNull(stakePeriodChanges);
        this.dispatchProcessor = requireNonNull(dispatchProcessor);
        this.storeMetricsService = requireNonNull(storeMetricsService);
        this.blockRecordManager = requireNonNull(blockRecordManager);
        this.blockStreamManager = requireNonNull(blockStreamManager);
        this.cacheWarmer = requireNonNull(cacheWarmer);
        this.opWorkflowMetrics = requireNonNull(opWorkflowMetrics);
        this.throttleServiceManager = requireNonNull(throttleServiceManager);
        this.version = requireNonNull(version);
        this.initTrigger = requireNonNull(initTrigger);
        this.hollowAccountCompletions = requireNonNull(hollowAccountCompletions);
        this.systemSetup = requireNonNull(systemSetup);
        this.stakeInfoHelper = requireNonNull(stakeInfoHelper);
        this.recordCache = requireNonNull(recordCache);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
        this.stakePeriodManager = requireNonNull(stakePeriodManager);
        this.migrationStateChanges = new ArrayList<>(migrationStateChanges);
        this.userTxnFactory = requireNonNull(userTxnFactory);
        this.streamMode = configProvider
                .getConfiguration()
                .getConfigData(BlockStreamConfig.class)
                .streamMode();
        this.tssBaseService = requireNonNull(tssBaseService);
    }

    /**
     * Handles the next {@link Round}
     *
     * @param state the writable {@link State} that this round will work on
     * @param round the next {@link Round} that needs to be processed
     */
    public void handleRound(@NonNull final State state, @NonNull final Round round) {
        logStartRound(round);
        cacheWarmer.warm(state, round);
        if (streamMode != RECORDS) {
            blockStreamManager.startRound(round, state);
            blockStreamManager.writeItem(BlockItem.newBuilder()
                    .roundHeader(new RoundHeader(round.getRoundNum()))
                    .build());
            if (!migrationStateChanges.isEmpty()) {
                migrationStateChanges.forEach(builder -> blockStreamManager.writeItem(BlockItem.newBuilder()
                        .stateChanges(builder.consensusTimestamp(blockStreamManager.blockTimestamp())
                                .build())
                        .build()));
                migrationStateChanges.clear();
            }
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
        for (final var event : round) {
            if (streamMode != RECORDS) {
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
                            HapiUtils.toString(event.getSoftwareVersion()),
                            HapiUtils.toString(version),
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
                        handlePlatformTransaction(state, event, creator, platformTxn);
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
        // round is the minimum we can do. Note the BlockStreamManager#endRound() method is called in Hedera's
        // implementation of SwirldState#sealConsensusRound(), since the BlockStreamManager cannot do its
        // end-of-block work until the platform has finished all its state changes.
        if (userTransactionsHandled.get() && streamMode != BLOCKS) {
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
     */
    private void handlePlatformTransaction(
            @NonNull final State state,
            @NonNull final ConsensusEvent event,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction txn) {
        final var handleStart = System.nanoTime();

        // Always use platform-assigned time for user transaction, c.f. https://hips.hedera.com/hip/hip-993
        final var consensusNow = txn.getConsensusTimestamp();
        var type = ORDINARY_TRANSACTION;
        stakePeriodManager.setCurrentStakePeriodFor(consensusNow);
        if (streamMode != BLOCKS) {
            final var isBoundary = blockRecordManager.startUserTransaction(consensusNow, state);
            if (streamMode == RECORDS && isBoundary) {
                type = typeOfBoundary(state);
            }
        }
        if (streamMode != RECORDS) {
            type = switch (blockStreamManager.pendingWork()) {
                case GENESIS_WORK -> GENESIS_TRANSACTION;
                case POST_UPGRADE_WORK -> POST_UPGRADE_TRANSACTION;
                default -> ORDINARY_TRANSACTION;};
        }
        final var userTxn = userTxnFactory.createUserTxn(state, event, creator, txn, consensusNow, type);
        final var handleOutput = execute(userTxn);
        if (streamMode != BLOCKS) {
            final var records = ((LegacyListRecordSource) handleOutput.recordSourceOrThrow()).precomputedRecords();
            blockRecordManager.endUserTransaction(records.stream(), state);
        }
        if (streamMode != RECORDS) {
            handleOutput.blockRecordSourceOrThrow().forEachItem(blockStreamManager::writeItem);
        }
        opWorkflowMetrics.updateDuration(userTxn.functionality(), (int) (System.nanoTime() - handleStart));
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
        try {
            final var baseBuilder = initializeBuilderInfo(
                    userTxn.baseBuilder(), userTxn.txnInfo(), exchangeRateManager.exchangeRates());
            final var dispatch = userTxnFactory.createDispatch(userTxn, baseBuilder);
            if (isOlderSoftwareEvent(userTxn)) {
                if (streamMode != BLOCKS) {
                    final var lastRecordManagerTime = blockRecordManager.consTimeOfLastHandledTxn();
                    // This updates consTimeOfLastHandledTxn as a side-effect
                    blockRecordManager.advanceConsensusClock(userTxn.consensusNow(), userTxn.state());
                    if (streamMode == RECORDS) {
                        // If relying on last-handled time to trigger interval processing, do so now
                        processInterval(userTxn, lastRecordManagerTime);
                    }
                }
                initializeBuilderInfo(userTxn.baseBuilder(), userTxn.txnInfo(), exchangeRateManager.exchangeRates())
                        .status(BUSY);
                // Flushes the BUSY builder to the stream, no other side effects
                userTxn.stack().commitTransaction(userTxn.baseBuilder());
            } else {
                if (userTxn.type() == GENESIS_TRANSACTION) {
                    // (FUTURE) Once all genesis setup is done via dispatch, remove this method
                    systemSetup.externalizeInitSideEffects(
                            userTxn.tokenContextImpl(), exchangeRateManager.exchangeRates());
                    // Set the genesis roster in state
                    final var writableStoreFactory = new WritableStoreFactory(
                            userTxn.stack(), RosterStateId.NAME, userTxn.config(), storeMetricsService);
                    final var rosterStore = writableStoreFactory.getStore(WritableRosterStore.class);
                    rosterStore.putActiveRoster(networkInfo.roster(), 1L);
                } else if (userTxn.type() == POST_UPGRADE_TRANSACTION) {
                    final var streamBuilder = stakeInfoHelper.adjustPostUpgradeStakes(
                            userTxn.tokenContextImpl(),
                            networkInfo,
                            userTxn.config(),
                            new WritableStakingInfoStore(userTxn.stack().getWritableStates(TokenService.NAME)),
                            new WritableNetworkStakingRewardsStore(
                                    userTxn.stack().getWritableStates(TokenService.NAME)));
                    if (streamMode != RECORDS) {
                        // Only externalize this if we are streaming blocks
                        streamBuilder.exchangeRate(exchangeRateManager.exchangeRates());
                        userTxn.stack().commitTransaction(streamBuilder);
                    } else {
                        // Only update this if we are relying on RecordManager state for post-upgrade processing
                        blockRecordManager.markMigrationRecordsStreamed();
                    }
                    // C.f. https://github.com/hashgraph/hedera-services/issues/14751,
                    // here we may need to switch the newly adopted candidate roster
                    // in the RosterService state to become the active roster
                    final var ledgerId = getLedgerId(dispatch.handleContext());
                    if (ledgerId != null) {
                        final var rosterStore =
                                dispatch.handleContext().storeFactory().writableStore(WritableRosterStore.class);
                        final var candidateRoster = rosterStore.getCandidateRoster();
                        if (candidateRoster != null) {
                            final long roundNumber = ((CesEvent) userTxn.event()).getRoundReceived();
                            rosterStore.putActiveRoster(candidateRoster, roundNumber);
                        }
                    } else {
                        // If there is NOT an existing ledger ID, adopt the ledger ID i.e. CREATE a ledger ID and store
                        // it in state as “the” ledger ID
                        final var genesisRoster = new Roster(List.of());
                        tssBaseService.bootstrapLedgerId(genesisRoster, dispatch.handleContext(), id -> {});
                    }
                }
                updateNodeStakes(userTxn, dispatch);
                var lastRecordManagerTime = Instant.EPOCH;
                if (streamMode != BLOCKS) {
                    lastRecordManagerTime = blockRecordManager.consTimeOfLastHandledTxn();
                    // This updates consTimeOfLastHandledTxn as a side-effect
                    blockRecordManager.advanceConsensusClock(userTxn.consensusNow(), userTxn.state());
                }
                if (streamMode == RECORDS) {
                    processInterval(userTxn, lastRecordManagerTime);
                } else {
                    if (processInterval(userTxn, blockStreamManager.lastIntervalProcessTime())) {
                        blockStreamManager.setLastIntervalProcessTime(userTxn.consensusNow());
                    }
                }
                logPreDispatch(userTxn);
                if (userTxn.type() != ORDINARY_TRANSACTION) {
                    if (userTxn.type() == GENESIS_TRANSACTION) {
                        logger.info("Doing genesis setup @ {}", userTxn.consensusNow());
                        systemSetup.doGenesisSetup(dispatch);
                    } else if (userTxn.type() == POST_UPGRADE_TRANSACTION) {
                        logger.info("Doing post-upgrade setup @ {}", userTxn.consensusNow());
                        systemSetup.doPostUpgradeSetup(dispatch);
                    }
                    if (streamMode != RECORDS) {
                        blockStreamManager.confirmPendingWorkFinished();
                    }
                }
                hollowAccountCompletions.completeHollowAccounts(userTxn, dispatch);
                dispatchProcessor.processDispatch(dispatch);
                updateWorkflowMetrics(userTxn);
            }
            final var handleOutput =
                    userTxn.stack().buildHandleOutput(userTxn.consensusNow(), exchangeRateManager.exchangeRates());
            final var dueDiligenceFailure = userTxn.preHandleResult().status() == NODE_DUE_DILIGENCE_FAILURE
                    ? DueDiligenceFailure.YES
                    : DueDiligenceFailure.NO;
            recordCache.addRecordSource(
                    userTxn.creatorInfo().nodeId(),
                    userTxn.txnInfo().transactionID(),
                    dueDiligenceFailure,
                    handleOutput.preferringBlockRecordSource());
            return handleOutput;
        } catch (final Exception e) {
            logger.error("{} - exception thrown while handling user transaction", ALERT_MESSAGE, e);
            return failInvalidStreamItems(userTxn);
        }
    }

    private static @Nullable LedgerId getLedgerId(HandleContext context) {
        // FUTURE: lookup the ledger id from the state described in
        // https://github.com/hashgraph/hedera-services/blob/develop/platform-sdk/docs/proposals/TSS-Ledger-Id/TSS-Ledger-Id.md#ledger-id-queue
        return LedgerId.DEFAULT;
    }

    /**
     * Returns a stream of a single {@link ResponseCodeEnum#FAIL_INVALID} record
     * for the given user transaction.
     *
     * @return the failure record
     */
    private HandleOutput failInvalidStreamItems(@NonNull final UserTxn userTxn) {
        // The stack for the user txn should never be committed
        userTxn.stack().rollbackFullStack();

        RecordSource cacheableRecordSource = null;
        final RecordSource recordSource;
        if (streamMode != BLOCKS) {
            final var failInvalidBuilder = new RecordStreamBuilder(REVERSIBLE, NOOP_RECORD_CUSTOMIZER, USER);
            initializeBuilderInfo(failInvalidBuilder, userTxn.txnInfo(), exchangeRateManager.exchangeRates())
                    .status(FAIL_INVALID)
                    .consensusTimestamp(userTxn.consensusNow());
            final var failInvalidRecord = failInvalidBuilder.build();
            cacheableRecordSource = recordSource = new LegacyListRecordSource(
                    List.of(failInvalidRecord),
                    List.of(new RecordSource.IdentifiedReceipt(
                            failInvalidRecord.transactionRecord().transactionIDOrThrow(),
                            failInvalidRecord.transactionRecord().receiptOrThrow())));
        } else {
            recordSource = null;
        }
        final BlockRecordSource blockRecordSource;
        if (streamMode != RECORDS) {
            final List<BlockStreamBuilder.Output> outputs = new LinkedList<>();
            final var failInvalidBuilder = new BlockStreamBuilder(REVERSIBLE, NOOP_RECORD_CUSTOMIZER, USER);
            initializeBuilderInfo(failInvalidBuilder, userTxn.txnInfo(), exchangeRateManager.exchangeRates())
                    .status(FAIL_INVALID)
                    .consensusTimestamp(userTxn.consensusNow());
            outputs.add(failInvalidBuilder.build());
            cacheableRecordSource = blockRecordSource = new BlockRecordSource(outputs);
        } else {
            blockRecordSource = null;
        }

        recordCache.addRecordSource(
                userTxn.creatorInfo().nodeId(),
                userTxn.txnInfo().transactionID(),
                DueDiligenceFailure.NO,
                requireNonNull(cacheableRecordSource));
        return new HandleOutput(blockRecordSource, recordSource);
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
        if (userTxn.type() == GENESIS_TRANSACTION || userTxn.consensusNow().getEpochSecond() > lastMetricUpdateSecond) {
            opWorkflowMetrics.switchConsensusSecond();
            lastMetricUpdateSecond = userTxn.consensusNow().getEpochSecond();
        }
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

    private void updateNodeStakes(@NonNull final UserTxn userTxn, @NonNull final Dispatch dispatch) {
        try {
            stakePeriodChanges.process(
                    dispatch,
                    userTxn.stack(),
                    userTxn.tokenContextImpl(),
                    streamMode,
                    userTxn.type() == GENESIS_TRANSACTION,
                    blockStreamManager.lastIntervalProcessTime());
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
                    userTxn.consensusNow(),
                    userTxn.txnInfo().txBody(),
                    requireNonNull(userTxn.txnInfo().payerID()));
            logStartUserTransactionPreHandleResultP2(userTxn.preHandleResult());
            logStartUserTransactionPreHandleResultP3(userTxn.preHandleResult());
        }
    }

    /**
     * Process all time-based events that are due since the last processing time.
     *
     * @param userTxn the user transaction
     * @param lastProcessTime an upper bound on the last time that time-based events were processed
     * @return true if the interval was processed
     */
    private boolean processInterval(@NonNull final UserTxn userTxn, final Instant lastProcessTime) {
        // If we have never processed an interval, treat this time as the last processed time
        if (Instant.EPOCH.equals(lastProcessTime)) {
            return true;
        } else if (lastProcessTime.getEpochSecond() < userTxn.consensusNow().getEpochSecond()) {
            // There is at least one unprocessed second since the last processing time
            final var startSecond = lastProcessTime.getEpochSecond();
            final var endSecond = userTxn.consensusNow().getEpochSecond() - 1;
            final var scheduleStore = new WritableStoreFactory(
                            userTxn.stack(), ScheduleService.NAME, userTxn.config(), storeMetricsService)
                    .getStore(WritableScheduleStore.class);
            scheduleStore.purgeExpiredSchedulesBetween(startSecond, endSecond);
            userTxn.stack().commitSystemStateChanges();
            return true;
        }
        return false;
    }

    /**
     * Returns the type of transaction encountering the given state at a block boundary.
     *
     * @param state the boundary state
     * @return the type of the boundary transaction
     */
    private TransactionType typeOfBoundary(@NonNull final State state) {
        final var files = state.getReadableStates(FileService.NAME).get(BLOBS_KEY);
        // The files map is empty only at genesis
        if (files.size() == 0) {
            return GENESIS_TRANSACTION;
        }
        final var blockInfo = state.getReadableStates(BlockRecordService.NAME)
                .<BlockInfo>getSingleton(BLOCK_INFO_STATE_KEY)
                .get();
        return !requireNonNull(blockInfo).migrationRecordsStreamed() ? POST_UPGRADE_TRANSACTION : ORDINARY_TRANSACTION;
    }
}
