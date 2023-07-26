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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.ExpandedSignaturePair;
import com.hedera.node.app.signature.SignatureExpander;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.solvency.SolvencyPreCheck;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.handle.verifier.BaseHandleContextVerifier;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleResult.Status;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private final InstantSource instantSource;
    private final HederaRecordCache hederaRecordCache;
    private final SolvencyPreCheck solvencyPreCheck;

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
            @NonNull final InstantSource instantSource,
            @NonNull final HederaRecordCache hederaRecordCache,
            @NonNull final SolvencyPreCheck solvencyPreCheck) {
        this.networkInfo = requireNonNull(networkInfo, "networkInfo must not be null");
        this.preHandleWorkflow = requireNonNull(preHandleWorkflow, "preHandleWorkflow must not be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null");
        this.blockRecordManager = requireNonNull(blockRecordManager, "recordManager must not be null");
        this.signatureExpander = requireNonNull(signatureExpander, "signatureExpander must not be null");
        this.signatureVerifier = requireNonNull(signatureVerifier, "signatureVerifier must not be null");
        this.checker = requireNonNull(checker, "checker must not be null");
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup, "serviceScopeLookup must not be null");
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.instantSource = requireNonNull(instantSource, "instantSource must not be null");
        this.hederaRecordCache = requireNonNull(hederaRecordCache, "hederaRecordCache must not be null");
        this.solvencyPreCheck = requireNonNull(solvencyPreCheck, "solvencyPreCheck must not be null");
    }

    /**
     * Handles the next {@link Round}
     *
     * @param state the writable {@link HederaState} that this round will work on
     * @param round the next {@link Round} that needs to be processed
     */
    public void handleRound(@NonNull final HederaState state, @NonNull final Round round) {
        // handle each transaction in the round
        round.forEachEventTransaction((event, txn) -> {
            try {
                handlePlatformTransaction(state, event, txn);
            } catch (final Throwable e) {
                logger.fatal(
                        "A fatal unhandled exception occurred during transaction handling. "
                                + "While this node may not die right away, it is in a bad way, most likely fatally.",
                        e);
            }
        });
        // inform BlockRecordManager that the round is complete, so it can update running-hashes in state
        // that have been being computed in background threads. The running hash has to be included in
        // state, but we want to synchronize with background threads as infrequently as possible. So once per
        // round is the minimum we can do.
        blockRecordManager.endRound(state);
    }

    private void handlePlatformTransaction(
            @NonNull final HederaState state,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final ConsensusTransaction platformTxn) {
        // skip system transactions
        if (platformTxn.isSystem()) {
            return;
        }

        // Get the consensus timestamp
        final var consensusNow = platformTxn.getConsensusTimestamp();

        // Setup record builder list
        blockRecordManager.startUserTransaction(consensusNow, state);
        final var recordBuilder = new SingleTransactionRecordBuilderImpl(consensusNow);
        final var recordListBuilder = new RecordListBuilder(recordBuilder);

        PreHandleResult preHandleResult = null;
        try {
            // Setup configuration
            final var configuration = configProvider.getConfiguration();
            final var hederaConfig = configuration.getConfigData(HederaConfig.class);

            preHandleResult = getCurrentPreHandleResult(state, platformEvent, platformTxn, configuration);
            final var transactionInfo = preHandleResult.txInfo();
            final var txBody = transactionInfo.txBody();
            recordBuilder
                    .transaction(transactionInfo.transaction())
                    .transactionBytes(transactionInfo.signedBytes())
                    .transactionID(txBody.transactionID())
                    .memo(txBody.memo());

            // If pre-handle was successful, we return the result. Otherwise, we charge the node or throw an exception.
            switch (preHandleResult.status()) {
                case SO_FAR_SO_GOOD -> {
                    /* All good nothing to do */
                }
                case NODE_DUE_DILIGENCE_FAILURE -> createPenaltyPayment();
                case UNKNOWN_FAILURE -> throw new IllegalStateException("Pre-handle failed with unknown failure");
                default -> throw new PreCheckException(preHandleResult.responseCode());
            }

            // If pre-handle was successful, we return the result. Otherwise, we charge the node or throw an exception.
            switch (preHandleResult.status()) {
                case SO_FAR_SO_GOOD -> {
                    /* All good nothing to do */
                }
                case NODE_DUE_DILIGENCE_FAILURE -> createPenaltyPayment();
                case UNKNOWN_FAILURE -> throw new IllegalStateException("Pre-handle failed with unknown failure");
                default -> throw new PreCheckException(preHandleResult.responseCode());
            }

            // Check the payer signature. Whether this is a duplicate transaction or not, we need to have the payer
            // information to proceed. Also perform a solvency check on the account to make sure the account has not
            // been deleted, it does exist, and it has sufficient funds to pay for the transaction. If any of those
            // things is not true, then we will charge the node instead.
            final var timeout = hederaConfig.workflowVerificationTimeoutMS();
            final var maxMillis = instantSource.millis() + timeout;
            final var payerKeyVerification =
                    preHandleResult.verificationResults().get(preHandleResult.payerKey());
            if (payerKeyVerification.get(timeout, TimeUnit.MILLISECONDS).failed()) {
                throw new HandleException(ResponseCodeEnum.INVALID_SIGNATURE);
            }
            boolean chargeNode = false;
            try {
                solvencyPreCheck.checkPayerAccountStatus(state, preHandleResult.payer());
                solvencyPreCheck.checkSolvencyOfVerifiedPayer(
                        state, preHandleResult.txInfo().transaction());
            } catch (PreCheckException e) {
                // If the account is not valid, or it doesn't have sufficient funds - we charge the node
                chargeNode = true;
            }

            final var stack = new SavepointStackImpl(state, configuration);

            // Check for duplicate transactions. It is perfectly normal for there to be duplicates -- it is valid for
            // a user to intentionally submit duplicates to multiple nodes as a hedge against dishonest nodes, or for
            // other reasons. If we find a duplicate, we *will not* execute the transaction, we will simply charge
            // the payer (whether the payer from the transaction or the node in the event of a due diligence failure)
            // and create an appropriate record to save in state and send to the record stream.
            final var foundTransactionRecord = hederaRecordCache.getRecord(
                    preHandleResult.txInfo().txBody().transactionID());

            if (foundTransactionRecord != null) {
                // TODO: 1. Compute the node+network fees(not implement yet)

                // 2. Charge the payer or the node - depends on https://github.com/hashgraph/hedera-services/issues/6811
                // TODO: we need to decide how we are going to charge the user - should we create a crypto transfer or
                // should we directly from the state(?) Probably we will do it as do it when we have
                // NODE_DUE_DILIGENCE_FAILURE
                // and call the createPenaltyPayment(). If we choose to generate PreHandleResult and should let the flow
                // continue(remove the else statement) so we can execute it. We should use chargeNode boolean to decide
                // should we charge the node or the account.

                // 3. TODO: Create a transaction record
                // When createPenaltyPayment is implemented it will produce PreHandleResult. Using this result we can
                // set it to the record as we do above:
                /*
                recordBuilder.transaction(
                    preHandleResult.txInfo().transaction(),
                    preHandleResult.txInfo().signedBytes());
                 */
            } else {
                for (final var key : preHandleResult.requiredKeys()) {
                    final var remainingMillis = maxMillis - instantSource.millis();
                    if (remainingMillis <= 0) {
                        throw new TimeoutException("Verification of signatures timed out");
                    }
                    final var verification =
                            preHandleResult.verificationResults().get(key);
                    if (verification.get(remainingMillis, TimeUnit.MILLISECONDS).failed()) {
                        throw new HandleException(ResponseCodeEnum.INVALID_SIGNATURE);
                    }
                }

                // Setup context
                final var verifier = new BaseHandleContextVerifier(hederaConfig, preHandleResult.verificationResults());
                final var context = new HandleContextImpl(
                        txBody,
                        preHandleResult.payer(),
                        preHandleResult.payerKey(),
                        TransactionCategory.USER,
                        recordBuilder,
                        stack,
                        verifier,
                        recordListBuilder,
                        checker,
                        dispatcher,
                        serviceScopeLookup,
                        blockRecordManager,
                        hederaRecordCache);

                // Dispatch the transaction to the handler
                dispatcher.dispatchHandle(context);
            }

            // TODO: Finalize transaction with the help of the token service

            recordBuilder.status(SUCCESS);

            // commit state
            stack.commit();
        } catch (final PreCheckException e) {
            recordFailedTransaction(e.responseCode(), recordBuilder, recordListBuilder);
        } catch (final HandleException e) {
            recordFailedTransaction(e.getStatus(), recordBuilder, recordListBuilder);
        } catch (final InterruptedException e) {
            logger.error("Interrupted while waiting for signature verification", e);
            Thread.currentThread().interrupt();
            recordBuilder.status(ResponseCodeEnum.UNKNOWN);
        } catch (final TimeoutException e) {
            logger.warn("Timed out while waiting for signature verification, probably going to ISS soon", e);
            recordBuilder.status(ResponseCodeEnum.UNKNOWN);
        } catch (final Throwable e) {
            logger.error("An unexpected exception was thrown during handle", e);
            recordBuilder.status(ResponseCodeEnum.UNKNOWN);
        }

        // TODO: handle long scheduled transactions

        // TODO: handle system tasks. System tasks should be outside the blockRecordManager start/end user transaction
        // TODO: and have their own start/end. So system transactions are handled like separate user transactions.

        // store all records at once
        final var recordListResult = recordListBuilder.build();

        // add the transaction to the cache
        if (preHandleResult != null) {
            hederaRecordCache.add(
                    platformEvent.getCreatorId().id(),
                    preHandleResult.payer(),
                    recordListResult.mainRecord().record(),
                    consensusNow);
        } else {
            // The only reason the preHandleResult might be null is if there is an exception in the try-catch
            // block above. If that happens the recordBuilder's status will be updated by the catch blocks so there
            // is no need to override it here. Logging should be enough. // TODO: is that true?
            logger.warn("The transaction was not saved in the cache");
        }

        blockRecordManager.endUserTransaction(recordListResult.recordStream(), state);
    }

    public void recordFailedTransaction(
            @NonNull final ResponseCodeEnum status,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final RecordListBuilder recordListBuilder) {
        recordBuilder.status(status);
        recordListBuilder.revertChildRecordBuilders(recordBuilder);
        // TODO: Finalize failed transaction with the help of token-service and commit required state changes
    }

    /*
     * This method gets all the verification data for the current transaction. If pre-handle was previously ran
     * successfully, we only add the missing keys. If it did not run or an error occurred, we run it again.
     * If there is a due diligence error, this method will return a CryptoTransfer to charge the node along with
     * its verification data.
     */
    @NonNull
    private PreHandleResult getCurrentPreHandleResult(
            @NonNull final HederaState state,
            @NonNull final ConsensusEvent platformEvent,
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
            if (preHandleResult.status() == Status.NODE_DUE_DILIGENCE_FAILURE) {
                return createPenaltyPayment();
            }

            // If pre-handle was successful, we need to add signatures that were not known at the time of pre-handle.
            if (preHandleResult.status() == Status.SO_FAR_SO_GOOD) {
                return addMissingSignatures(state, preHandleResult, configuration);
            }
        }

        // If we reach this point, either pre-handle was not run or it failed but may succeed now.
        // Therefore, we simply rerun pre-handle.
        final var storeFactory = new ReadableStoreFactory(state);
        final var accountStore = storeFactory.getStore(ReadableAccountStore.class);
        final var creator = networkInfo.nodeInfo(platformEvent.getCreatorId().id());
        final var creatorId = creator == null ? null : creator.accountId();
        return preHandleWorkflow.preHandleTransaction(creatorId, storeFactory, accountStore, platformTxn);
    }

    @NonNull
    private PreHandleResult createPenaltyPayment() {
        // TODO: Implement createPenaltyPayment() - https://github.com/hashgraph/hedera-services/issues/6811
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private boolean preHandleStillValid(
            @NonNull final VersionedConfiguration configuration, @Nullable final Object metadata) {
        if (metadata instanceof final PreHandleResult preHandleResult) {
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
            @NonNull final HederaState state,
            @NonNull final PreHandleResult previousResult,
            @NonNull final Configuration configuration)
            throws PreCheckException {
        final var txInfo = previousResult.txInfo();
        final var txBody = txInfo.txBody();
        final var sigPairs = txInfo.signatureMap().sigPairOrElse(emptyList());
        final var signedBytes = txInfo.signedBytes();

        // extract keys and hollow accounts again
        final var storeFactory = new ReadableStoreFactory(state);
        final var context = new PreHandleContextImpl(storeFactory, txBody, configuration, dispatcher);
        dispatcher.dispatchPreHandle(context);

        // prepare signature verification
        final var verifications = new HashMap<Key, SignatureVerificationFuture>();
        final var payerKey = previousResult.payerKey();
        verifications.put(payerKey, previousResult.verificationResults().get(payerKey));

        // expand all keys
        final var expanded = new HashSet<ExpandedSignaturePair>();
        signatureExpander.expand(context.requiredNonPayerKeys(), sigPairs, expanded);
        signatureExpander.expand(context.optionalNonPayerKeys(), sigPairs, expanded);

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
                verifications,
                previousResult.innerResult(),
                previousResult.configVersion());
    }
}
