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
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.RecordCache;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.events.Event;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
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
     * Per-thread shared resources are shared in a {@link SessionContext}. We store these in a thread local, because we
     * do not have control over the thread pool used by the underlying gRPC server.
     */
    private static final ThreadLocal<SessionContext> SESSION_CONTEXT_THREAD_LOCAL =
            ThreadLocal.withInitial(SessionContext::new);

    private final TransactionChecker transactionChecker;
    private final TransactionDispatcher dispatcher;
    private final SignaturePreparer signaturePreparer;
    private final Cryptography cryptography;
    private final ExecutorService exe;
    private final RecordCache recordCache;
    private final AccountAccess accountAccess;
    private final Function<Long, AccountID> creatorIdToAccountId;

    /**
     * Constructor of {@code PreHandleWorkflowImpl}
     *
     * @param exe the {@link ExecutorService} to use when submitting new tasks
     * @param dispatcher the {@link TransactionDispatcher} that will call transaction-specific
     * {@code preHandle()}-methods
     * @param transactionChecker the {@link TransactionChecker} that pre-processes the {@link byte[]} of a transaction
     * @param signaturePreparer the {@link SignaturePreparer} to prepare signatures
     * @param cryptography the {@link Cryptography} component used to verify signatures
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    @Inject
    public PreHandleWorkflowImpl(
            @NonNull final ExecutorService exe,
            @NonNull final AccountAccess accountAccess,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final SignaturePreparer signaturePreparer,
            @NonNull final Cryptography cryptography,
            @NonNull final RecordCache recordCache,
            @NonNull final Function<Long, AccountID> creatorIdToAccountId) {
        requireNonNull(exe);
        this.dispatcher = requireNonNull(dispatcher);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.signaturePreparer = requireNonNull(signaturePreparer);
        this.cryptography = requireNonNull(cryptography);
        this.exe = requireNonNull(exe);
        this.creatorIdToAccountId = requireNonNull(creatorIdToAccountId);
        this.recordCache = requireNonNull(recordCache);
        this.accountAccess = requireNonNull(accountAccess);
    }

    // I have to keep in mind that I also want a synchronous flow because the HandleWorkflowImpl is going
    // to need a way to call back into the PreHandleWorkflowImpl in case it needs to run pre-handle, and we
    // cannot call the method with an Event, and we don't want to do it async either.

    /** {@inheritDoc} */
    @Override
    public void start(@NonNull final HederaState state, @NonNull final Event event) {
        final var creatorId = event.getCreatorId();
        final var creator = creatorIdToAccountId.apply(creatorId);
        assert creator != null : "Creator ID " + creatorId + " is not a valid account ID!"; // TODO Maybe use a real interface with @NonNull annotated return.

        // Each background thread, processing each transaction, will add to this queue, concurrently, their
        // TransactionSignatures that need to be verified. At the end, we will dump these into a list,
        // and pass them as a batch to the platform to verify asynchronously.
        final var transactionSignatures = new ConcurrentLinkedQueue<TransactionSignature>();

        // Using the executor service, submit a task for each transaction in the event.
        final var itr = event.transactionIterator();
        while (itr.hasNext()) {
            // TODO Are we supposed to skip System transactions? I assume so!
            final var platformTx = itr.next();
            if (platformTx.isSystem()) continue;

            platformTx.setMetadata(exe.submit(() -> {
                // 1. Parse the Transaction and check the syntax
                final var ctx = SESSION_CONTEXT_THREAD_LOCAL.get();
                final var txBytes = Bytes.wrap(platformTx.getContents());
                final TransactionInfo txInfo;
                try {
                    txInfo = transactionChecker.parseAndCheck(ctx, txBytes);
                } catch (PreCheckException preCheck) {
                    // The node SHOULD have verified the transaction before it was submitted to the network.
                    // Since it didn't, it has failed in its due diligence and will be charged accordingly.
                    return PreHandleResult.nodeDueDiligenceFailure(creator, preCheck.responseCode());
                } catch (Exception e) {
                    // If some random exception happened, then we should not charge the node for it. Instead,
                    // we will just record the exception and try again during handle. Then if we fail again
                    // at handle, then we will throw away the transaction (hopefully, deterministically!)
                    return PreHandleResult.unknownFailure(creator, e);
                }

                // 2. Deduplicate
                final var txBody = txInfo.txBody();
                final var txId = txBody.transactionID();
                assert txId != null : "TransactionID should never be null, transactionChecker forbids it";
                final var duplicate = recordCache.isReceiptPresent(txId);
                if (duplicate && creator.equals(txBody.nodeAccountID())) {
                    // If the transaction is a duplicate, and the node is the payer, then the node has
                    // failed a due-diligence check. It is trying to send multiple transactions through
                    // consensus with the same TransactionID! We'll charge the node for this.
                    return PreHandleResult.nodeDueDiligenceFailure(creator, DUPLICATE_TRANSACTION, txInfo);
                }

                // 3. Get Payer Account
                // TODO I don't like AccountAccess because it doesn't let me track what account was accessed
                //  and it doesn't let me control what state is being used!!
                final var payer = txId.accountID();
                assert payer != null : "Payer account cannot be null, transactionChecker forbids it";
                final var accountOptional = accountAccess.getAccountById(payer);
                if (accountOptional.isEmpty()) {
                    // If the payer account does not exist, it may be that the node that submitted this
                    // transaction didn't know that. Or it may be that the account will be created before
                    // this transaction is handled. So we will just record the fact that we were unable to
                    // get the payer account, and try again during handle. If we fail during handle, then
                    // we have no choice but to charge the node then.
                    return PreHandleResult.preHandleFailure(creator, PAYER_ACCOUNT_NOT_FOUND, txInfo);
                }

                // 4. Collect payer TransactionSignatures
                final var payerAccount = accountOptional.get();
                final var payerSig = signaturePreparer.prepareSignature(state, txBytes, txInfo.signatureMap(), payer); // TODO This is wrong, shouldn't take the AccountID
                transactionSignatures.add(payerSig);
                // TODO What happens if prepareSignature throws an exception? Can it?

                // If this is a duplicate transaction, then we only want to verify the payer signature,
                // and then we can skip the rest of the pre-handle logic.
                if (duplicate) {
                    // TODO Sig results should be included in the PreHandleResult
                    payerSig.waitForFuture();
                    return PreHandleResult.preHandleFailure(payer, DUPLICATE_TRANSACTION, txInfo);
                }

                // 5. Call Pre-Transaction Handlers
                final var storeFactory = new ReadableStoreFactory(state); // TODO This looks out of place
                final var accountStore = storeFactory.createAccountStore();
                final var context = new PreHandleContext(accountStore, txInfo.txBody(), OK);
                try {
                    dispatcher.dispatchPreHandle(storeFactory, context);

                    // TODO Deal with potential inner context
//            final var innerContext = context.getInnerContext();
//            TransactionMetadata innerMetadata = null;
//            if (innerContext != null) {
//                // VERIFY: the txBytes used for inner transactions is the same as the outer transaction
//                final var innerPayerSignature = verifyPayerSignature(state, innerContext, txBytes, signatureMap);
//                final var innerOtherSignatures = verifyOtherSignatures(state, innerContext, txBytes, signatureMap);
//                innerMetadata = createTransactionMetadata(
//                        innerContext, signatureMap, innerPayerSignature, innerOtherSignatures, null);
//            }

                } catch (PreCheckException preCheck) {
                    // Pre-handle is the time for the service to perform semantic checks on the transaction.
                    // It is quite possible those semantic checks will fail and throw a PreCheckException.
                    // In that case, the payer will end up paying for the transaction.
                    return PreHandleResult.preHandleFailure(payer, preCheck.responseCode(), txInfo);
                }

                // 6. Collection additional TransactionSignatures
                final var nonPayerKeys = context.getRequiredNonPayerKeys();
                final var signatures = signaturePreparer.prepareSignatures(state, txBytes, txInfo.signatureMap(), nonPayerKeys);
                // TODO What happens if prepareSignatures throws an exception? Can it?
                transactionSignatures.addAll(signatures.values());

                // 7. Block on the TransactionSignatures
                payerSig.waitForFuture();
                for (final var sig : signatures.values()) {
                    sig.waitForFuture();
                }
                // TODO What happens if we get an exception from one of these?

                // 8. Create and return TransactionMetadata
                // TODO Sig results should be included in the PreHandleResult
                return new PreHandleResult(txInfo, payer, OK, null, null);
                // TODO I did NOT keep a record of what state was accessed during pre-handle, and that was the
                //  whole point!! That needs to be done.
            }));
        }

        // Pass these to the platform to verify. Each individual TransactionSignature has its own Future
        // which is used to determine when the signature verification is completed.
        cryptography.verifyAsync(new ArrayList<>(transactionSignatures));
    }
}
