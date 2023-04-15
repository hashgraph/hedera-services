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
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Implementation of {@link PreHandleWorkflow} */
@Singleton
public class PreHandleWorkflowImpl implements PreHandleWorkflow {

    private static final Logger LOG = LogManager.getLogger(PreHandleWorkflowImpl.class);

    private final TransactionChecker transactionChecker;
    private final TransactionDispatcher dispatcher;
    private final SignaturePreparer signaturePreparer;
    private final Cryptography cryptography;
    private final Function<Runnable, CompletableFuture<Void>> runner;

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
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final SignaturePreparer signaturePreparer,
            @NonNull final Cryptography cryptography) {
        requireNonNull(exe);
        this.dispatcher = requireNonNull(dispatcher);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.signaturePreparer = requireNonNull(signaturePreparer);
        this.cryptography = requireNonNull(cryptography);
        this.runner = runnable -> CompletableFuture.runAsync(runnable, exe);
    }

    // Used only for testing
    PreHandleWorkflowImpl(
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final SignaturePreparer signaturePreparer,
            @NonNull final Cryptography cryptography,
            @NonNull final Function<Runnable, CompletableFuture<Void>> runner) {
        this.dispatcher = requireNonNull(dispatcher);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.signaturePreparer = requireNonNull(signaturePreparer);
        this.cryptography = requireNonNull(cryptography);
        this.runner = requireNonNull(runner);
    }

    @Override
    public void start(@NonNull final HederaState state, @NonNull final Event event) {
        preHandle(requireNonNull(event).transactionIterator(), requireNonNull(state));
    }

    public void preHandle(@NonNull final Iterator<Transaction> itr, @NonNull final HederaState state) {
        // Each transaction in the event will go through pre-handle using a background thread
        // from the executor service. The Future representing that work is stored on the
        // platform transaction. The HandleTransactionWorkflow will pull this future back
        // out and use it to block until the pre handle work is done, if needed.
        final ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
        while (itr.hasNext()) {
            final var platformTx = itr.next();
            final var future = runner.apply(() -> {
                try {
                    final var metadata = securePreHandle(state, platformTx);
                    platformTx.setMetadata(metadata);
                } catch (final PreCheckException e) {
                    platformTx.setMetadata(createInvalidResult(e.responseCode()));
                }
            });
            futures.add(future);
        }

        // wait until all transactions were processed before returning
        final CompletableFuture<?>[] array = futures.toArray(new CompletableFuture<?>[0]);
        CompletableFuture.allOf(array).join();
    }

    private PreHandleResult securePreHandle(
            final HederaState state, final com.swirlds.common.system.transaction.Transaction platformTx)
            throws PreCheckException {
        try {
            return preHandle(state, platformTx);
        } catch (Exception ex) {
            // Some unknown and unexpected failure happened. If this was non-deterministic, I could
            // end up with an ISS. It is critical that I log whatever happened, because we should
            // have caught all legitimate failures in another catch block.
            LOG.error("An unexpected exception was thrown during pre-handle", ex);
            return createInvalidResult(UNKNOWN);
        }
    }

    PreHandleResult preHandle(
            final HederaState state, final com.swirlds.common.system.transaction.Transaction platformTx) {
        TransactionBody txBody;
        try {
            // Parse the Transaction and check the syntax
            final var txBytes = Bytes.wrap(platformTx.getContents());

            // 1. Parse the Transaction and check the syntax
            final var tx = transactionChecker.parse(txBytes);
            final var transactionInfo = transactionChecker.check(tx);
            txBody = transactionInfo.txBody();

            // 2. Call PreTransactionHandler to do transaction-specific checks, get list of required
            // keys, and prefetch required data
            final var storeFactory = new ReadableStoreFactory(state);
            final var accountStore = storeFactory.createAccountStore();
            final var context = new PreHandleContext(accountStore, txBody);
            dispatcher.dispatchPreHandle(storeFactory, context);

            // 3. Prepare and verify signature-data
            final var signatureMap = transactionInfo.signatureMap();
            final var txBodyBytes = transactionInfo.transaction().bodyBytes();
            final var payerSignature = verifyPayerSignature(state, context, txBodyBytes, signatureMap);
            final var otherSignatures = verifyOtherSignatures(state, context, txBodyBytes, signatureMap);

            // 4. Eventually prepare and verify signatures of inner transaction
            final var innerContext = context.innerContext();
            PreHandleResult innerResult = null;
            if (innerContext != null) {
                // VERIFY: the txBytes used for inner transactions is the same as the outer transaction
                final var innerPayerSignature = verifyPayerSignature(state, innerContext, txBytes, signatureMap);
                final var innerOtherSignatures = verifyOtherSignatures(state, innerContext, txBytes, signatureMap);
                innerResult = createResult(innerContext, signatureMap, innerPayerSignature, innerOtherSignatures, null);
            }

            // 5. Return PreHandleResult
            return createResult(context, signatureMap, payerSignature, otherSignatures, innerResult);

        } catch (PreCheckException preCheckException) {
            return createInvalidResult(preCheckException.responseCode());
        } catch (Exception ex) {
            // Some unknown and unexpected failure happened. If this was non-deterministic, I could
            // end up with an ISS. It is critical that I log whatever happened, because we should
            // have caught all legitimate failures in another catch block.
            LOG.error("An unexpected exception was thrown during pre-handle", ex);
            return createInvalidResult(UNKNOWN);
        }
    }

    @Nullable
    private TransactionSignature verifyPayerSignature(
            @NonNull final HederaState state,
            @NonNull final PreHandleContext context,
            @NonNull Bytes bytes,
            @NonNull SignatureMap signatureMap) {
        if (context.payerKey() == null) {
            return null;
        }

        final var payerSignature =
                signaturePreparer.prepareSignature(state, PbjConverter.asBytes(bytes), signatureMap, context.payer());
        cryptography.verifyAsync(payerSignature);
        return payerSignature;
    }

    @NonNull
    private Map<HederaKey, TransactionSignature> verifyOtherSignatures(
            @NonNull final HederaState state,
            @NonNull final PreHandleContext context,
            @NonNull final Bytes txBodyBytes,
            @NonNull final SignatureMap signatureMap) {
        final var otherSignatures = signaturePreparer.prepareSignatures(
                state, PbjConverter.asBytes(txBodyBytes), signatureMap, context.requiredNonPayerKeys());
        cryptography.verifyAsync(new ArrayList<>(otherSignatures.values()));
        return otherSignatures;
    }

    @NonNull
    private static PreHandleResult createResult(
            @NonNull final PreHandleContext context,
            @NonNull final SignatureMap signatureMap,
            @Nullable final TransactionSignature payerSignature,
            @NonNull final Map<HederaKey, TransactionSignature> otherSignatures,
            @Nullable final PreHandleResult innerResult) {
        final var otherSigs = otherSignatures.values();
        final var allSigs = new ArrayList<TransactionSignature>(otherSigs.size() + 1);
        if (payerSignature != null) {
            allSigs.add(payerSignature);
        }
        allSigs.addAll(otherSigs);
        return new PreHandleResult(context, OK, signatureMap, allSigs, innerResult);
    }

    @NonNull
    private static PreHandleResult createInvalidResult(@NonNull final ResponseCodeEnum responseCode) {
        return new PreHandleResult(responseCode);
    }
}
