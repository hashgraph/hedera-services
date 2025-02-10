/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.workflows.prehandle.PreHandleWorkflowImpl.isAtomicBatch;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A workflow to pre-handle transactions.
 */
public interface PreHandleWorkflow {
    Logger log = LogManager.getLogger(PreHandleWorkflow.class);

    /**
     * Starts the pre-handle transaction workflow of the {@link Event}
     *
     * @param readableStoreFactory      the {@link ReadableStoreFactory} that is used for looking up stores
     * @param creator                   The {@link AccountID} of the node that created these transactions
     * @param transactions              An {@link Stream} over all transactions to pre-handle
     * @param stateSignatureTxnCallback A callback to be called when encountering a {@link StateSignatureTransaction}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    void preHandle(
            @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final AccountID creator,
            @NonNull final Stream<Transaction> transactions,
            @NonNull final Consumer<StateSignatureTransaction> stateSignatureTxnCallback);

    /**
     * Starts the pre-handle transaction workflow for a single transaction.
     *
     * <p>If this method is called directly, pre-handle is done on the current thread.
     *
     * @param creator                   The {@link AccountID} of the node that created these transactions
     * @param storeFactory              The {@link ReadableStoreFactory} based on the current state
     * @param accountStore              The {@link ReadableAccountStore} based on the current state
     * @param applicationTxBytes        The {@link Transaction} to pre-handle
     * @param maybeReusableResult       The result of a previous call to the same method that may,
     * @param stateSignatureTxnCallback A callback to be called when encountering a {@link StateSignatureTransaction}
     *                                  depending on changes in state, be reusable for this call
     * @return The {@link PreHandleResult} of running pre-handle
     */
    @NonNull
    PreHandleResult preHandleTransaction(
            @NonNull AccountID creator,
            @NonNull ReadableStoreFactory storeFactory,
            @NonNull ReadableAccountStore accountStore,
            @NonNull Bytes applicationTxBytes,
            @Nullable PreHandleResult maybeReusableResult,
            @NonNull Consumer<StateSignatureTransaction> stateSignatureTxnCallback);

    /**
     * Starts the pre-handle transaction workflow for all transactions including inner transactions in an atomic batch.
     *
     * @param creator                   the node that created the transaction
     * @param storeFactory              the store factory
     * @param accountStore              the account store
     * @param applicationTxBytes        the transaction to be verified
     * @param maybeReusableResult       the previous result of pre-handle
     * @param stateSignatureTxnCallback the callback to be called when encountering a {@link StateSignatureTransaction}
     * @return the verification data for the transaction
     */
    default PreHandleResult preHandleAllTransactions(
            @NonNull AccountID creator,
            @NonNull ReadableStoreFactory storeFactory,
            @NonNull ReadableAccountStore accountStore,
            @NonNull Bytes applicationTxBytes,
            @Nullable PreHandleResult maybeReusableResult,
            @NonNull Consumer<StateSignatureTransaction> stateSignatureTxnCallback) {
        final var result = preHandleTransaction(
                creator,
                storeFactory,
                accountStore,
                applicationTxBytes,
                maybeReusableResult,
                stateSignatureTxnCallback);
        // If the transaction is an atomic batch, we need to pre-handle all inner transactions as well
        // and add their results to the outer transaction's pre-handle result
        if (result.txInfo() != null && isAtomicBatch(result.txInfo())) {
            final var resultPresent = maybeReusableResult != null
                    && maybeReusableResult.innerResults() != null
                    && !maybeReusableResult.innerResults().isEmpty();
            for (int i = 0;
                    i
                            < result.txInfo()
                                    .txBody()
                                    .atomicBatchOrThrow()
                                    .transactions()
                                    .size();
                    i++) {
                final var innerTx = result.txInfo()
                        .txBody()
                        .atomicBatchOrThrow()
                        .transactions()
                        .get(i);
                final var innerResult = preHandleTransaction(
                        creator,
                        storeFactory,
                        accountStore,
                        com.hedera.hapi.node.base.Transaction.PROTOBUF.toBytes(innerTx),
                        resultPresent ? maybeReusableResult.innerResults().get(i) : null,
                        ignore -> {});
                requireNonNull(result.innerResults()).add(innerResult);
            }
        }
        return result;
    }

    /**
     * This method gets all the verification data for the current transaction. If pre-handle was previously ran
     * successfully, we only add the missing keys. If it did not run or an error occurred, we run it again.
     * If there is a due diligence error, this method will return a CryptoTransfer to charge the node along with
     * its verification data.
     *
     * @param creator      the node that created the transaction
     * @param platformTxn  the transaction to be verified
     * @param storeFactory the store factory
     * @return the verification data for the transaction
     */
    @NonNull
    default PreHandleResult getCurrentPreHandleResult(
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn,
            @NonNull final ReadableStoreFactory storeFactory) {
        final var metadata = platformTxn.getMetadata();
        final PreHandleResult previousResult;
        if (metadata instanceof PreHandleResult result) {
            previousResult = result;
        } else {
            // This should be impossible since the Platform contract guarantees that StateLifecycles.onPreHandle()
            // is always called before StateLifecycles.onHandleTransaction(); and our preHandle() implementation
            // always sets the metadata to a PreHandleResult
            log.error(
                    "Received transaction without PreHandleResult metadata from node {} (was {})",
                    creator.nodeId(),
                    metadata);
            previousResult = null;
        }
        // We do not know how long transactions are kept in memory. Clearing metadata to avoid keeping it for too long.
        platformTxn.setMetadata(null);
        return preHandleAllTransactions(
                creator.accountId(),
                storeFactory,
                storeFactory.getStore(ReadableAccountStore.class),
                platformTxn.getApplicationTransaction(),
                previousResult,
                txns -> {});
    }
}
