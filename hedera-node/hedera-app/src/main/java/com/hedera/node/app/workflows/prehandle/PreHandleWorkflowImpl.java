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
package com.hedera.node.app.workflows.prehandle;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.ServicesAccessor;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.service.token.CryptoQueryHandler;
import com.hedera.node.app.spi.meta.ErrorTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.ingest.IngestChecker;
import com.hedera.node.app.workflows.ingest.PreCheckException;
import com.hederahashgraph.api.proto.java.*;
import com.swirlds.common.system.events.Event;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default implementation of {@link PreHandleWorkflow} */
public class PreHandleWorkflowImpl implements PreHandleWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(PreHandleWorkflowImpl.class);

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
    private final ServicesAccessor servicesAccessor;
    private final IngestChecker checker;

    private HederaState lastUsedState;
    private PreHandleDispatcher dispatcher;
    private CryptoQueryHandler cryptoQueryHandler;

    /**
     * Constructor of {@code PreHandleWorkflowImpl}
     *
     * @param exe the {@link ExecutorService} to use when submitting new tasks
     * @param servicesAccessor the {@link ServicesAccessor} with references to all {@link
     *     com.hedera.node.app.spi.Service}-implementations
     * @param ingestChecker an {@link IngestChecker} that contains all validators
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    public PreHandleWorkflowImpl(
            @Nonnull final ExecutorService exe,
            @Nonnull final ServicesAccessor servicesAccessor,
            @Nonnull final IngestChecker ingestChecker) {
        this.exe = requireNonNull(exe);
        this.servicesAccessor = requireNonNull(servicesAccessor);
        this.checker = requireNonNull(ingestChecker);
    }

    @Override
    public synchronized void start(@Nonnull final HederaState state, @Nonnull final Event event) {
        requireNonNull(state);
        requireNonNull(event);

        // If the latest immutable state has changed, we need to adjust the dispatcher and the
        // query-handler.
        if (!Objects.equals(state, lastUsedState)) {
            dispatcher = new PreHandleDispatcherImpl(state, servicesAccessor);
            final var cryptoState = state.createReadableStates(HederaState.CRYPTO_SERVICE);
            cryptoQueryHandler = servicesAccessor.cryptoService().createQueryHandler(cryptoState);
            lastUsedState = state;
        }

        // Each transaction in the event will go through pre-handle using a background thread
        // from the executor service. The Future representing that work is stored on the
        // platform transaction. The HandleTransactionWorkflow will pull this future back
        // out and use it to block until the pre handle work is done, if needed.
        final var itr = event.transactionIterator();
        while (itr.hasNext()) {
            final var platformTx = itr.next();
            final var future =
                    exe.submit(() -> preHandle(dispatcher, cryptoQueryHandler, platformTx));
            platformTx.setMetadata(future);
        }
    }

    private TransactionMetadata preHandle(
            final PreHandleDispatcher dispatcher,
            final CryptoQueryHandler query,
            final com.swirlds.common.system.transaction.Transaction platformTx) {
        TransactionBody txBody = null;
        try {
            final var ctx = SESSION_CONTEXT_THREAD_LOCAL.get();
            final var txBytes = platformTx.getContents();

            // 1. Parse the transaction object from the txBytes (protobuf)
            final var tx = ctx.txParser().parseFrom(txBytes);
            checker.checkTransaction(tx);

            // 2. Parse and validate the signed transaction
            final var signedTransaction =
                    ctx.signedParser().parseFrom(tx.getSignedTransactionBytes());
            checker.checkSignedTransaction(signedTransaction);

            // 3. Parse and validate the TransactionBody.
            txBody = ctx.txBodyParser().parseFrom(signedTransaction.getBodyBytes());
            final var accountOpt = query.getAccountById(txBody.getTransactionID().getAccountID());
            if (accountOpt.isEmpty()) {
                // This is an error condition. No account!
                throw new PreCheckException(PAYER_ACCOUNT_NOT_FOUND, "Account missing");
            }
            final var account = accountOpt.get();
            checker.checkTransactionBody(txBody, account);

            // 4. Call PreTransactionHandler to do transaction-specific checks, get list of required
            // keys, and prefetch required data
            final var metadata = dispatcher.dispatch(txBody);

            // 5. Check signatures
            checker.verifySignatures(
                    platformTx, signedTransaction.getSigMap(), metadata.getReqKeys());

            return metadata;

        } catch (PreCheckException preCheckException) {
            return new ErrorTransactionMetadata(
                    preCheckException.responseCode(), preCheckException, txBody);
        } catch (Exception ex) {
            // Some unknown and unexpected failure happened. If this was non-deterministic, I could
            // end up with an ISS. It is critical that I log whatever happened, because we should
            // have caught all legitimate failures in another catch block.
            LOG.error("An unexpected exception was thrown during pre-handle", ex);
            return new ErrorTransactionMetadata(ResponseCodeEnum.UNKNOWN, ex, txBody);
        }
    }
}
