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

import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static com.hedera.node.app.spi.HapiUtils.isHollow;
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult.NO_DUPLICATE;
import static com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult.SAME_NODE;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartEvent;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartRound;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransaction;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP2;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP3;
import static com.hedera.node.app.throttle.ThrottleAccumulator.isGasThrottled;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.NODE_DUE_DILIGENCE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PRE_HANDLE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulatorImpl;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.ChildRecordFinalizer;
import com.hedera.node.app.service.token.records.CryptoUpdateRecordBuilder;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.InsufficientNonFeeDebitsException;
import com.hedera.node.app.spi.workflows.InsufficientServiceFeeException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.SynchronizedThrottleAccumulator;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionChecker.RequireMinValidLifetimeBuffer;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.GenesisRecordsConsensusHook;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.ConsensusConfig;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private static final Set<HederaFunctionality> DISPATCHING_CONTRACT_TRANSACTIONS =
            EnumSet.of(HederaFunctionality.CONTRACT_CREATE, HederaFunctionality.CONTRACT_CALL, ETHEREUM_TRANSACTION);
    private final NetworkInfo networkInfo;
    private final PreHandleWorkflow preHandleWorkflow;
    private final TransactionDispatcher dispatcher;
    private final BlockRecordManager blockRecordManager;
    private final TransactionChecker checker;
    private final ServiceScopeLookup serviceScopeLookup;
    private final ConfigProvider configProvider;
    private final HederaRecordCache recordCache;
    private final GenesisRecordsConsensusHook genesisRecordsTimeHook;
    private final StakingPeriodTimeHook stakingPeriodTimeHook;
    private final ScheduleExpirationHook scheduleExpirationHook;
    private final FeeManager feeManager;
    private final ExchangeRateManager exchangeRateManager;
    private final ChildRecordFinalizer childRecordFinalizer;
    private final ParentRecordFinalizer transactionFinalizer;
    private final SystemFileUpdateFacility systemFileUpdateFacility;
    private final PlatformStateUpdateFacility platformStateUpdateFacility;
    private final SolvencyPreCheck solvencyPreCheck;
    private final Authorizer authorizer;
    private final NetworkUtilizationManager networkUtilizationManager;
    private final SynchronizedThrottleAccumulator synchronizedThrottleAccumulator;
    private final CacheWarmer cacheWarmer;
    private final HandleWorkflowMetrics handleWorkflowMetrics;
    private final ThrottleServiceManager throttleServiceManager;

    @Inject
    public HandleWorkflow(
            @NonNull final NetworkInfo networkInfo,
            @NonNull final PreHandleWorkflow preHandleWorkflow,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final TransactionChecker checker,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final ConfigProvider configProvider,
            @NonNull final HederaRecordCache recordCache,
            @NonNull final GenesisRecordsConsensusHook genesisRecordsTimeHook,
            @NonNull final StakingPeriodTimeHook stakingPeriodTimeHook,
            @NonNull final FeeManager feeManager,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final ChildRecordFinalizer childRecordFinalizer,
            @NonNull final ParentRecordFinalizer transactionFinalizer,
            @NonNull final SystemFileUpdateFacility systemFileUpdateFacility,
            @NonNull final PlatformStateUpdateFacility platformStateUpdateFacility,
            @NonNull final SolvencyPreCheck solvencyPreCheck,
            @NonNull final Authorizer authorizer,
            @NonNull final NetworkUtilizationManager networkUtilizationManager,
            @NonNull final SynchronizedThrottleAccumulator synchronizedThrottleAccumulator,
            @NonNull final ScheduleExpirationHook scheduleExpirationHook,
            @NonNull final CacheWarmer cacheWarmer,
            @NonNull final HandleWorkflowMetrics handleWorkflowMetrics,
            @NonNull final ThrottleServiceManager throttleServiceManager) {
        this.networkInfo = requireNonNull(networkInfo, "networkInfo must not be null");
        this.preHandleWorkflow = requireNonNull(preHandleWorkflow, "preHandleWorkflow must not be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null");
        this.blockRecordManager = requireNonNull(blockRecordManager, "recordManager must not be null");
        this.checker = requireNonNull(checker, "checker must not be null");
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup, "serviceScopeLookup must not be null");
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.recordCache = requireNonNull(recordCache, "recordCache must not be null");
        this.genesisRecordsTimeHook = requireNonNull(genesisRecordsTimeHook, "genesisRecordsTimeHook must not be null");
        this.stakingPeriodTimeHook = requireNonNull(stakingPeriodTimeHook, "stakingPeriodTimeHook must not be null");
        this.feeManager = requireNonNull(feeManager, "feeManager must not be null");
        this.exchangeRateManager = requireNonNull(exchangeRateManager, "exchangeRateManager must not be null");
        this.childRecordFinalizer = childRecordFinalizer;
        this.transactionFinalizer = requireNonNull(transactionFinalizer, "transactionFinalizer must not be null");
        this.systemFileUpdateFacility =
                requireNonNull(systemFileUpdateFacility, "systemFileUpdateFacility must not be null");
        this.platformStateUpdateFacility =
                requireNonNull(platformStateUpdateFacility, "platformStateUpdateFacility must not be null");
        this.solvencyPreCheck = requireNonNull(solvencyPreCheck, "solvencyPreCheck must not be null");
        this.authorizer = requireNonNull(authorizer, "authorizer must not be null");
        this.networkUtilizationManager =
                requireNonNull(networkUtilizationManager, "networkUtilizationManager must not be null");
        this.synchronizedThrottleAccumulator =
                requireNonNull(synchronizedThrottleAccumulator, "synchronizedThrottleAccumulator must not be null");
        this.scheduleExpirationHook = requireNonNull(scheduleExpirationHook, "scheduleExpirationHook must not be null");
        this.cacheWarmer = requireNonNull(cacheWarmer, "cacheWarmer must not be null");
        this.handleWorkflowMetrics = requireNonNull(handleWorkflowMetrics, "handleWorkflowMetrics must not be null");
        this.throttleServiceManager = requireNonNull(throttleServiceManager, "throttleServiceManager must not be null");
    }

    /**
     * Handles the next {@link Round}
     *
     * @param state the writable {@link HederaState} that this round will work on
     * @param round the next {@link Round} that needs to be processed
     */
    public void handleRound(
            @NonNull final HederaState state, @NonNull final PlatformState platformState, @NonNull final Round round) {
        // Keep track of whether any user transactions were handled. If so, then we will need to close the round
        // with the block record manager.
        final var userTransactionsHandled = new AtomicBoolean(false);

        // log start of round to transaction state log
        logStartRound(round);

        // warm the cache
        cacheWarmer.warm(state, round);

        // handle each event in the round
        for (final ConsensusEvent event : round) {
            final var creator = networkInfo.nodeInfo(event.getCreatorId().id());
            if (creator == null) {
                // We were given an event for a node that *does not exist in the address book*. This will be logged as
                // a warning, as this should never happen, and we will skip the event. The platform should guarantee
                // that we never receive an event that isn't associated with the address book, and every node in the
                // address book must have an account ID, since you cannot delete an account belonging to a node, and
                // you cannot change the address book non-deterministically.
                logger.warn("Received event from node {} which is not in the address book", event.getCreatorId());
                return;
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
                        handlePlatformTransaction(state, platformState, event, creator, platformTxn);
                    }
                } catch (final Exception e) {
                    logger.fatal(
                            "Possibly CATASTROPHIC failure while running the handle workflow. "
                                    + "While this node may not die right away, it is in a bad way, most likely fatally.",
                            e);
                }
            }
        }

        // Inform the BlockRecordManager that the round is complete, so it can update running-hashes in state
        // that have been being computed in background threads. The running hash has to be included in
        // state, but we want to synchronize with background threads as infrequently as possible. So once per
        // round is the minimum we can do.
        if (userTransactionsHandled.get()) {
            blockRecordManager.endRound(state);
        }
    }

    private void handlePlatformTransaction(
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn) {
        // Get the consensus timestamp. FUTURE We want this to exactly match the consensus timestamp from the hashgraph,
        // but for compatibility with the current implementation, we adjust it as follows.
        final Instant consensusNow = platformTxn.getConsensusTimestamp().minusNanos(1000 - 3L);

        // handle user transaction
        handleUserTransaction(consensusNow, state, platformState, platformEvent, creator, platformTxn);
    }

    private void handleUserTransaction(
            @NonNull final Instant consensusNow,
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn) {
        // Determine if this is the first transaction after startup. This needs to be determined BEFORE starting the
        // user transaction
        final var consTimeOfLastHandledTxn = blockRecordManager.consTimeOfLastHandledTxn();
        final var isFirstTransaction = !consTimeOfLastHandledTxn.isAfter(Instant.EPOCH);

        // Setup record builder list
        final boolean switchedBlocks = blockRecordManager.startUserTransaction(consensusNow, state);
        final var recordListBuilder = new RecordListBuilder(consensusNow);
        final var recordBuilder = recordListBuilder.userTransactionRecordBuilder();

        // Setup helpers
        final var configuration = configProvider.getConfiguration();
        final var stack = new SavepointStackImpl(state);
        final var readableStoreFactory = new ReadableStoreFactory(stack);
        final var feeAccumulator = createFeeAccumulator(stack, configuration, recordBuilder);

        final var tokenServiceContext =
                new TokenContextImpl(configuration, stack, recordListBuilder, blockRecordManager, isFirstTransaction);
        // It's awful that we have to check this every time a transaction is handled, especially since this mostly
        // applies to non-production cases. Let's find a way to 💥💥 remove this 💥💥
        genesisRecordsTimeHook.process(tokenServiceContext);
        try {
            // If this is the first user transaction after midnight, then handle staking updates prior to handling the
            // transaction itself.
            stakingPeriodTimeHook.process(stack, tokenServiceContext);
        } catch (final Exception e) {
            // If anything goes wrong, we log the error and continue
            logger.error("Failed to process staking period time hook", e);
        }

        // Consensus hooks have now had a chance to publish any records from migrations; therefore we can begin handling
        // the user transaction
        blockRecordManager.advanceConsensusClock(consensusNow, state);
        // Look for any expired schedules and delete them when new block is created
        if (switchedBlocks) {
            final var firstSecondToExpire =
                    blockRecordManager.firstConsTimeOfLastBlock().getEpochSecond();
            final var lastSecondToExpire = consensusNow.getEpochSecond();
            final var scheduleStore =
                    new WritableStoreFactory(stack, ScheduleService.NAME).getStore(WritableScheduleStore.class);
            // purge all expired schedules between the first consensus time of last block and the current consensus time
            scheduleExpirationHook.processExpiredSchedules(scheduleStore, firstSecondToExpire, lastSecondToExpire);
        }

        final long handleStart = System.nanoTime();
        TransactionBody txBody;
        AccountID payer = null;
        Fees fees = null;
        TransactionInfo transactionInfo = null;
        Set<AccountID> prePaidRewardReceivers = emptySet();
        try {
            final var preHandleResult = getCurrentPreHandleResult(readableStoreFactory, creator, platformTxn);

            transactionInfo = preHandleResult.txInfo();

            if (transactionInfo == null) {
                // FUTURE: Charge node generic penalty, set values in record builder, and remove log statement
                logger.error("Bad transaction from creator {}", creator);
                return;
            }

            // Get the parsed data
            final var transaction = transactionInfo.transaction();
            txBody = transactionInfo.txBody();
            payer = transactionInfo.payerID();

            final Bytes transactionBytes;
            if (transaction.signedTransactionBytes().length() > 0) {
                transactionBytes = transaction.signedTransactionBytes();
            } else {
                // in this case, recorder hash the transaction itself, not its bodyBytes.
                transactionBytes = Transaction.PROTOBUF.toBytes(transaction);
            }

            // Log start of user transaction to transaction state log
            logStartUserTransaction(platformTxn, txBody, payer);
            logStartUserTransactionPreHandleResultP2(preHandleResult);
            logStartUserTransactionPreHandleResultP3(preHandleResult);

            // Initialize record builder list
            recordBuilder
                    .transaction(transactionInfo.transaction())
                    .transactionBytes(transactionBytes)
                    .transactionID(transactionInfo.transactionID())
                    .exchangeRate(exchangeRateManager.exchangeRates())
                    .memo(txBody.memo());

            // Set up the verifier
            final var hederaConfig = configuration.getConfigData(HederaConfig.class);
            final var legacyFeeCalcNetworkVpt =
                    transactionInfo.signatureMap().sigPairOrElse(emptyList()).size();
            final var verifier = new DefaultKeyVerifier(
                    legacyFeeCalcNetworkVpt, hederaConfig, preHandleResult.getVerificationResults());
            final var signatureMapSize = SignatureMap.PROTOBUF.measureRecord(transactionInfo.signatureMap());

            // Setup context
            final var context = new HandleContextImpl(
                    txBody,
                    transactionInfo.functionality(),
                    signatureMapSize,
                    payer,
                    preHandleResult.getPayerKey(),
                    networkInfo,
                    TransactionCategory.USER,
                    recordBuilder,
                    stack,
                    configuration,
                    verifier,
                    recordListBuilder,
                    checker,
                    dispatcher,
                    serviceScopeLookup,
                    blockRecordManager,
                    recordCache,
                    feeManager,
                    exchangeRateManager,
                    consensusNow,
                    authorizer,
                    solvencyPreCheck,
                    childRecordFinalizer,
                    transactionFinalizer,
                    networkUtilizationManager,
                    synchronizedThrottleAccumulator,
                    platformState);

            // Calculate the fee
            fees = dispatcher.dispatchComputeFees(context);

            // Run all pre-checks
            final var validationResult = validate(
                    consensusNow,
                    verifier,
                    preHandleResult,
                    readableStoreFactory,
                    networkUtilizationManager,
                    context,
                    dispatcher,
                    stack,
                    platformEvent.getCreatorId().id());

            final var hasWaivedFees = authorizer.hasWaivedFees(payer, transactionInfo.functionality(), txBody);
            if (validationResult.status() != SO_FAR_SO_GOOD) {
                recordBuilder.status(validationResult.responseCodeEnum());
                try {
                    // If the payer is authorized to waive fees, then we don't charge them
                    if (!hasWaivedFees) {
                        if (validationResult.status() == NODE_DUE_DILIGENCE_FAILURE) {
                            feeAccumulator.chargeNetworkFee(creator.accountId(), fees.networkFee());
                        } else if (validationResult.status() == PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE) {
                            // We do not charge partial service fees; if the payer is unwilling or unable to cover
                            // the entire service fee, then we only charge network and node fees (prioritizing
                            // the network fee in case of a very low payer balance)
                            feeAccumulator.chargeFees(payer, creator.accountId(), fees.withoutServiceComponent());
                        } else {
                            final var feesToCharge =
                                    validationResult.responseCodeEnum().equals(DUPLICATE_TRANSACTION)
                                            ? fees.withoutServiceComponent()
                                            : fees;
                            feeAccumulator.chargeFees(payer, creator.accountId(), feesToCharge);
                        }
                    }
                } catch (final HandleException ex) {
                    final var identifier = validationResult.status == NODE_DUE_DILIGENCE_FAILURE
                            ? "node " + creator.nodeId()
                            : "account " + payer;
                    logger.error(
                            "Unable to charge {} a penalty after {} happened. Cause of the failed charge:",
                            identifier,
                            validationResult.responseCodeEnum,
                            ex);
                }
            } else {
                try {
                    // Any hollow accounts that must sign to have all needed signatures, need to be finalized
                    // as a result of transaction being handled.
                    Set<Account> hollowAccounts = preHandleResult.getHollowAccounts();
                    SignatureVerification maybeEthTxVerification = null;
                    if (transactionInfo.functionality() == ETHEREUM_TRANSACTION) {
                        final var maybeEthTxSigs = CONTRACT_SERVICE
                                .handlers()
                                .ethereumTransactionHandler()
                                .maybeEthTxSigsFor(
                                        transactionInfo.txBody().ethereumTransactionOrThrow(),
                                        readableStoreFactory.getStore(ReadableFileStore.class),
                                        configuration);
                        if (maybeEthTxSigs != null) {
                            final var alias = Bytes.wrap(maybeEthTxSigs.address());
                            final var accountStore = readableStoreFactory.getStore(ReadableAccountStore.class);
                            final var maybeHollowAccountId = accountStore.getAccountIDByAlias(alias);
                            if (maybeHollowAccountId != null) {
                                final var maybeHollowAccount =
                                        requireNonNull(accountStore.getAccountById(maybeHollowAccountId));
                                if (isHollow(maybeHollowAccount)) {
                                    hollowAccounts = new LinkedHashSet<>(preHandleResult.getHollowAccounts());
                                    hollowAccounts.add(maybeHollowAccount);
                                    maybeEthTxVerification = new SignatureVerificationImpl(
                                            Key.newBuilder()
                                                    .ecdsaSecp256k1(Bytes.wrap(maybeEthTxSigs.publicKey()))
                                                    .build(),
                                            alias,
                                            true);
                                }
                            }
                        }
                    }
                    finalizeHollowAccounts(context, configuration, hollowAccounts, verifier, maybeEthTxVerification);

                    // If the payer is authorized to waive fees, then we don't charge them
                    if (!hasWaivedFees) {
                        // privileged transactions are not charged fees
                        feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                    }

                    if (networkUtilizationManager.wasLastTxnGasThrottled()) {
                        // Don't charge the payer the service fee component, because the user-submitted transaction
                        // was fully valid but network capacity was unavailable to satisfy it
                        fees = fees.withoutServiceComponent();
                        throw new HandleException(CONSENSUS_GAS_EXHAUSTED);
                    }

                    // Dispatch the transaction to the handler
                    dispatcher.dispatchHandle(context);
                    // Possibly charge assessed fees for preceding child transactions; but
                    // only if not a contract operation, since these dispatches were already
                    // charged using gas. [FUTURE - stop setting transactionFee in recordBuilder
                    // at the point of dispatch, so we no longer need this special case here.]
                    final var isContractOp =
                            DISPATCHING_CONTRACT_TRANSACTIONS.contains(transactionInfo.functionality());
                    if (!isContractOp
                            && !recordListBuilder.precedingRecordBuilders().isEmpty()) {
                        // We intentionally charge fees even if the transaction failed (may need to update
                        // mono-service to this behavior?)
                        final var childFees = recordListBuilder.precedingRecordBuilders().stream()
                                .mapToLong(SingleTransactionRecordBuilderImpl::transactionFee)
                                .sum();
                        // If the payer is authorized to waive fees, then we don't charge them
                        if (!hasWaivedFees && !feeAccumulator.chargeNetworkFee(payer, childFees)) {
                            throw new HandleException(INSUFFICIENT_PAYER_BALANCE);
                        }
                    }
                    recordBuilder.status(SUCCESS);
                    // Only ScheduleCreate and ScheduleSign can trigger paid staking rewards via
                    // dispatch; and only if this top-level transaction was successful
                    prePaidRewardReceivers = context.dispatchPaidStakerIds();

                    // Notify responsible facility if system-file was uploaded.
                    // Returns SUCCESS if no system-file was uploaded
                    final var fileUpdateResult = systemFileUpdateFacility.handleTxBody(stack, txBody);

                    recordBuilder
                            .exchangeRate(exchangeRateManager.exchangeRates())
                            .status(fileUpdateResult);

                    // Notify if platform state was updated
                    platformStateUpdateFacility.handleTxBody(stack, platformState, txBody);

                } catch (final HandleException e) {
                    // In case of a ContractCall when it reverts, the gas charged should not be rolled back
                    rollback(e.shouldRollbackStack(), e.getStatus(), stack, recordListBuilder);
                    if (!hasWaivedFees && e.shouldRollbackStack()) {
                        // Only re-charge fees if we rolled back the stack
                        feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                    }
                }
            }
        } catch (final Exception e) {
            logger.error("Possibly CATASTROPHIC failure while handling a user transaction", e);
            // We should always rollback stack including gas charges when there is an unexpected exception
            rollback(true, ResponseCodeEnum.FAIL_INVALID, stack, recordListBuilder);
            if (payer != null && fees != null) {
                try {
                    feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                } catch (final Exception chargeException) {
                    logger.error(
                            "Unable to charge account {} a penalty after an unexpected exception {}. Cause of the failed charge:",
                            payer,
                            e,
                            chargeException);
                }
            }
        }

        if (isFirstTransaction || consensusNow.getEpochSecond() > consTimeOfLastHandledTxn.getEpochSecond()) {
            handleWorkflowMetrics.switchConsensusSecond();
        }

        // After a contract operation was handled (i.e., not throttled), update the
        // gas throttle by leaking any unused gas
        if (isGasThrottled(transactionInfo.functionality())
                && recordBuilder.status() != CONSENSUS_GAS_EXHAUSTED
                && recordBuilder.hasContractResult()) {
            final var gasUsed = recordBuilder.getGasUsedForContractTxn();
            handleWorkflowMetrics.addGasUsed(gasUsed);
            final var contractsConfig = configuration.getConfigData(ContractsConfig.class);
            if (contractsConfig.throttleThrottleByGas()) {
                final var gasLimitForContractTx =
                        getGasLimitForContractTx(transactionInfo.txBody(), transactionInfo.functionality());
                final var excessAmount = gasLimitForContractTx - gasUsed;
                networkUtilizationManager.leakUnusedGasPreviouslyReserved(transactionInfo, excessAmount);
            }
        }

        throttleServiceManager.saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
        final var function = transactionInfo.functionality();
        transactionFinalizer.finalizeParentRecord(
                payer,
                tokenServiceContext,
                function,
                extraRewardReceivers(transactionInfo, recordBuilder),
                prePaidRewardReceivers);

        // Commit all state changes
        stack.commitFullStack();

        // store all records at once, build() records end of transaction to log
        final var recordListResult = recordListBuilder.build();
        recordCache.add(creator.nodeId(), payer, recordListResult.records());

        blockRecordManager.endUserTransaction(recordListResult.records().stream(), state);

        final int handleDuration = (int) (System.nanoTime() - handleStart);
        handleWorkflowMetrics.updateTransactionDuration(transactionInfo.functionality(), handleDuration);
    }

    /**
     * Returns a set of "extra" account ids that should be considered as eligible for
     * collecting their accrued staking rewards with the given transaction info and
     * record builder.
     *
     * <p><b>IMPORTANT:</b> Needed only for mono-service fidelity.
     *
     * <p>There are three cases, none of which HIP-406 defined as a reward situation;
     * but were "false positives" in the original mono-service implementation:
     * <ol>
     *     <li>For a crypto transfer, any account explicitly listed in the HBAR
     *     transfer list, even with a zero balance adjustment.</li>
     *     <li>For a contract operation, any called contract.</li>
     *     <li>For a contract operation, any account loaded in a child
     *     transaction (primarily, any account involved in a child
     *     token transfer).</li>
     * </ol>
     *
     * @param transactionInfo the transaction info
     * @param recordBuilder the record builder
     * @return the set of extra account ids
     */
    static Set<AccountID> extraRewardReceivers(
            @NonNull final TransactionInfo transactionInfo,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        return extraRewardReceivers(transactionInfo.txBody(), transactionInfo.functionality(), recordBuilder);
    }

    static Set<AccountID> extraRewardReceivers(
            @NonNull final TransactionBody body,
            @NonNull final HederaFunctionality function,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        if (recordBuilder.status() != SUCCESS) {
            return emptySet();
        }
        return switch (function) {
            case CRYPTO_TRANSFER -> zeroAdjustIdsFrom(body.cryptoTransferOrThrow()
                    .transfersOrElse(TransferList.DEFAULT)
                    .accountAmountsOrElse(emptyList()));
            case ETHEREUM_TRANSACTION, CONTRACT_CALL, CONTRACT_CREATE -> recordBuilder.explicitRewardSituationIds();
            default -> emptySet();
        };
    }

    /**
     * Returns any ids from the given list of explicit hbar adjustments that have a zero amount.
     *
     * @param explicitHbarAdjustments the list of explicit hbar adjustments
     * @return the set of account ids that have a zero amount
     */
    private static @NonNull Set<AccountID> zeroAdjustIdsFrom(
            @NonNull final List<AccountAmount> explicitHbarAdjustments) {
        Set<AccountID> zeroAdjustmentAccounts = null;
        for (final var aa : explicitHbarAdjustments) {
            if (aa.amount() == 0) {
                if (zeroAdjustmentAccounts == null) {
                    zeroAdjustmentAccounts = new LinkedHashSet<>();
                }
                zeroAdjustmentAccounts.add(aa.accountID());
            }
        }
        return zeroAdjustmentAccounts == null ? emptySet() : zeroAdjustmentAccounts;
    }

    /**
     * Updates key on the hollow accounts that need to be finalized. This is done by dispatching a preceding
     * synthetic update transaction. The ksy is derived from the signature expansion, by looking up the ECDSA key
     * for the alias.
     *
     * @param context the handle context
     * @param configuration the configuration
     * @param accounts the set of hollow accounts that need to be finalized
     * @param verifier the key verifier
     * @param ethTxVerification
     */
    private void finalizeHollowAccounts(
            @NonNull final HandleContext context,
            @NonNull final Configuration configuration,
            @NonNull final Set<Account> accounts,
            @NonNull final DefaultKeyVerifier verifier,
            @Nullable SignatureVerification ethTxVerification) {
        final var consensusConfig = configuration.getConfigData(ConsensusConfig.class);
        final var precedingHollowAccountRecords = accounts.size();
        final var maxRecords = consensusConfig.handleMaxPrecedingRecords();
        // If the hollow accounts that need to be finalized is greater than the max preceding
        // records allowed throw an exception
        if (precedingHollowAccountRecords >= maxRecords) {
            throw new HandleException(MAX_CHILD_RECORDS_EXCEEDED);
        } else {
            for (final var hollowAccount : accounts) {
                if (hollowAccount.accountIdOrElse(AccountID.DEFAULT).equals(AccountID.DEFAULT)) {
                    // The CryptoCreateHandler uses a "hack" to validate that a CryptoCreate with
                    // an EVM address has signed with that alias's ECDSA key; that is, it adds a
                    // dummy "hollow account" with the EVM address as an alias. But we don't want
                    // to try to finalize such a dummy account, so skip it here.
                    continue;
                }
                // get the verified key for this hollow account
                final var verification =
                        ethTxVerification != null && hollowAccount.alias().equals(ethTxVerification.evmAlias())
                                ? ethTxVerification
                                : requireNonNull(
                                        verifier.verificationFor(hollowAccount.alias()),
                                        "Required hollow account verified signature did not exist");
                if (verification.key() != null) {
                    if (!IMMUTABILITY_SENTINEL_KEY.equals(hollowAccount.keyOrThrow())) {
                        logger.error("Hollow account {} has a key other than the sentinel key", hollowAccount);
                        return;
                    }
                    // dispatch synthetic update transaction for updating key on this hollow account
                    final var syntheticUpdateTxn = TransactionBody.newBuilder()
                            .cryptoUpdateAccount(CryptoUpdateTransactionBody.newBuilder()
                                    .accountIDToUpdate(hollowAccount.accountId())
                                    .key(verification.key())
                                    .build())
                            .build();
                    // Note the null key verification callback below; we bypass signature
                    // verifications when doing hollow account finalization
                    final var recordBuilder = context.dispatchPrecedingTransaction(
                            syntheticUpdateTxn, CryptoUpdateRecordBuilder.class, null, context.payer());
                    // For some reason update accountId is set only for the hollow account finalizations and not
                    // for top level crypto update transactions. So we set it here.
                    recordBuilder.accountID(hollowAccount.accountId());
                }
            }
        }
    }

    @NonNull
    private FeeAccumulator createFeeAccumulator(
            @NonNull final SavepointStackImpl stack,
            @NonNull final Configuration configuration,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        final var serviceApiFactory = new ServiceApiFactory(stack, configuration);
        final var tokenApi = serviceApiFactory.getApi(TokenServiceApi.class);
        return new FeeAccumulatorImpl(tokenApi, recordBuilder);
    }

    private static long getGasLimitForContractTx(final TransactionBody txnBody, final HederaFunctionality function) {
        return switch (function) {
            case CONTRACT_CREATE -> txnBody.contractCreateInstance().gas();
            case CONTRACT_CALL -> txnBody.contractCall().gas();
            case ETHEREUM_TRANSACTION -> EthTxData.populateEthTxData(
                            txnBody.ethereumTransaction().ethereumData().toByteArray())
                    .gasLimit();
            default -> 0L;
        };
    }

    private ValidationResult validate(
            @NonNull final Instant consensusNow,
            @NonNull final KeyVerifier verifier,
            @NonNull final PreHandleResult preHandleResult,
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final NetworkUtilizationManager utilizationManager,
            @NonNull final HandleContextImpl context,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final HederaState state,
            final long nodeID) {
        if (preHandleResult.status() == NODE_DUE_DILIGENCE_FAILURE) {
            utilizationManager.trackFeePayments(consensusNow, state);
            final var fees = dispatcher.dispatchComputeFees(context);
            return new ValidationResult(preHandleResult.status(), preHandleResult.responseCode(), fees);
        }

        final var txInfo = requireNonNull(preHandleResult.txInfo());
        final var payerID = requireNonNull(txInfo.payerID());
        final var functionality = txInfo.functionality();
        final var txBody = txInfo.txBody();
        boolean isPayerHollow;

        final Account payer;
        try {
            payer = solvencyPreCheck.getPayerAccount(storeFactory, payerID);
        } catch (PreCheckException e) {
            throw new IllegalStateException("Missing payer should be a due diligence failure", e);
        }
        isPayerHollow = isHollow(payer);
        // Check all signature verifications. This will also wait, if validation is still ongoing.
        // If the payer is hollow the key will be null, so we skip the payer signature verification.
        if (!isPayerHollow) {
            final var payerKeyVerification = verifier.verificationFor(preHandleResult.getPayerKey());
            if (payerKeyVerification.failed()) {
                utilizationManager.trackFeePayments(consensusNow, state);
                final var fees = dispatcher.dispatchComputeFees(context);
                return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, INVALID_PAYER_SIGNATURE, fees);
            }
        }

        // verify all the keys
        for (final var key : preHandleResult.getRequiredKeys()) {
            final var verification = verifier.verificationFor(key);
            if (verification.failed()) {
                utilizationManager.trackFeePayments(consensusNow, state);
                final var fees = dispatcher.dispatchComputeFees(context);
                return new ValidationResult(PRE_HANDLE_FAILURE, INVALID_SIGNATURE, fees);
            }
        }
        // If there are any hollow accounts whose signatures need to be verified, verify them
        for (final var hollowAccount : preHandleResult.getHollowAccounts()) {
            final var verification = verifier.verificationFor(hollowAccount.alias());
            if (verification.failed()) {
                utilizationManager.trackFeePayments(consensusNow, state);
                final var fees = dispatcher.dispatchComputeFees(context);
                return new ValidationResult(PRE_HANDLE_FAILURE, INVALID_SIGNATURE, fees);
            }
        }

        // Notice that above, we computed fees assuming network utilization for
        // just a fee payment. Here we instead calculate fees based on tracking the
        // user transaction. This is for mono-service fidelity, but does not have any
        // particular priority and could be revisited later after diff testing
        utilizationManager.trackTxn(txInfo, consensusNow, state);
        final var fees = dispatcher.dispatchComputeFees(context);

        // Check for duplicate transactions. It is perfectly normal for there to be duplicates -- it is valid for
        // a user to intentionally submit duplicates to multiple nodes as a hedge against dishonest nodes, or for
        // other reasons. If we find a duplicate, we *will not* execute the transaction, we will simply charge
        // the payer (whether the payer from the transaction or the node in the event of a due diligence failure)
        // and create an appropriate record to save in state and send to the record stream.
        final var duplicateCheckResult = recordCache.hasDuplicate(txBody.transactionIDOrThrow(), nodeID);
        if (duplicateCheckResult != NO_DUPLICATE) {
            return new ValidationResult(
                    duplicateCheckResult == SAME_NODE ? NODE_DUE_DILIGENCE_FAILURE : PRE_HANDLE_FAILURE,
                    DUPLICATE_TRANSACTION,
                    fees);
        }

        // Check the status and solvency of the payer (assuming their signature is valid)
        try {
            solvencyPreCheck.checkSolvency(txInfo, payer, fees, false);
        } catch (final InsufficientServiceFeeException e) {
            return new ValidationResult(PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE, e.responseCode(), fees);
        } catch (final InsufficientNonFeeDebitsException e) {
            return new ValidationResult(PRE_HANDLE_FAILURE, e.responseCode(), fees);
        } catch (final PreCheckException e) {
            // Includes InsufficientNetworkFeeException
            return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, e.responseCode(), fees);
        }

        // Check the time box of the transaction
        try {
            checker.checkTimeBox(txBody, consensusNow, RequireMinValidLifetimeBuffer.NO);
        } catch (final PreCheckException e) {
            return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, e.responseCode(), fees);
        }

        // Check if the payer has the required permissions
        if (!authorizer.isAuthorized(payerID, functionality)) {
            if (functionality == HederaFunctionality.SYSTEM_DELETE) {
                return new ValidationResult(PRE_HANDLE_FAILURE, NOT_SUPPORTED, fees);
            }
            return new ValidationResult(PRE_HANDLE_FAILURE, UNAUTHORIZED, fees);
        }

        // Check if pre-handle was successful
        if (preHandleResult.status() != SO_FAR_SO_GOOD) {
            return new ValidationResult(preHandleResult.status(), preHandleResult.responseCode(), fees);
        }

        // Check if the transaction is privileged and if the payer has the required privileges
        final var privileges = authorizer.hasPrivilegedAuthorization(payerID, functionality, txBody);
        if (privileges == SystemPrivilege.UNAUTHORIZED) {
            return new ValidationResult(PRE_HANDLE_FAILURE, AUTHORIZATION_FAILED, fees);
        }
        if (privileges == SystemPrivilege.IMPERMISSIBLE) {
            return new ValidationResult(PRE_HANDLE_FAILURE, ENTITY_NOT_ALLOWED_TO_DELETE, fees);
        }

        return new ValidationResult(SO_FAR_SO_GOOD, OK, fees);
    }

    private record ValidationResult(
            @NonNull PreHandleResult.Status status, @NonNull ResponseCodeEnum responseCodeEnum, @NonNull Fees fees) {}

    /**
     * Rolls back the stack and sets the status of the transaction in case of a failure.
     *
     * @param rollbackStack whether to rollback the stack. Will be false when the failure is due to a
     * {@link HandleException} that is due to a contract call revert.
     * @param status the status to set
     * @param stack the save point stack to rollback
     * @param recordListBuilder the record list builder to revert
     */
    private void rollback(
            final boolean rollbackStack,
            @NonNull final ResponseCodeEnum status,
            @NonNull final SavepointStackImpl stack,
            @NonNull final RecordListBuilder recordListBuilder) {
        if (rollbackStack) {
            stack.rollbackFullStack();
        }
        final var userTransactionRecordBuilder = recordListBuilder.userTransactionRecordBuilder();
        userTransactionRecordBuilder.status(status);
        recordListBuilder.revertChildrenOf(userTransactionRecordBuilder);
    }

    /*
     * This method gets all the verification data for the current transaction. If pre-handle was previously ran
     * successfully, we only add the missing keys. If it did not run or an error occurred, we run it again.
     * If there is a due diligence error, this method will return a CryptoTransfer to charge the node along with
     * its verification data.
     */
    @NonNull
    private PreHandleResult getCurrentPreHandleResult(
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn) {
        final var metadata = platformTxn.getMetadata();
        final PreHandleResult previousResult;
        if (metadata instanceof PreHandleResult result) {
            previousResult = result;
        } else {
            // This should be impossible since the Platform contract guarantees that SwirldState.preHandle()
            // is always called before SwirldState.handleTransaction(); and our preHandle() implementation
            // always sets the metadata to a PreHandleResult
            logger.error(
                    "Received transaction without PreHandleResult metadata from node {} (was {})",
                    creator.nodeId(),
                    metadata);
            previousResult = null;
        }
        // We do not know how long transactions are kept in memory. Clearing metadata to avoid keeping it for too long.
        platformTxn.setMetadata(null);
        return preHandleWorkflow.preHandleTransaction(
                creator.accountId(),
                storeFactory,
                storeFactory.getStore(ReadableAccountStore.class),
                platformTxn,
                previousResult);
    }
}
