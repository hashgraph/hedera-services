/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.api.implementation.workflows.prehandle.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.token.CryptoQueryHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.services.api.implementation.SessionContext;
import com.hedera.services.api.implementation.TransactionMetadataImpl;
import com.hedera.services.api.implementation.workflows.ingest.IngestChecker;
import com.hedera.services.api.implementation.workflows.ingest.PreCheckException;
import com.hedera.services.api.implementation.workflows.prehandle.PreHandleDispatcher;
import com.hedera.services.api.implementation.workflows.prehandle.PreHandleWorkflow;
import com.hederahashgraph.api.proto.java.*;
import com.swirlds.common.system.events.Event;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/** Default implementation of {@link PreHandleWorkflow} */
public class PreHandleWorkflowImpl implements PreHandleWorkflow {

    /**
     * Per-thread shared resources are shared in a {@link SessionContext}. We store these in a
     * thread local, because we do not have control over the thread pool used by the underlying gRPC
     * server.
     */
    private static final ThreadLocal<SessionContext> SESSION_CONTEXT_THREAD_LOCAL =
            ThreadLocal.withInitial(
                    () ->
                            new SessionContext(
                                    Query.parser(),
                                    Transaction.parser(),
                                    SignedTransaction.parser(),
                                    TransactionBody.parser()));

    private final ExecutorService exe;
    private final Supplier<CryptoQueryHandler> query;
    private final IngestChecker checker;
    private final PreHandleDispatcher dispatcher;

    /**
     * Constructor of {@code PreHandleWorkflowImpl}
     *
     * @param exe the {@link ExecutorService} to use when submitting new tasks
     * @param query a {@link Supplier} of {@link CryptoQueryHandler} used to request account data
     * @param ingestChecker an {@link IngestChecker} that contains all validators
     * @param dispatcher a {@link PreHandleDispatcher} that handles the actual request
     * @throws NullPointerException if any of the paramters is {@code null}
     */
    public PreHandleWorkflowImpl(
            ExecutorService exe,
            Supplier<CryptoQueryHandler> query,
            IngestChecker ingestChecker,
            PreHandleDispatcher dispatcher) {
        this.exe = requireNonNull(exe);
        this.query = requireNonNull(query);
        this.checker = requireNonNull(ingestChecker);
        this.dispatcher = requireNonNull(dispatcher);
    }

    @Override
    public void start(Event event) {
        requireNonNull(event);
        // Each transaction in the event will go through pre-handle using a background thread
        // from the executor service. The Future representing that work is stored on the
        // platform transaction. The HandleTransactionWorkflow will pull this future back
        // out and use it to block until the pre handle work is done, if needed.
        final var itr = event.transactionIterator();
        while (itr.hasNext()) {
            final var platformTx = itr.next();
            final var future = exe.submit(() -> preHandle(platformTx));
            platformTx.setMetadata(future);
        }
    }

    private TransactionMetadata preHandle(
            com.swirlds.common.system.transaction.Transaction platformTx) {
        try {
            final var ctx = SESSION_CONTEXT_THREAD_LOCAL.get();
            final var txBytes = platformTx.getContents();

            // 0. Parse the transaction object from the txBytes (protobuf)
            final var tx = ctx.txParser().parseFrom(txBytes);

            // 1. Parse and validate the signed transaction
            final var signedTransaction =
                    ctx.signedParser().parseFrom(tx.getSignedTransactionBytes());
            checker.checkSignedTransaction(signedTransaction);

            // 2. Parse and validate the TransactionBody.
            final var txBody = ctx.txBodyParser().parseFrom(signedTransaction.getBodyBytes());
            final var accountOpt =
                    query.get().getAccountById(txBody.getTransactionID().getAccountID());
            if (accountOpt.isEmpty()) {
                // This is an error condition. No account!
                throw new PreCheckException(ResponseCodeEnum.INVALID_ACCOUNT_ID, "Account missing");
            }
            final var account = accountOpt.get();
            checker.checkTransactionBody(txBody, account);

            // 3. Validate signature
            final var key = account.getKey();
            checker.checkSignatures(
                    tx.getSignedTransactionBytes(), signedTransaction.getSigMap(), key);

            // 4. If signatures all check out, then check the throttles. Ya, I'd like to check
            //    throttles way back on step 1, but we need to verify whether the account is
            //    a privileged account (less than 0.0.100) and if so skip throttles. Without
            //    a way to authenticate the payload any earlier than step 3, we have to do it now.
            final var kind = txBody.getDataCase();
            checker.checkThrottles(kind);

            // Now that all the standard "ingest" checks are done, delegate to the appropriate
            // service module to do any service-specific pre-checks.
            dispatcher.dispatch(txBody);

            // Looks like we've done all we can and still haven't encountered any kind of problem,
            // so we can go ahead and create and return the transaction metadata.
            // TODO Need to provide a way for service modules to do their own preloading and save it
            //      in the md
            return new TransactionMetadataImpl(txBody);
        } catch (PreCheckException preCheckException) {
            // TODO Actually we should have a more specific kind of metadata here maybe? And
            //      definitely don't log.
            return new TransactionMetadata.UnknownErrorTransactionMetadata(preCheckException);
        } catch (Exception ex) {
            // Some unknown and unexpected failure happened. If this was non-deterministic, I could
            // end up with an ISS. It is critical that I log whatever happened, because we should
            // have caught all legitimate failures in another catch block.
            // TODO Log it.
            return new TransactionMetadata.UnknownErrorTransactionMetadata(ex);
        }
    }
}
