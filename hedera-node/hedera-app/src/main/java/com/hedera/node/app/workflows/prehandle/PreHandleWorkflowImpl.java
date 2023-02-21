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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.StoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.swirlds.common.system.events.Event;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implementation of {@link PreHandleWorkflow} */
public class PreHandleWorkflowImpl implements PreHandleWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(PreHandleWorkflowImpl.class);

    /**
     * Per-thread shared resources are shared in a {@link SessionContext}. We store these in a
     * thread local, because we do not have control over the thread pool used by the underlying gRPC
     * server.
     */
    private static final ThreadLocal<SessionContext> SESSION_CONTEXT_THREAD_LOCAL =
            ThreadLocal.withInitial(SessionContext::new);

    private final WorkflowOnset onset;
    private final TransactionDispatcher dispatcher;
    private final Function<Supplier<?>, CompletableFuture<?>> runner;

    /**
     * Constructor of {@code PreHandleWorkflowImpl}
     *
     * @param exe the {@link ExecutorService} to use when submitting new tasks
     * @param dispatcher the {@link TransactionDispatcher} that will call transaction-specific
     *     {@code preHandle()}-methods
     * @param onset the {@link WorkflowOnset} that pre-processes the {@link byte[]} of a transaction
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    public PreHandleWorkflowImpl(
            @NonNull final ExecutorService exe,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final WorkflowOnset onset) {
        requireNonNull(exe);

        this.dispatcher = requireNonNull(dispatcher);
        this.onset = requireNonNull(onset);
        this.runner = supplier -> CompletableFuture.supplyAsync(supplier, exe);
    }

    // Used only for testing
    PreHandleWorkflowImpl(
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final WorkflowOnset onset,
            @NonNull final Function<Supplier<?>, CompletableFuture<?>> runner) {
        this.dispatcher = requireNonNull(dispatcher);
        this.onset = requireNonNull(onset);
        this.runner = requireNonNull(runner);
    }

    @Override
    public synchronized void start(@NonNull final HederaState state, @NonNull final Event event) {
        requireNonNull(state);
        requireNonNull(event);

        // Each transaction in the event will go through pre-handle using a background thread
        // from the executor service. The Future representing that work is stored on the
        // platform transaction. The HandleTransactionWorkflow will pull this future back
        // out and use it to block until the pre handle work is done, if needed.
        final ArrayList<CompletableFuture<?>> futures = new ArrayList<>();
        final var itr = event.transactionIterator();
        while (itr.hasNext()) {
            final var platformTx = itr.next();
            final var future = runner.apply(() -> preHandle(state, platformTx));
            platformTx.setMetadata(future);
            futures.add(future);
        }

        // wait until all transactions were processed before returning
        final CompletableFuture<?>[] array = futures.toArray(new CompletableFuture<?>[0]);
        CompletableFuture.allOf(array).join();
    }

    private TransactionMetadata preHandle(
            final HederaState state, final com.swirlds.common.system.transaction.Transaction platformTx) {
        TransactionBody txBody = null;
        AccountID payerID = null;
        try {
            final var ctx = SESSION_CONTEXT_THREAD_LOCAL.get();
            final var txBytes = platformTx.getContents();

            // 1. Parse the Transaction and check the syntax
            final var onsetResult = onset.parseAndCheck(ctx, txBytes);
            txBody = onsetResult.txBody();
            payerID = txBody.transactionID().accountID();

            // 2. Call PreTransactionHandler to do transaction-specific checks, get list of required
            // keys, and prefetch required data
            final var storeFactory = new StoreFactory(state);
            final var accountStore = storeFactory.getAccountStore();
            final var handlerContext = new PreHandleContext(accountStore, txBody);
            dispatcher.dispatchPreHandle(storeFactory, handlerContext);

            // 3. Prepare signature-data
            // TODO: Prepare signature-data once this functionality was implemented

            // 4. Verify signatures
            // TODO: Verify signature via the platform once this functionality was implemented

            // 5. Return TransactionMetadata
            return createTransactionMetadata(storeFactory.getUsedStates(), handlerContext);

        } catch (PreCheckException preCheckException) {
            return createInvalidTransactionMetadata(txBody, payerID, preCheckException.responseCode());
        } catch (Exception ex) {
            // Some unknown and unexpected failure happened. If this was non-deterministic, I could
            // end up with an ISS. It is critical that I log whatever happened, because we should
            // have caught all legitimate failures in another catch block.
            LOG.error("An unexpected exception was thrown during pre-handle", ex);
            return createInvalidTransactionMetadata(txBody, payerID, ResponseCodeEnum.UNKNOWN);
        }
    }

    @NonNull
    private static TransactionMetadata createTransactionMetadata(
            @NonNull final Map<String, ReadableStates> usedStates, @NonNull final PreHandleContext context) {
        final List<TransactionMetadata.ReadKeys> readKeys = extractAllReadKeys(usedStates);
        return new TransactionMetadata(context, readKeys);
    }

    @NonNull
    private static TransactionMetadata createInvalidTransactionMetadata(
            @Nullable final TransactionBody txBody,
            @Nullable final AccountID payerID,
            @NonNull final ResponseCodeEnum responseCode) {
        return new TransactionMetadata(txBody, payerID, responseCode);
    }

    private static List<TransactionMetadata.ReadKeys> extractAllReadKeys(
            @NonNull final Map<String, ReadableStates> usedStates) {
        return usedStates.entrySet().stream()
                .flatMap(entry -> {
                    final String statesKey = entry.getKey();
                    final ReadableStates readableStates = entry.getValue();
                    return extractReadKeysFromReadableStates(statesKey, readableStates);
                })
                .toList();
    }

    private static Stream<TransactionMetadata.ReadKeys> extractReadKeysFromReadableStates(
            @NonNull final String statesKey, @NonNull final ReadableStates readableStates) {
        return readableStates.stateKeys().stream()
                .map(stateKey -> {
                    final Set<? extends Comparable<?>> readKeys =
                            readableStates.get(stateKey).readKeys();
                    return new TransactionMetadata.ReadKeys(statesKey, stateKey, readKeys);
                })
                .filter(listEntry -> !listEntry.readKeys().isEmpty());
    }
}
