/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.prehandle;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.signature.SignatureVerificationResult;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.ReceiptCache;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.inject.Singleton;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static java.util.Objects.requireNonNull;

/** Implementation of {@link PreHandleWorkflow} */
@Singleton
public class PreHandleWorkflowImpl implements PreHandleWorkflow {
    private static final Logger logger = LogManager.getLogger(PreHandleWorkflowImpl.class);
    /**
     * Used to verify basic syntactic and semantic validity of a transaction.
     *
     * <p>The hashgraph platform gossips {@link Event}s between nodes. Each {@link Event} contains zero or
     * more transactions. We have to parse the bytes for those transactions, and check their validity both
     * semantically and syntactically. This is done by the {@link TransactionChecker}. It is possible that
     * a misbehaving node, or a malicious node, will gossip a transaction that is invalid.
     */
    private final TransactionChecker transactionChecker;
    /** Dispatches transactions to the appropriate {@link TransactionHandler} based on the type of transaction. */
    private final TransactionDispatcher dispatcher;
    /** Verifies signatures */
    private final SignatureVerifier signatureVerifier;
    /**
     * The {@link ExecutorService} to use for submitting tasks for transaction pre-handling.
     *
     * <p>Each transaction is handled in a separate thread. The {@link ExecutorService} is used to buffer up these
     * tasks and manage a set of threads that will handle the tasks. In the future when the hashgraph platform has
     * a resource manager, we will use that instead. By supplying this executor service, the code that constructs this
     * object can control the number of threads that are used to handle transactions. This is useful for testing and
     * for proper configuration.
     */
    private final ExecutorService exe;
    /**
     * The {@link ReceiptCache} is used to check if a transaction has already been processed, to avoid processing
     * the same transaction twice. Each transaction has a {@link TransactionID} that is unique for each transaction.
     */
    private final ReceiptCache receiptCache;

    /**
     * Creates a new instance of {@code PreHandleWorkflowImpl}.
     *
     * @param exe the {@link ExecutorService} for managing submission of pre-handle tasks. This service should be
     *            configured with a maximum number of threads, and with a maximum blocking queue length. This way,
     *            back-pressure can be applied to the hashgraph platform if the event intake rate is too high.
     * @param dispatcher the {@link TransactionDispatcher} for invoking the {@link TransactionHandler} for each
     *                   transaction.
     * @param transactionChecker the {@link TransactionChecker} for parsing and verifying the transaction
     * @param signatureVerifier the {@link SignatureVerifier} to verify signatures
     * @param receiptCache the {@link ReceiptCache} used to check if a transaction has already been processed
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    @Inject
    public PreHandleWorkflowImpl(
            @NonNull final ExecutorService exe,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull final ReceiptCache receiptCache) {
        this.exe = requireNonNull(exe);
        this.dispatcher = requireNonNull(dispatcher);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.signatureVerifier = requireNonNull(signatureVerifier);
        this.receiptCache = requireNonNull(receiptCache);
    }

    /** {@inheritDoc} */
    @Override
    public void preHandle(
            @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final AccountID creator,
            @NonNull final Iterator<Transaction> transactions) {

        requireNonNull(readableStoreFactory);
        requireNonNull(creator);
        requireNonNull(transactions);

        // Used for looking up payer account information.
        final var accountStore = readableStoreFactory.createAccountStore();

        // Using the executor service, submit a task for each transaction in the event.
        final var tasks = new ArrayList<WorkItem>(1000); // Some arbitrary number
        while (transactions.hasNext()) {
            // Skip platform (system) transactions.
            final var platformTx = transactions.next();
            if (platformTx.isSystem()) continue;

            // Submit the task to the executor service and put the resulting Future as the metadata on the transaction
            final var future = exe.submit(() ->
                    preHandleTransaction(creator, readableStoreFactory, accountStore, platformTx));
            tasks.add(new WorkItem(platformTx, future));
        }

        // Waits for all the background threads to complete their work and stores the resulting PreHandleResult
        // as the transaction's metadata.
        for (final var task : tasks) {
            try {
                final var result = task.future.get(1, TimeUnit.SECONDS);
                task.platformTx.setMetadata(result);
            } catch (InterruptedException e) {
                // The thread should only ever be interrupted during shutdown, so we can just log the error.
                logger.error("Interrupted while waiting for a transaction to be pre handled.");
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                logger.error("Unexpected error while pre handling a transaction!", e);
            } catch (TimeoutException e) {
                logger.error("Timed out while waiting for a transaction to be pre handled!", e);
            }
        }
    }

    // For each transaction, we will use a background thread to parse the transaction, validate it, lookup the
    // payer, collect non-payer keys, and warm up the cache. Then, once all the keys have been collected, we will
    // pass the keys and signatures to the platform for verification.
    private PreHandleResult preHandleTransaction(
            @NonNull final AccountID creator,
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final Transaction platformTx) {

        // 1. Parse the Transaction and check the syntax
        final var txBytes = Bytes.wrap(platformTx.getContents());
        final TransactionInfo txInfo;
        try {
            txInfo = transactionChecker.parseAndCheck(txBytes);
        } catch (PreCheckException preCheck) {
            // The node SHOULD have verified the transaction before it was submitted to the network.
            // Since it didn't, it has failed in its due diligence and will be charged accordingly.
            return PreHandleResult.nodeDueDiligenceFailure(creator, preCheck.responseCode(), null);
        } catch (Throwable th) {
            // If some random exception happened, then we should not charge the node for it. Instead,
            // we will just record the exception and try again during handle. Then if we fail again
            // at handle, then we will throw away the transaction (hopefully, deterministically!)
            return PreHandleResult.unknownFailure(creator);
        }

        // 2. Deduplication check
        final var txBody = txInfo.txBody();
        final var txId = txBody.transactionID();
        assert txId != null : "TransactionID should never be null, transactionChecker forbids it";
        final var cacheItem = receiptCache.get(txId);
        // Check whether the transaction is a node-submitted duplicate, or a user-submitted duplicate, or not a
        // duplicate at all. A node-submitted duplicate ends processing with the node as the payer. A user-submitted
        // duplicate continues with processing until after we do payer verification, and then terminates. If it isn't
        // a duplicate at all, then we just keep going.
        final boolean userSubmittedDuplicate;
        if (cacheItem != null) {
            if (cacheItem.nodeAccountIDs().contains(creator)) {
                // The creator has sent the same transaction more than once! The node has failed a due-diligence check.
                // We'll charge the node for this.
                return PreHandleResult.nodeDueDiligenceFailure(creator, DUPLICATE_TRANSACTION, txInfo);
            } else {
                userSubmittedDuplicate = true;
            }
        } else {
            userSubmittedDuplicate = false;
        }
        // If we got here, it was either not a duplicate or a user-submitted duplicate, so we need to record
        // that the creator node has sent us this transaction for further deduplication checks in the future
        receiptCache.record(txId, creator);

        // 3. Get Payer Account
        final var payer = txId.accountID();
        assert payer != null : "Payer account cannot be null, transactionChecker forbids it";
        final var payerAccount = accountStore.getAccountById(payer);
        if (payerAccount == null) {
            // If the payer account doesn't exist, then we cannot gather signatures for it, and will need to do
            // so later during the handle phase. Technically, we could still try to gather and verify the other
            // signatures, but that might be tricky and complicated with little gain. So just throw.
            return PreHandleResult.preHandleFailure(creator, PAYER_ACCOUNT_NOT_FOUND, txInfo, null);
        }

        // 4. Collect payer TransactionSignatures. Any PreHandleResult created from this point on MUST include
        //    the payerVerificationFuture.
        final var payerVerificationFuture = new SignatureVerificationResult(List.of(signatureVerifier.verify(
                txInfo.signedBytes(),
                txInfo.signatureMap().sigPairOrThrow(),
                payerAccount.getKey())));

        // 5. Deduplication check (again). If the user submitted a duplicate transaction, then now is the time
        //    to record that they did so and make them the payer.
        if (userSubmittedDuplicate) {
            // The user has submitted the same transaction more than once. This is a perfectly reasonable thing
            // for the user to do. We will charge the payer, but we won't actually do any work for this
            // transaction other than the payer check.
            return PreHandleResult.preHandleFailure(payer, DUPLICATE_TRANSACTION, txInfo, payerVerificationFuture);
        }

        // NOTE: At this point we have completed the due-diligence checks. Any failures from here on out
        // can be attributed to the transaction itself, and not the node that submitted it.

        // 6. Create the PreHandleContext. This will get reused across several calls to the transaction handlers
        final PreHandleContext context;
        try {
            // NOTE: Once PreHandleContext is moved from being a concrete implementation in SPI, to being an Interface/
            // implementation pair, with the implementation in `hedera-app`, then we will change the constructor,
            // so I can pass the payer account in directly, since I've already looked it up. But I don't really want
            // that as a public API in the SPI, so for now, we do a double lookup. Boo.
            context = new PreHandleContext(accountStore, txInfo.txBody());
        } catch (PreCheckException preCheck) {
            // This should NEVER happen. The only way an exception is thrown from the PreHandleContext constructor
            // is if the payer account doesn't exist, but by the time we reach this line of code, we already know
            // that it does exist.
            throw new RuntimeException("Payer account disappeared between preHandle and preHandleContext creation!", preCheck);
        }

        // 7. Call Pre-Transaction Handlers
        try {
            // FUTURE: First, perform semantic checks on the transaction (TBD)
            // Then gather the signatures from the transaction handler
            dispatcher.dispatchPreHandle(storeFactory, context);
            // FUTURE: Finally, let the transaction handler do warm up of other state it may want to use later (TBD)
        } catch (PreCheckException preCheck) {
            // It is quite possible those semantic checks and other tasks will fail and throw a PreCheckException.
            // In that case, the payer will end up paying for the transaction.
            return PreHandleResult.preHandleFailure(payer, preCheck.responseCode(), txInfo, payerVerificationFuture);
        }

        // 8. Collect additional TransactionSignatures
        final var nonPayerKeys = context.requiredNonPayerKeys();
        final var nonPayerFutures = new ArrayList<Future<Boolean>>();
        for (final var key : nonPayerKeys) {
            final var future = signatureVerifier.verify(
                    txInfo.signedBytes(),
                    txInfo.signatureMap().sigPairOrThrow(),
                    key);
            nonPayerFutures.add(future);
        }

        // 9. Create and return TransactionMetadata
        final var signatureVerificationResult = new SignatureVerificationResult(nonPayerFutures);
        return new PreHandleResult(payer, OK, txInfo, payerVerificationFuture, signatureVerificationResult, null);
    }

    /** A platform transaction and the future that produces its {@link PreHandleResult} */
    private record WorkItem(
        @NonNull Transaction platformTx,
        @NonNull Future<PreHandleResult> future) {
    }
}
