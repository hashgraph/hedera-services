/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.clock.SystemClock;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HandleWorkflow {

    private static final Logger LOG = LogManager.getLogger(HandleWorkflow.class);

    // TODO: Get full list of unrecoverable errors
    private static final Set<ResponseCodeEnum> UNRECOVERABLE_ERRORS = Set.of(
            INVALID_TRANSACTION, INVALID_TRANSACTION_BODY, TRANSACTION_EXPIRED);
    private static final long SIGNATURE_VERIFICATION_TIMEOUT_MS = 3000L;

    private final SystemClock systemClock;
    private final TransactionChecker transactionChecker;
    private final TransactionDispatcher dispatcher;

    @Inject
    public HandleWorkflow(
            @NonNull final SystemClock systemClock,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final TransactionDispatcher dispatcher) {
        this.systemClock = requireNonNull(systemClock, "The supplied argument 'systemClock' cannot be null");
        this.transactionChecker = requireNonNull(transactionChecker,
                "The supplied argument 'transactionChecker' cannot be null");
        this.dispatcher = requireNonNull(dispatcher, "The supplied argument 'dispatcher' cannot be null");
    }

    /**
     * Handles the next {@link Round}
     *
     * @param state the writable {@link HederaState} that this round will work on
     * @param round the next {@link Round} that needs to be processed
     */
    public void handleRound(@NonNull final HederaState state, @NonNull final Round round) {
        // handle each transaction in the round
        round.forEachTransaction(txn -> handleTransaction(state, txn));
    }

    private void handleTransaction(@NonNull final HederaState state, @NonNull final ConsensusTransaction platformTxn) {
        // skip system transactions
        if (platformTxn.isSystem()) {
            return;
        }

        // Advance system clock
        final Instant consensusTimestamp = platformTxn.getConsensusTimestamp();
        systemClock.advance(consensusTimestamp);

//        try {
//            final var context = prepareHandleContext(state, platformTxn, consensusTimestamp);
//            dispatcher.dispatchHandle(context);
//            finalizeTransaction(context);
//            commitState(context);
//            writeRecord(context);
//            updateReceipt(context);
//        } catch (HandleException e) {
//            writeFailureRecord(e);
//            updateFailureReceipt(e);
//        } catch (Throwable e) {
//            LOG.error("An unexpected exception was thrown during handle", e);
//            updateFatalErrorReceipt(platformTxn);
//        }


        // TODO: handle long scheduled transactions

        // TODO: handle system tasks
    }

    private HandleContextImpl prepareHandleContext(
            @NonNull final HederaState state,
            @NonNull final ConsensusTransaction platformTxn,
            @NonNull final Instant consensusTimestamp) throws HandleException {
        final var metadata = platformTxn.getMetadata();
        // We do not know how long transactions are kept in memory. Clearing metadata to avoid keeping it for too long.
        platformTxn.setMetadata(null);

        PreHandleResult preHandleResult;
        final List<Future<Object>> signatureVerifications;
//        if (preHandleStillValid(metadata)) {
//            final var previousResult = (PreHandleResult) metadata;
//            if (previousResult.isDueDiligenceFailure()) {
//                final var fee = calculateNetworkFee();
//                final var cryptoTransfer = createPenaltyPayment(fee);
//                return new HandleContextImpl();
//            }
//
//            if (previousResult.status() == OK) {
//                preHandleResult = addMissingSignatures(previousResult);
//            } else {
//                preHandleResult = preHandleWorkflow.preHandleTransaction(creator, storeFactory, platformTxn);
//            }
//        } else {
//            preHandleResult = preHandleWorkflow.preHandleTransaction(creator, storeFactory, platformTxn);
//        }
//
//        if (preHandleResult.status() != OK) {
//            throw new PreCheckException(preHandleResult.status());
//        }
//
//
//
//        if (! checkSignature(preHandleResult.payerVerification())) {
//            return new HandleContextImpl();
//        }

        throw new UnsupportedOperationException("Not implemented yet");
    }

    private boolean preHandleStillValid(@NonNull final Object metadata) {
        // TODO: Check config and other preconditions
        return metadata instanceof PreHandleResult;
    }

    private void verifyMissingSignatures() {
        throw new UnsupportedOperationException("Not implemented yet");

//        final var txBody = requireNonNull(preHandleResult.txnBody());
//
//        // extract keys and hollow accounts again
//        final var storeFactory = new ReadableStoreFactory(state);
//        final var context = new PreHandleContextImpl(storeFactory, txBody);
//        dispatcher.dispatchPreHandle(context);
//
//        // compare keys and hollow accounts
//        final var signatureData = new ArrayList<>();
//        signatureData.add(preHandleResult.payerSignature());
//        context.requiredNonPayerKeys().
//        for (final var key : context.requiredNonPayerKeys()) {
//            final var signatureData = preHandleResult.cryptoSignatures().get(key);
//            if (signatureData == null) {
//                signaturePreparer. ()
//                throw new PreCheckException(INVALID_TRANSACTION);
//            }
//            signatureData.add(preHandleResult.signatureMap().get(key));
//        }
//        Map<Key, TransactionSignature> signatureMap;
//        signatureMap.g
//        final var keys = context.requiredNonPayerKeys();
//
//        // initiate signature verification for delta
    }

    private void checkSignature(@NonNull final Future<SignatureVerification> signatureVerification) {
        throw new UnsupportedOperationException("Not implemented yet");

//        try {
//            signatureVerification.get(SIGNATURE_VERIFICATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
//        } catch (TimeoutException ex) {
//
//        }

    }
}
