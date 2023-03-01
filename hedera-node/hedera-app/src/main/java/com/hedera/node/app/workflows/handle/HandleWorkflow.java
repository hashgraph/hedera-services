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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.clock.SystemClock;
import com.hedera.node.app.meta.InvalidTransactionMetadata;
import com.hedera.node.app.meta.ValidTransactionMetadata;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.onset.OnsetResult;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;

public class HandleWorkflow {

    private final PreHandleWorkflow preHandleWorkflow;
    private final SystemClock systemClock;
    private final SignaturePreparer signaturePreparer;
    private final Cryptography cryptography;
    private final HandleChecker checker;
    private final TransactionDispatcher dispatcher;

    /**
     * Constructor of {@code HandleWorkflow}
     *
     * @param systemClock the {@link SystemClock} that keeps track of consensus time
     * @param signaturePreparer
     * @param cryptography
     * @param checker the {@link HandleChecker} that contains checks related to the handle workflow
     * @param dispatcher the {@link TransactionDispatcher} that dispatches calls to the right handler
     */
    public HandleWorkflow(
            @NonNull final SystemClock systemClock,
            SignaturePreparer signaturePreparer, Cryptography cryptography, @NonNull final HandleChecker checker,
            @NonNull final TransactionDispatcher dispatcher) {
        this.systemClock = requireNonNull(systemClock);
        this.signaturePreparer = requireNonNull(signaturePreparer);
        this.cryptography = requireNonNull(cryptography);
        this.checker = requireNonNull(checker);
        this.dispatcher = requireNonNull(dispatcher);
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

    private void handleTransaction(@NonNull final HederaState state, @NonNull final ConsensusTransaction txn) {
        // Advance system clock
        final Instant consensusTimestamp = txn.getConsensusTimestamp();
        systemClock.advance(consensusTimestamp);

        final var rawMetadata = txn.getMetadata();
        txn.setMetadata(null);

        // In some special situations, it is possible that pre-handle was not executed at all
        final List<TransactionSignature> signatures;
        if (rawMetadata == null) {
            preHandleWorkflow.securePreHandle(state, txn);
            signatures =
            final TransactionMetadata metadata = txn.getMetadata();
            txn.setMetadata(null);
            if (metadata.failed()) {
                // TODO: Need to handle cases when we had a fatal error during pre-handle
                throw new UnsupportedOperationException("Fatal error during pre-handle, which is not supported yet");
            }

        } else if (rawMetadata instanceof InvalidTransactionMetadata) {
            // TODO: Need to handle cases when we had a fatal error during pre-handle
            throw new UnsupportedOperationException("Fatal error during pre-handle, which is not supported yet");

        } else {
            // At this point, we know that pre-handle was executed and we have valid metadata
            final var metadata = (ValidTransactionMetadata) rawMetadata;

            // Rerun pre-handle to determine keys
            final var storeFactory = new ReadableStoreFactory(state);
            final var accountStore = storeFactory.createAccountStore();
            final var context = new PreHandleContext(accountStore, metadata.txnBody());
            dispatcher.dispatchPreHandle(storeFactory, context);

            // if the keys changed, we need to revalidate signatures
            final var signatures = checker.determineSignatures(state, metadata, context);
        }


            final var innerSignatures = metadata.innerMetadata() != null
                    ? checker.determineSignatures(state, metadata.innerMetadata(), context)
                    : List.of();


            final var otherSignatures = metadata.otherSignatures().entrySet().stream()
                    .map(entry -> {
                        final var sig = metadata.nonPayerSignature(key);
                        return sig != null && Objects.equals(key, sig.getKey())
                                ? sig
                                : signaturePreparer.prepareSignature(state, metadata.txnBodyBytes(), metadata.signatureMap(), key);
                    })


            // TODO: What do we need to do, if the verification is ongoing?
            try {
                payerSignature.getFuture().get();
            } catch (InterruptedException | ExecutionException e) {
                // TODO: Handle this case
                throw new RuntimeException(e);
            }

            if (metadata.payerSignature() == null || !Objects.equals(context.getPayerKey(), metadata.payerKey()) || metadata.payerSignature().getSignatureStatus() != VerificationStatus.VALID) {
                // Would it be better to wait a little until
                checkPayerSignature(metadata, context);
            }
            if (!Objects.equals(context.getPayerKey(), metadata.payerKey()) || metadata.payerSignature() == null || wait here -> metadata.payerSignature().getSignatureStatus() == VerificationStatus.INVALID) {
                verifyPayerSignature(metadata, context);
            }
            signaturesInvalid = signaturesInvalid
                    || !Objects.equals(context.getPayerKey(), metadata.payerKey())
                    || !Objects.equals(context.getRequiredNonPayerKeys(), metadata.requiredNonPayerKeys());
        }




            // use the fresh metadata
            metadata = new TransactionMetadata(context, List.of());

            if (metadata.failed()) {
                // TODO: If pre-handle failed (again), we just need to writeRecords
                return;
            }

            // Eventually we need to revalidate signatures
            if (signaturesInvalid || checker.areSignaturesOutdated(metadata.payerKey(), metadata.requiredNonPayerKeys())) {
                if (! checker.validateSignatures(metadata.payerKey(), metadata.requiredNonPayerKeys())) {
                    // TODO: If signature validation failed again, we just need to writeRecords
                    return;
                }
            }

            // Dispatch the transaction to specific handlers
            final var storeFactory = new WritableStoreFactory(state);
            final var handlerContext = new HandleContext(metadata.txnBody(), metadata.handlerMetadata());
            final var result = dispatcher.dispatchHandle(storeFactory, handlerContext);

            if (result == HandleResult.FAIL_INVALID) {
                // TODO: Handle case when transaction "failed invalid"
                return;
            }

            // Commit the state when transaction was successful
            if (result == HandleResult.SUCCESS) {
                commitToState(storeFactory.getUsedStates());
            }

            // Write success on success and normal fails
            writeRecords(storeFactory.getUsedStates());
        }

    }

}
