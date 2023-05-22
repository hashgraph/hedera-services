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

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.RecordListBuilder;
import com.hedera.node.app.records.RecordManager;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.signature.ExpandedSignaturePair;
import com.hedera.node.app.signature.SignatureExpander;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleResult.Status;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HandleWorkflow {

    private static final Logger LOG = LogManager.getLogger(HandleWorkflow.class);

    private final NodeInfo nodeInfo;
    private final PreHandleWorkflow preHandleWorkflow;
    private final TransactionDispatcher dispatcher;
    private final HandleContextService contextService;
    private final RecordManager recordManager;
    private final SignatureExpander signatureExpander;
    private final SignatureVerifier signatureVerifier;
    private final ConfigProvider configProvider;

    @Inject
    public HandleWorkflow(
            @NonNull final NodeInfo nodeInfo,
            @NonNull final PreHandleWorkflow preHandleWorkflow,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final HandleContextService contextService,
            @NonNull final RecordManager recordManager,
            @NonNull final SignatureExpander signatureExpander,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull final ConfigProvider configProvider) {
        this.nodeInfo = requireNonNull(nodeInfo, "nodeInfo must not be null");
        this.preHandleWorkflow = requireNonNull(preHandleWorkflow, "preHandleWorkflow must not be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null");
        this.contextService = requireNonNull(contextService, "contextService must not be null");
        this.recordManager = requireNonNull(recordManager, "recordManager must not be null");
        this.signatureExpander = requireNonNull(signatureExpander, "signatureExpander must not be null");
        this.signatureVerifier = requireNonNull(signatureVerifier, "signatureVerifier must not be null");
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
    }

    /**
     * Handles the next {@link Round}
     *
     * @param state the writable {@link HederaState} that this round will work on
     * @param round the next {@link Round} that needs to be processed
     */
    public void handleRound(@NonNull final HederaState state, @NonNull final Round round) {
        // handle each transaction in the round
        round.forEachEventTransaction((event, txn) -> handlePlatformTransaction(state, event, txn));
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
        final Instant consensusNow = platformTxn.getConsensusTimestamp();

        // Setup record builder list
        recordManager.startUserTransaction(consensusNow);
        final var recordBuilder = new SingleTransactionRecordBuilder(consensusNow);
        final var recordListBuilder = new RecordListBuilder(consensusNow, recordBuilder);

        try {
            final var verifications = getUpdatedVerifications(state, platformEvent, platformTxn);

            // Read all signature verifications. This will also wait, if validation is still ongoing.
            final var keyVerifications = new HashMap<Key, SignatureVerification>();
            for (final var entry : verifications.keyVerifications().entrySet()) {
                // TODO: Implement timeout
                final var verification = entry.getValue().get();
                if (verification.failed()) {
                    throw new HandleException(ResponseCodeEnum.INVALID_SIGNATURE);
                }
                keyVerifications.put(entry.getKey(), verification);
            }

            // Setup context
            final var context = new HandleContextImpl(
                    state,
                    verifications.txBody(),
                    TransactionCategory.USER,
                    recordBuilder,
                    keyVerifications,
                    recordListBuilder,
                    contextService);

            // Dispatch the transaction to the handler
            dispatcher.dispatchHandle(context);

            // TODO: Finalize transaction

            // commit state
            context.commitStateChanges();
        } catch (HandleException e) {
            recordBuilder.status(e.getStatus());
            recordListBuilder.revertChildRecordBuilders(recordBuilder);
            // TODO: Finalize failed transaction and commit changes
        } catch (InterruptedException e) {
            LOG.error("Interrupted while waiting for signature verification", e);
            Thread.currentThread().interrupt();
            recordBuilder.status(ResponseCodeEnum.UNKNOWN);
        } catch (Throwable e) {
            LOG.error("An unexpected exception was thrown during handle", e);
            recordBuilder.status(ResponseCodeEnum.UNKNOWN);
        }

        // TODO update receipt

        // TODO: handle long scheduled transactions

        // TODO: handle system tasks

        // store all records at once
        recordManager.endUserTransaction(recordListBuilder.build());
    }

    private VerificationResult getUpdatedVerifications(
            @NonNull final HederaState state,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final ConsensusTransaction platformTxn)
            throws PreCheckException {
        final var metadata = platformTxn.getMetadata();
        // We do not know how long transactions are kept in memory. Clearing metadata to avoid keeping it for too long.
        platformTxn.setMetadata(null);

        if (preHandleStillValid(metadata)) {
            final var previousResult = (PreHandleResult) metadata;
            if (previousResult.status() == Status.NODE_DUE_DILIGENCE_FAILURE) {
                return createPenaltyPayment();
            }

            if (previousResult.status() == Status.SO_FAR_SO_GOOD) {
                return addMissingSignatures(state, previousResult);
            }
        }

        final var storeFactory = new ReadableStoreFactory(state);
        final var accountStore = storeFactory.getStore(ReadableAccountStore.class);
        final var creator = nodeInfo.accountOf(platformEvent.getCreatorId());
        final var result = preHandleWorkflow.preHandleTransaction(creator, storeFactory, accountStore, platformTxn);

        return switch (result.status()) {
            case SO_FAR_SO_GOOD -> new VerificationResult(result);
            case NODE_DUE_DILIGENCE_FAILURE -> createPenaltyPayment();
            default -> throw new PreCheckException(result.responseCode());
        };
    }

    private VerificationResult createPenaltyPayment() {
        // TODO: Implement HandleWorkflow.createPnealtyPayment()
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private boolean preHandleStillValid(@NonNull final Object metadata) {
        // TODO: Check config and other preconditions
        return metadata instanceof PreHandleResult;
    }

    private VerificationResult addMissingSignatures(
            @NonNull final HederaState state, @NonNull final PreHandleResult previousResult) throws PreCheckException {
        final var txBody = previousResult.txInfo().txBody();

        // extract keys and hollow accounts again
        final var storeFactory = new ReadableStoreFactory(state);
        final var context = new PreHandleContextImpl(storeFactory, txBody);
        dispatcher.dispatchPreHandle(context);

        // sort keys
        final var newVerifications = new HashMap<Key, SignatureVerificationFuture>();
        final var previousVerifications = previousResult.verificationResults();
        final var originals = previousResult.txInfo().signatureMap().sigPairOrElse(emptyList());
        final var expanded = new HashSet<ExpandedSignaturePair>();
        final var nonPayerKeys = context.requiredNonPayerKeys();
        for (final var key : nonPayerKeys) {
            final var found = previousVerifications.get(key);
            if (found != null) {
                newVerifications.put(key, found);
            } else {
                signatureExpander.expand(key, originals, expanded);
            }
        }

        // start verification of any key that was not found in the previous result
        if (!expanded.isEmpty()) {
            newVerifications.putAll(
                    signatureVerifier.verify(previousResult.txInfo().signedBytes(), expanded));
        }

        return new VerificationResult(txBody, newVerifications);
    }

    private record VerificationResult(
            @NonNull TransactionBody txBody, @NonNull Map<Key, SignatureVerificationFuture> keyVerifications) {
        @SuppressWarnings("DataFlowIssue")
        public VerificationResult(PreHandleResult result) {
            this(result.txInfo().txBody(), result.verificationResults());
        }
    }
}
