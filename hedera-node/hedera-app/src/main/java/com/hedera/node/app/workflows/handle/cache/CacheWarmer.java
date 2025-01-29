/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.cache;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.spi.workflows.WarmupContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * This class is used to warm up the cache. It is called at the beginning of a round with the current state
 * and the round. It will start a background thread which iterates through all transactions and calls the
 * {@link TransactionHandler#warm} method.
 */
@Singleton
public class CacheWarmer {

    private final TransactionChecker checker;
    private final TransactionDispatcher dispatcher;
    private final Executor executor;

    @Inject
    public CacheWarmer(
            @NonNull final TransactionChecker checker,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull @Named("CacheWarmer") final Executor executor) {
        this.checker = checker;
        this.dispatcher = requireNonNull(dispatcher);
        this.executor = requireNonNull(executor);
    }

    /**
     * Warms up the cache for the given round.
     *
     * @param state the current state
     * @param round the current round
     */
    public void warm(@NonNull final State state, @NonNull final Round round) {
        executor.execute(() -> {
            final ReadableStoreFactory storeFactory = new ReadableStoreFactory(state);
            final ReadableAccountStore accountStore = storeFactory.getStore(ReadableAccountStore.class);
            for (final ConsensusEvent event : round) {
                event.forEachTransaction(platformTransaction -> executor.execute(() -> {
                    final TransactionBody txBody = extractTransactionBody(platformTransaction);
                    if (txBody != null) {
                        final AccountID payerID = txBody.transactionIDOrElse(TransactionID.DEFAULT)
                                .accountID();
                        if (payerID != null) {
                            accountStore.warm(payerID);
                        }
                        final var context = new WarmupContextImpl(txBody, storeFactory);
                        dispatcher.dispatchWarmup(context);
                    }
                }));
            }
        });
    }

    @Nullable
    private TransactionBody extractTransactionBody(@NonNull final Transaction platformTransaction) {
        // First we check if the transaction was already parsed during pre-handle (should be almost always the case)
        final var metadata = platformTransaction.getMetadata();
        if (metadata instanceof PreHandleResult result) {
            return result.txInfo() == null ? null : result.txInfo().txBody();
        }

        // If not we parse it here using existing code. This is not ideal but should be rare.
        // We can potentially optimize this by limiting the code to the bare minimum needed
        // or keeping the result for later.
        try {
            final Bytes buffer = platformTransaction.getApplicationTransaction();
            return checker.parseAndCheck(buffer).txBody();
        } catch (PreCheckException ex) {
            return null;
        }
    }

    /**
     * The default implementation of {@link WarmupContext}.
     */
    public static class WarmupContextImpl implements WarmupContext {

        @NonNull
        private final TransactionBody txBody;

        @NonNull
        private final ReadableStoreFactory storeFactory;

        /**
         * Constructor of {@code WarmupContextImpl}
         *
         * @param txBody the {@link TransactionInfo} of the transaction
         * @param storeFactory the {@link ReadableStoreFactory} to create stores
         */
        public WarmupContextImpl(
                @NonNull final TransactionBody txBody, @NonNull final ReadableStoreFactory storeFactory) {
            this.txBody = txBody;
            this.storeFactory = storeFactory;
        }

        @NonNull
        @Override
        public TransactionBody body() {
            return txBody;
        }

        @NonNull
        @Override
        public <C> C createStore(@NonNull final Class<C> storeInterface) {
            return storeFactory.getStore(storeInterface);
        }
    }
}
