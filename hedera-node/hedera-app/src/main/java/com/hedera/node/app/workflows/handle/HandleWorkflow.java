/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
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
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulatorImpl;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.ExpandedSignaturePair;
import com.hedera.node.app.signature.SignatureExpander;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.InsufficientNonFeeDebitsException;
import com.hedera.node.app.spi.workflows.InsufficientServiceFeeException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.record.GenesisRecordsConsensusHook;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.handle.verifier.BaseHandleContextVerifier;
import com.hedera.node.app.workflows.handle.verifier.HandleContextVerifier;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The handle workflow that is responsible for handling the next {@link Round} of transactions.
 */
public class HandleWorkflow {

    private static final Logger logger = LogManager.getLogger(HandleWorkflow.class);

    private final NetworkInfo networkInfo;
    private final PreHandleWorkflow preHandleWorkflow;
    private final TransactionDispatcher dispatcher;
    private final BlockRecordManager blockRecordManager;
    private final SignatureExpander signatureExpander;
    private final SignatureVerifier signatureVerifier;
    private final TransactionChecker checker;
    private final ServiceScopeLookup serviceScopeLookup;
    private final ConfigProvider configProvider;
    private final HederaRecordCache recordCache;
    private final GenesisRecordsConsensusHook genesisRecordsTimeHook;
    private final StakingPeriodTimeHook stakingPeriodTimeHook;
    private final FeeManager feeManager;
    private final ExchangeRateManager exchangeRateManager;
    private final ParentRecordFinalizer transactionFinalizer;
    private final SystemFileUpdateFacility systemFileUpdateFacility;
    private final DualStateUpdateFacility dualStateUpdateFacility;
    private final SolvencyPreCheck solvencyPreCheck;
    private final Authorizer authorizer;
    private final NetworkUtilizationManager networkUtilizationManager;

    @Inject
    public HandleWorkflow(
            @NonNull final NetworkInfo networkInfo,
            @NonNull final PreHandleWorkflow preHandleWorkflow,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final SignatureExpander signatureExpander,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull final TransactionChecker checker,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final ConfigProvider configProvider,
            @NonNull final HederaRecordCache recordCache,
            @NonNull final GenesisRecordsConsensusHook genesisRecordsTimeHook,
            @NonNull final StakingPeriodTimeHook stakingPeriodTimeHook,
            @NonNull final FeeManager feeManager,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final ParentRecordFinalizer transactionFinalizer,
            @NonNull final SystemFileUpdateFacility systemFileUpdateFacility,
            @NonNull final DualStateUpdateFacility dualStateUpdateFacility,
            @NonNull final SolvencyPreCheck solvencyPreCheck,
            @NonNull final Authorizer authorizer,
            @NonNull final NetworkUtilizationManager networkUtilizationManager) {
        this.networkInfo = requireNonNull(networkInfo, "networkInfo must not be null");
        this.preHandleWorkflow = requireNonNull(preHandleWorkflow, "preHandleWorkflow must not be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null");
        this.blockRecordManager = requireNonNull(blockRecordManager, "recordManager must not be null");
        this.signatureExpander = requireNonNull(signatureExpander, "signatureExpander must not be null");
        this.signatureVerifier = requireNonNull(signatureVerifier, "signatureVerifier must not be null");
        this.checker = requireNonNull(checker, "checker must not be null");
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup, "serviceScopeLookup must not be null");
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.recordCache = requireNonNull(recordCache, "recordCache must not be null");
        this.genesisRecordsTimeHook = requireNonNull(genesisRecordsTimeHook, "genesisRecordsTimeHook must not be null");
        this.stakingPeriodTimeHook = requireNonNull(stakingPeriodTimeHook, "stakingPeriodTimeHook must not be null");
        this.feeManager = requireNonNull(feeManager, "feeManager must not be null");
        this.exchangeRateManager = requireNonNull(exchangeRateManager, "exchangeRateManager must not be null");
        this.transactionFinalizer = requireNonNull(transactionFinalizer, "transactionFinalizer must not be null");
        this.systemFileUpdateFacility =
                requireNonNull(systemFileUpdateFacility, "systemFileUpdateFacility must not be null");
        this.dualStateUpdateFacility =
                requireNonNull(dualStateUpdateFacility, "dualStateUpdateFacility must not be null");
        this.solvencyPreCheck = requireNonNull(solvencyPreCheck, "solvencyPreCheck must not be null");
        this.authorizer = requireNonNull(authorizer, "authorizer must not be null");
        this.networkUtilizationManager =
                requireNonNull(networkUtilizationManager, "networkUtilizationManager must not be null");
    }

    /**
     * Handles the next {@link Round}
     *
     * @param state the writable {@link HederaState} that this round will work on
     * @param round the next {@link Round} that needs to be processed
     */
    public void handleRound(
            @NonNull final HederaState state, @NonNull final SwirldDualState dualState, @NonNull final Round round) {
        // Keep track of whether any user transactions were handled. If so, then we will need to close the round
        // with the block record manager.
        final var userTransactionsHandled = new AtomicBoolean(false);

        // log start of round to transaction state log
        logStartRound(round);

        // handle each event in the round
        for (final ConsensusEvent event : round) {
            final var creator = networkInfo.nodeInfo(event.getCreatorId().id());
            if (creator == null) {
                // We were given an event for a node that *does not exist in the address book*. This will be logged as
                // a warning, as this should never happen, and we will skip the event. The platform should guarantee
                // that we never receive an event that isn't associated with the address book, and every node in the
                // address book must have an account ID, since you cannot delete an account belonging to a node and
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
                        handlePlatformTransaction(state, dualState, event, creator, platformTxn);
                    }
                } catch (final Exception e) {
                    logger.fatal(
                            "A fatal unhandled exception occurred during transaction handling. "
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
            @NonNull final SwirldDualState dualState,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn) {
        // Get the consensus timestamp. FUTURE We want this to exactly match the consensus timestamp from the hashgraph,
        // but for compatibility with the current implementation, we adjust it as follows.
        final Instant consensusNow = platformTxn.getConsensusTimestamp().minusNanos(1000 + 3L);

        // handle user transaction
        handleUserTransaction(consensusNow, state, dualState, platformEvent, creator, platformTxn);
    }

    private void handleUserTransaction(
            @NonNull final Instant consensusNow,
            @NonNull final HederaState state,
            @NonNull final SwirldDualState dualState,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn) {
        // Setup record builder list
        blockRecordManager.startUserTransaction(consensusNow, state);
        final var recordListBuilder = new RecordListBuilder(consensusNow);
        final var recordBuilder = recordListBuilder.userTransactionRecordBuilder();

        // Setup helpers
        final var configuration = configProvider.getConfiguration();
        final var stack = new SavepointStackImpl(state);
        final var readableStoreFactory = new ReadableStoreFactory(stack);
        final var feeAccumulator = createFeeAccumulator(stack, configuration, recordBuilder);

        final var tokenServiceContext = new TokenContextImpl(configuration, stack, recordListBuilder);
        // It's awful that we have to check this every time a transaction is handled, especially since this mostly
        // applies to non-production cases. Let's find a way to 💥💥 remove this 💥💥
        genesisRecordsTimeHook.process(tokenServiceContext);
        try {
            // If this is the first user transaction after midnight, then handle staking updates prior to handling the
            // transaction itself.
            stakingPeriodTimeHook.process(tokenServiceContext);
        } catch (Exception e) {
            // If anything goes wrong, we log the error and continue
            logger.error("Failed to process staking period time hook", e);
        }
        // @future('7836'): update the exchange rate and call from here

        TransactionBody txBody = null;
        AccountID payer = null;
        Fees fees = null;
        try {
            final var preHandleResult =
                    getCurrentPreHandleResult(readableStoreFactory, creator, platformTxn, configuration);

            final var transactionInfo = preHandleResult.txInfo();

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
            logStartUserTransactionPreHandleResultP2(
                    preHandleResult.payer(),
                    preHandleResult.payerKey(),
                    preHandleResult.status(),
                    preHandleResult.responseCode());
            logStartUserTransactionPreHandleResultP3(
                    preHandleResult.txInfo(), preHandleResult.requiredKeys(), preHandleResult.verificationResults());

            // Initialize record builder list
            recordBuilder
                    .transaction(transactionInfo.transaction())
                    .transactionBytes(transactionBytes)
                    .transactionID(transactionInfo.transactionID())
                    .exchangeRate(exchangeRateManager.exchangeRates())
                    .memo(txBody.memo());

            // Set up the verifier
            final var hederaConfig = configuration.getConfigData(HederaConfig.class);
            final var verifier = new BaseHandleContextVerifier(hederaConfig, preHandleResult.verificationResults());
            final var signatureMapSize = SignatureMap.PROTOBUF.measureRecord(transactionInfo.signatureMap());

            // Setup context
            final var context = new HandleContextImpl(
                    txBody,
                    transactionInfo.functionality(),
                    signatureMapSize,
                    payer,
                    preHandleResult.payerKey(),
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
                    authorizer);

            // Calculate the fee
            fees = dispatcher.dispatchComputeFees(context);

            // Run all pre-checks
            final var validationResult = validate(
                    consensusNow,
                    verifier,
                    preHandleResult,
                    readableStoreFactory,
                    fees,
                    platformEvent.getCreatorId().id());

            networkUtilizationManager.resetFrom(stack);

            if (validationResult.status() != SO_FAR_SO_GOOD) {
                final var sigVerificationFailed = validationResult.responseCodeEnum() == INVALID_SIGNATURE;
                if (sigVerificationFailed) {
                    // If the signature status isn't ok, only work done will be fee charging
                    // Note this is how it's implemented in mono (TopLevelTransition.java#L93), in future we may want to
                    // not trackFeePayments() only for INVALID_SIGNATURE but for any preCheckResult.status() !=
                    // SO_FAR_SO_GOOD
                    networkUtilizationManager.trackFeePayments(payer, consensusNow, stack);
                }
                recordBuilder.status(validationResult.responseCodeEnum());
                try {
                    if (validationResult.status() == NODE_DUE_DILIGENCE_FAILURE) {
                        feeAccumulator.chargeNetworkFee(creator.accountId(), fees.networkFee());
                    } else if (validationResult.status() == PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE) {
                        // We do not charge partial service fees; if the payer is unwilling or unable to cover
                        // the entire service fee, then we only charge network and node fees (prioritizing
                        // the network fee in case of a very low payer balance)
                        feeAccumulator.chargeFees(payer, creator.accountId(), fees.withoutServiceComponent());
                    } else {
                        feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                    }
                } catch (HandleException ex) {
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
                networkUtilizationManager.trackTxn(transactionInfo, consensusNow, stack);
                if (!authorizer.hasWaivedFees(payer, transactionInfo.functionality(), txBody)) {
                    // privileged transactions are not charged fees
                    feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                }
                try {
                    if (networkUtilizationManager.wasLastTxnGasThrottled()) {
                        // Don't charge the payer the service fee component, because the user-submitted transaction
                        // was fully valid but network capacity was unavailable to satisfy it
                        fees = fees.withoutServiceComponent();
                        throw new HandleException(CONSENSUS_GAS_EXHAUSTED);
                    }

                    // Dispatch the transaction to the handler
                    dispatcher.dispatchHandle(context);
                    recordBuilder.status(SUCCESS);

                    // After transaction is successfully handled update the gas throttle by leaking the unused gas
                    if (isGasThrottled(transactionInfo.functionality()) && recordBuilder.hasContractResult()) {
                        final var contractsConfig = configuration.getConfigData(ContractsConfig.class);
                        if (contractsConfig.throttleThrottleByGas()) {
                            final var gasUsed = recordBuilder.getGasUsedForContractTxn();
                            final var gasLimitForContractTx =
                                    getGasLimitForContractTx(txBody, transactionInfo.functionality());
                            final var excessAmount = gasLimitForContractTx - gasUsed;
                            networkUtilizationManager.leakUnusedGasPreviouslyReserved(transactionInfo, excessAmount);
                        }
                    }

                    // Notify responsible facility if system-file was uploaded
                    systemFileUpdateFacility.handleTxBody(stack, txBody);

                    // Notify if dual state was updated
                    dualStateUpdateFacility.handleTxBody(stack, dualState, txBody);

                } catch (final HandleException e) {
                    rollback(e.getStatus(), stack, recordListBuilder);
                    feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                }
            }
        } catch (final Exception e) {
            logger.error("An unexpected exception was thrown during handle", e);
            rollback(ResponseCodeEnum.FAIL_INVALID, stack, recordListBuilder);
            if (payer != null && fees != null) {
                try {
                    feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                } catch (HandleException chargeException) {
                    logger.error(
                            "Unable to charge account {} a penalty after an unexpected exception {}. Cause of the failed charge:",
                            payer,
                            e,
                            chargeException);
                }
            }
        }

        networkUtilizationManager.saveTo(stack);
        transactionFinalizer.finalizeParentRecord(payer, tokenServiceContext);

        // Commit all state changes
        stack.commitFullStack();

        // store all records at once, build() records end of transaction to log
        final var recordListResult = recordListBuilder.build();
        recordCache.add(creator.nodeId(), payer, recordListResult.records());

        blockRecordManager.endUserTransaction(recordListResult.records().stream(), state);
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
            @NonNull final HandleContextVerifier verifier,
            @NonNull final PreHandleResult preHandleResult,
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final Fees fees,
            final long nodeID) {

        final var txInfo = preHandleResult.txInfo();
        final var payerID = txInfo.payerID();
        final var functionality = txInfo.functionality();
        final var txBody = txInfo.txBody();

        // Check if pre-handle was successful
        if (preHandleResult.status() != SO_FAR_SO_GOOD) {
            return new ValidationResult(preHandleResult.status(), preHandleResult.responseCode());
        }

        // Check for duplicate transactions. It is perfectly normal for there to be duplicates -- it is valid for
        // a user to intentionally submit duplicates to multiple nodes as a hedge against dishonest nodes, or for
        // other reasons. If we find a duplicate, we *will not* execute the transaction, we will simply charge
        // the payer (whether the payer from the transaction or the node in the event of a due diligence failure)
        // and create an appropriate record to save in state and send to the record stream.
        final var duplicateCheckResult = recordCache.hasDuplicate(txBody.transactionID(), nodeID);
        if (duplicateCheckResult != NO_DUPLICATE) {
            return new ValidationResult(
                    duplicateCheckResult == SAME_NODE ? NODE_DUE_DILIGENCE_FAILURE : PRE_HANDLE_FAILURE,
                    DUPLICATE_TRANSACTION);
        }

        // Check the status and solvency of the payer
        try {
            final var payer = solvencyPreCheck.getPayerAccount(storeFactory, payerID);
            solvencyPreCheck.checkSolvency(txInfo, payer, fees);
        } catch (final InsufficientServiceFeeException e) {
            return new ValidationResult(PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE, e.responseCode());
        } catch (final InsufficientNonFeeDebitsException e) {
            return new ValidationResult(PRE_HANDLE_FAILURE, e.responseCode());
        } catch (PreCheckException e) {
            // Includes InsufficientNetworkFeeException
            return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, e.responseCode());
        }

        // Check the time box of the transaction
        try {
            checker.checkTimeBox(txBody, consensusNow);
        } catch (final PreCheckException e) {
            return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, e.responseCode());
        }

        // Check if the payer has the required permissions
        if (!authorizer.isAuthorized(payerID, functionality)) {
            if (functionality == HederaFunctionality.SYSTEM_DELETE) {
                return new ValidationResult(PRE_HANDLE_FAILURE, ResponseCodeEnum.NOT_SUPPORTED);
            }
            return new ValidationResult(PRE_HANDLE_FAILURE, ResponseCodeEnum.UNAUTHORIZED);
        }

        // Check if the transaction is privileged and if the payer has the required privileges
        final var privileges = authorizer.hasPrivilegedAuthorization(payerID, functionality, txBody);
        if (privileges == SystemPrivilege.UNAUTHORIZED) {
            return new ValidationResult(PRE_HANDLE_FAILURE, ResponseCodeEnum.AUTHORIZATION_FAILED);
        }
        if (privileges == SystemPrivilege.IMPERMISSIBLE) {
            return new ValidationResult(PRE_HANDLE_FAILURE, ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE);
        }

        // Check all signature verifications. This will also wait, if validation is still ongoing.
        final var payerKeyVerification = verifier.verificationFor(preHandleResult.payerKey());
        if (payerKeyVerification.failed()) {
            return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, INVALID_SIGNATURE);
        }

        // verify all the keys
        for (final var key : preHandleResult.requiredKeys()) {
            final var verification = verifier.verificationFor(key);
            if (verification.failed()) {
                return new ValidationResult(PRE_HANDLE_FAILURE, INVALID_SIGNATURE);
            }
        }
        // If there are any hollow accounts whose signatures need to be verified, verify them
        for (final var hollowAccount : preHandleResult.hollowAccounts()) {
            final var verification = verifier.verificationFor(hollowAccount.alias());
            if (verification.failed()) {
                return new ValidationResult(PRE_HANDLE_FAILURE, INVALID_SIGNATURE);
            }
        }

        return new ValidationResult(SO_FAR_SO_GOOD, OK);
    }

    private record ValidationResult(
            @NonNull PreHandleResult.Status status, @NonNull ResponseCodeEnum responseCodeEnum) {}

    private void rollback(
            @NonNull final ResponseCodeEnum status,
            @NonNull final SavepointStackImpl stack,
            @NonNull final RecordListBuilder recordListBuilder) {
        stack.rollbackFullStack();
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
            @NonNull final ConsensusTransaction platformTxn,
            @NonNull final VersionedConfiguration configuration)
            throws PreCheckException {
        final var metadata = platformTxn.getMetadata();
        // We do not know how long transactions are kept in memory. Clearing metadata to avoid keeping it for too long.
        platformTxn.setMetadata(null);

        // First check if pre-handle was run before (in which case metadata is a PreHandleResult)
        if (preHandleStillValid(configuration, metadata)) {
            final var preHandleResult = (PreHandleResult) metadata;

            // In case of due diligence error, we prepare a CryptoTransfer to charge the node and return immediately.
            if (preHandleResult.status() == NODE_DUE_DILIGENCE_FAILURE) {
                return preHandleResult;
            }

            // If pre-handle was successful, we need to add signatures that were not known at the time of pre-handle.
            if (preHandleResult.status() == SO_FAR_SO_GOOD) {
                return addMissingSignatures(storeFactory, preHandleResult, configuration);
            }
        }

        // If we reach this point, either pre-handle was not run or it failed but may succeed now.
        // Therefore, we simply rerun pre-handle.
        final var accountStore = storeFactory.getStore(ReadableAccountStore.class);
        return preHandleWorkflow.preHandleTransaction(creator.accountId(), storeFactory, accountStore, platformTxn);
    }

    private boolean preHandleStillValid(
            @NonNull final VersionedConfiguration configuration, @Nullable final Object metadata) {
        if (metadata instanceof PreHandleResult preHandleResult) {
            return preHandleResult.configVersion() == configuration.getVersion();
        }
        return false;
    }

    /*
     * This method is called when a previous run of pre-handle was successful. We gather the keys again and check if
     * any keys need to be added. If so, we trigger the signature verification for the new keys and collect all
     * results.
     */
    @NonNull
    private PreHandleResult addMissingSignatures(
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final PreHandleResult previousResult,
            @NonNull final Configuration configuration)
            throws PreCheckException {
        final var txInfo = previousResult.txInfo();
        final var txBody = txInfo.txBody();
        final var sigPairs = txInfo.signatureMap().sigPairOrElse(emptyList());
        final var signedBytes = txInfo.signedBytes();

        // extract keys and hollow accounts again
        final var context = new PreHandleContextImpl(storeFactory, txBody, configuration, dispatcher);
        dispatcher.dispatchPreHandle(context);

        // re-expand keys only if any of the keys have changed
        final var previousResults = previousResult.verificationResults();
        final var currentRequiredPayerKeys = context.requiredNonPayerKeys();
        final var currentOptionalPayerKeys = context.optionalNonPayerKeys();
        final var anyKeyChanged = haveKeyChanges(previousResults, context);
        // If none of the keys changed then non need to re-expand all signatures.
        if (!anyKeyChanged) {
            return previousResult;
        }

        // prepare signature verification
        final var verifications = new HashMap<Key, SignatureVerificationFuture>();
        final var payerKey = previousResult.payerKey();
        verifications.put(payerKey, previousResult.verificationResults().get(payerKey));

        // expand all keys
        final var expanded = new HashSet<ExpandedSignaturePair>();
        signatureExpander.expand(sigPairs, expanded);
        signatureExpander.expand(currentRequiredPayerKeys, sigPairs, expanded);
        signatureExpander.expand(currentOptionalPayerKeys, sigPairs, expanded);

        // remove all keys that were already verified
        for (final var it = expanded.iterator(); it.hasNext(); ) {
            final var entry = it.next();
            final var oldVerification = previousResult.verificationResults().get(entry.key());
            if (oldVerification != null) {
                verifications.put(oldVerification.key(), oldVerification);
                it.remove();
            }
        }

        // start verification for remaining keys
        if (!expanded.isEmpty()) {
            verifications.putAll(signatureVerifier.verify(signedBytes, expanded));
        }

        return new PreHandleResult(
                previousResult.payer(),
                payerKey,
                previousResult.status(),
                previousResult.responseCode(),
                previousResult.txInfo(),
                context.requiredNonPayerKeys(),
                context.requiredHollowAccounts(),
                verifications,
                previousResult.innerResult(),
                previousResult.configVersion());
    }

    /**
     * Checks if any of the keys changed from previous result to current result.
     * Only if keys changed we need to re-expand and re-verify the signatures.
     *
     * @param previousResults previous result from signature verification
     * @param context current context
     * @return true if any of the keys changed
     */
    private boolean haveKeyChanges(
            final Map<Key, SignatureVerificationFuture> previousResults, final PreHandleContextImpl context) {
        final var currentRequiredNonPayerKeys = context.requiredNonPayerKeys();
        final var currentOptionalNonPayerKeys = context.optionalNonPayerKeys();
        final var currentPayerKey = context.payerKey();

        for (final var key : currentRequiredNonPayerKeys) {
            if (!previousResults.containsKey(key)) {
                return true;
            }
        }
        for (final var key : currentOptionalNonPayerKeys) {
            if (!previousResults.containsKey(key)) {
                return true;
            }
        }
        return !previousResults.containsKey(currentPayerKey);
    }
}
