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

package com.hedera.node.app.workflows.ingest;

import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.utility.AutoCloseableWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Inject;

/** Implementation of {@link IngestWorkflow} */
public final class IngestWorkflowImpl implements IngestWorkflow {
    private final NodeInfo nodeInfo;
    private final CurrentPlatformStatus currentPlatformStatus;
    private final Supplier<AutoCloseableWrapper<HederaState>> stateAccessor;
    private final TransactionChecker transactionChecker;
    private final IngestChecker ingestChecker;
    private final ThrottleAccumulator throttleAccumulator;
    private final SubmissionManager submissionManager;

    /** A map of counter metrics for each type of transaction we can ingest */
    private final Map<HederaFunctionality, Counter> counters = new EnumMap<>(HederaFunctionality.class);

    /**
     * Constructor of {@code IngestWorkflowImpl}
     *
     * @param nodeInfo the {@link NodeInfo} of the current node
     * @param currentPlatformStatus the {@link CurrentPlatformStatus}
     * @param stateAccessor a {@link Supplier} that provides the latest immutable state
     * @param transactionChecker the {@link TransactionChecker} that pre-processes the bytes of a transaction
     * @param ingestChecker the {@link IngestChecker} with specific checks of an ingest-workflow
     * @param throttleAccumulator the {@link ThrottleAccumulator} for throttling
     * @param submissionManager the {@link SubmissionManager} to submit transactions to the platform
     * @param metrics the {@link Metrics} to use for tracking metrics
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public IngestWorkflowImpl(
            @NonNull final NodeInfo nodeInfo,
            @NonNull final CurrentPlatformStatus currentPlatformStatus,
            @NonNull final Supplier<AutoCloseableWrapper<HederaState>> stateAccessor,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final IngestChecker ingestChecker,
            @NonNull final ThrottleAccumulator throttleAccumulator,
            @NonNull final SubmissionManager submissionManager,
            @NonNull final Metrics metrics) {
        this.nodeInfo = requireNonNull(nodeInfo);
        this.currentPlatformStatus = requireNonNull(currentPlatformStatus);
        this.stateAccessor = requireNonNull(stateAccessor);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.ingestChecker = requireNonNull(ingestChecker);
        this.throttleAccumulator = requireNonNull(throttleAccumulator);
        this.submissionManager = requireNonNull(submissionManager);

        // Create metrics for tracking submission of transactions by type
        for (var function : HederaFunctionality.values()) {
            final var name = function.name() + "Sub";
            final var desc = "The number of transactions submitted for consensus for " + function.name();
            counters.put(function, metrics.getOrCreate(new Counter.Config("app", name).withDescription(desc)));
        }
    }

    @Override
    public void submitTransaction(
            @NonNull final Bytes requestBuffer,
            @NonNull final BufferedData responseBuffer) {
        submitTransaction(requestBuffer, responseBuffer, ReadableAccountStore::new);
    }

    // Package-private for testing
    void submitTransaction(
            @NonNull final Bytes requestBuffer,
            @NonNull final BufferedData responseBuffer,
            @NonNull final Function<ReadableStates, ReadableAccountStore> storeSupplier) {
        requireNonNull(requestBuffer);
        requireNonNull(responseBuffer);
        requireNonNull(storeSupplier);

        ResponseCodeEnum result = ResponseCodeEnum.OK;
        long estimatedFee = 0L;

        // 0. Node state pre-checks
        if (nodeInfo.isSelfZeroStake()) {
            result = ResponseCodeEnum.INVALID_NODE_ACCOUNT;
        } else if (currentPlatformStatus.get() != ACTIVE) {
            result = ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
        }

        if (result == ResponseCodeEnum.OK) {
            // Grab (and reference count) the state, so we have a consistent view of things
            try (final var wrappedState = stateAccessor.get()) {
                final var state = wrappedState.get();

                // 1. Parse the TransactionBody and check the syntax
                final var onsetResult = transactionChecker.parseAndCheck(requestBuffer);
                final var txBody = onsetResult.txBody();
                final var signatureMap = onsetResult.signatureMap();
                final var functionality = onsetResult.functionality();

                // This should never happen, because HapiUtils#checkFunctionality() will throw
                // UnknownHederaFunctionality if it cannot map to a proper value, and WorkflowOnset
                // will convert that to INVALID_TRANSACTION_BODY.
                assert functionality != HederaFunctionality.NONE;

                // 2. Check throttles
                if (throttleAccumulator.shouldThrottle(onsetResult.txBody())) {
                    throw new PreCheckException(ResponseCodeEnum.BUSY);
                }

                // 3. Check semantics
                ingestChecker.checkTransactionSemantics(txBody, functionality);

                // 4. Get payer account
                final AccountID payerID =
                        txBody.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT);

                // 5. Check payer's signature
                ingestChecker.checkPayerSignature(state, onsetResult.transaction(), signatureMap, payerID);

                // 6. Check account balance
                ingestChecker.checkSolvency(onsetResult.transaction());

                // 7. Submit to platform
                submissionManager.submit(txBody, PbjConverter.asBytes(requestBuffer));
                counters.get(functionality).increment();
            } catch (final InsufficientBalanceException e) {
                estimatedFee = e.getEstimatedFee();
                result = e.responseCode();
            } catch (final PreCheckException e) {
                result = e.responseCode();
            }
        }

        // 8. Return PreCheck code and estimated fee
        final var transactionResponse = TransactionResponse.newBuilder()
                .nodeTransactionPrecheckCode(result)
                .cost(estimatedFee)
                .build();

        try {
            TransactionResponse.PROTOBUF.write(transactionResponse, responseBuffer);
        } catch (IOException ex) {
            // It may be that the response couldn't be written because the response buffer was
            // too small, which would be an internal server error.
            throw new UncheckedIOException("Failed to write bytes to response buffer", ex);
        }
    }
}
