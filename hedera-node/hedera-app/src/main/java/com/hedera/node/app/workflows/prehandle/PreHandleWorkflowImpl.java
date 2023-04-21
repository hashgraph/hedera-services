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

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.signature.SignatureVerificationResult;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
     * Creates a new instance of {@code PreHandleWorkflowImpl}.
     *
     * @param exe the {@link ExecutorService} for managing submission of pre-handle tasks. This service should be
     *            configured with a maximum number of threads, and with a maximum blocking queue length. This way,
     *            back-pressure can be applied to the hashgraph platform if the event intake rate is too high.
     * @param dispatcher the {@link TransactionDispatcher} for invoking the {@link TransactionHandler} for each
     *                   transaction.
     * @param transactionChecker the {@link TransactionChecker} for parsing and verifying the transaction
     * @param signatureVerifier the {@link SignatureVerifier} to verify signatures
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    @Inject
    public PreHandleWorkflowImpl(
            @NonNull final ExecutorService exe,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final SignatureVerifier signatureVerifier) {
        this.exe = requireNonNull(exe);
        this.dispatcher = requireNonNull(dispatcher);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.signatureVerifier = requireNonNull(signatureVerifier);
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
            final var future =
                    exe.submit(() -> preHandleTransaction(creator, readableStoreFactory, accountStore, platformTx));
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
                task.platformTx.setMetadata(PreHandleResult.unknownFailure());
            } catch (ExecutionException e) {
                logger.error("Unexpected error while pre handling a transaction!", e);
                task.platformTx.setMetadata(PreHandleResult.unknownFailure());
            } catch (TimeoutException e) {
                logger.error("Timed out while waiting for a transaction to be pre handled!", e);
                task.platformTx.setMetadata(PreHandleResult.unknownFailure());
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
            return PreHandleResult.unknownFailure();
        }

        // 2. Get Payer Account
        final var txId = txInfo.txBody().transactionID();
        assert txId != null : "TransactionID should never be null, transactionChecker forbids it";
        final var payer = txId.accountID();
        assert payer != null : "Payer account cannot be null, transactionChecker forbids it";
        final var payerAccount = accountStore.getAccountById(payer);
        if (payerAccount == null) {
            // If the payer account doesn't exist, then we cannot gather signatures for it, and will need to do
            // so later during the handle phase. Technically, we could still try to gather and verify the other
            // signatures, but that might be tricky and complicated with little gain. So just throw.
            return PreHandleResult.preHandleFailure(creator, PAYER_ACCOUNT_NOT_FOUND, txInfo, null);
        }

        // 3. Collect payer signature checks. Any PreHandleResult created from this point on MUST include
        //    the payerVerificationFuture.
        final var payerVerificationFuture = signatureVerifier.verify(
                txInfo.signedBytes(), txInfo.signatureMap().sigPairOrThrow(), payerAccount.keyOrThrow());

        // 4. Create the PreHandleContext. This will get reused across several calls to the transaction handlers
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
            throw new RuntimeException(
                    "Payer account disappeared between preHandle and preHandleContext creation!", preCheck);
        }

        // 5. Call Pre-Transaction Handlers
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

        // 6. Collect additional TransactionSignatures
        final var nonPayerKeys = context.requiredNonPayerKeys();
        final var nonPayerFutures = new ArrayList<Future<Boolean>>();
        for (final var key : nonPayerKeys) {
            final var future = signatureVerifier.verify(
                    txInfo.signedBytes(), txInfo.signatureMap().sigPairOrThrow(), key);
            nonPayerFutures.add(future);
        }

        // 7. Create and return TransactionMetadata
        final var signatureVerificationResult = new SignatureVerificationResult(nonPayerFutures);
        return new PreHandleResult(payer, OK, txInfo, payerVerificationFuture, signatureVerificationResult, null);
    }

    /** A platform transaction and the future that produces its {@link PreHandleResult} */
    private record WorkItem(@NonNull Transaction platformTx, @NonNull Future<PreHandleResult> future) {}
}
