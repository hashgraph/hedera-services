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
package com.hedera.node.app.workflows.ingest;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.SessionContext;
import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.stats.HapiOpCounters;
import com.hedera.node.app.service.mono.token.impl.AccountStore;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.StoreCache;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.common.utility.AutoCloseableWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/** Default implementation of {@link IngestWorkflow} */
public final class IngestWorkflowImpl implements IngestWorkflow {

    private final NodeInfo nodeInfo;
    private final CurrentPlatformStatus currentPlatformStatus;
    private final Supplier<AutoCloseableWrapper<HederaState>> stateAccessor;
    private final StoreCache storeCache;
    private final WorkflowOnset onset;
    private final IngestChecker checker;
    private final ThrottleAccumulator throttleAccumulator;
    private final SubmissionManager submissionManager;
    private final HapiOpCounters opCounters;

    /**
     * Constructor of {@code IngestWorkflowImpl}
     *
     * @param nodeInfo the {@link NodeInfo} of the current node
     * @param currentPlatformStatus the {@link CurrentPlatformStatus}
     * @param stateAccessor a {@link Supplier} that provides the latest immutable state
     * @param storeCache the {@link StoreCache} that caches stores for all active states
     * @param onset the {@link WorkflowOnset} that pre-processes the {@link ByteBuffer} of a
     *     transaction
     * @param checker the {@link IngestWorkflow} with specific checks of an ingest-workflow
     * @param throttleAccumulator the {@link ThrottleAccumulator} for throttling
     * @param submissionManager the {@link SubmissionManager} to submit transactions to the platform
     * @param opCounters the {@link HapiOpCounters} with workflow-specific metrics
     */
    public IngestWorkflowImpl(
            @NonNull final NodeInfo nodeInfo,
            @NonNull final CurrentPlatformStatus currentPlatformStatus,
            @NonNull final Supplier<AutoCloseableWrapper<HederaState>> stateAccessor,
            @NonNull final StoreCache storeCache,
            @NonNull final WorkflowOnset onset,
            @NonNull final IngestChecker checker,
            @NonNull final ThrottleAccumulator throttleAccumulator,
            @NonNull final SubmissionManager submissionManager,
            @NonNull final HapiOpCounters opCounters) {
        this.nodeInfo = requireNonNull(nodeInfo);
        this.currentPlatformStatus = requireNonNull(currentPlatformStatus);
        this.stateAccessor = requireNonNull(stateAccessor);
        this.storeCache = requireNonNull(storeCache);
        this.onset = requireNonNull(onset);
        this.checker = requireNonNull(checker);
        this.throttleAccumulator = requireNonNull(throttleAccumulator);
        this.submissionManager = requireNonNull(submissionManager);
        this.opCounters = requireNonNull(opCounters);
    }

    @Override
    public void submitTransaction(
            @NonNull final SessionContext ctx,
            @NonNull final ByteBuffer requestBuffer,
            @NonNull final ByteBuffer responseBuffer) {

        ResponseCodeEnum result = OK;
        long estimatedFee = 0L;

        // Do some general pre-checks
        if (nodeInfo.isSelfZeroStake()) {
            result = INVALID_NODE_ACCOUNT;
        } else if (currentPlatformStatus.get() != ACTIVE) {
            result = PLATFORM_NOT_ACTIVE;
        }

        if (result == OK) {
            try (final var wrappedState = stateAccessor.get()) {
                final var state = wrappedState.get();

                // 1. Parse the TransactionBody and check the syntax
                final var onsetResult = onset.parseAndCheck(ctx, requestBuffer);
                if (onsetResult.errorCode() != OK) {
                    throw new PreCheckException(onsetResult.errorCode());
                }

                final var txBody = onsetResult.txBody();
                final var signatureMap = onsetResult.signatureMap();
                final var functionality = onsetResult.functionality();

                opCounters.countReceived(functionality);

                // 2. Check throttles
                if (throttleAccumulator.shouldThrottle(functionality)) {
                    throw new PreCheckException(BUSY);
                }

                // 3. Check semantics
                checker.checkTransactionSemantics(txBody, functionality);

                // 4. Get payer account
                final AccountID payerID = txBody.getTransactionID().getAccountID();
                final AccountStore accountStore = storeCache.getAccountStore(state);
                final var payer =
                        accountStore
                                .getAccount(payerID)
                                .orElseThrow(() -> new PreCheckException(PAYER_ACCOUNT_NOT_FOUND));

                // 5. Check payer's signature
                checker.checkPayerSignature(txBody, signatureMap, payer);

                // 6. Check account balance
                checker.checkSolvency(txBody, functionality, payer);

                // 7. Submit to platform
                submissionManager.submit(txBody, requestBuffer, ctx.txBodyParser());

                opCounters.countSubmitted(functionality);
            } catch (InsufficientBalanceException e) {
                estimatedFee = e.getEstimatedFee();
                result = e.responseCode();
            } catch (PreCheckException e) {
                result = e.responseCode();
            }
        }

        // 8. Return PreCheck code and evtl. estimated fee
        final var transactionResponse =
                TransactionResponse.newBuilder()
                        .setNodeTransactionPrecheckCode(result)
                        .setCost(estimatedFee)
                        .build();
        responseBuffer.put(transactionResponse.toByteArray());
    }
}
