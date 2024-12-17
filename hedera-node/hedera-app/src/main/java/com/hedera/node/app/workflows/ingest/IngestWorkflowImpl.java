/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Implementation of {@link IngestWorkflow} */
public final class IngestWorkflowImpl implements IngestWorkflow {

    private static final Logger logger = LogManager.getLogger(IngestWorkflowImpl.class);

    private final Supplier<AutoCloseableWrapper<State>> stateAccessor;
    private final TransactionChecker transactionChecker;
    private final IngestChecker ingestChecker;
    private final SubmissionManager submissionManager;
    private final ConfigProvider configProvider;

    /**
     * Constructor of {@code IngestWorkflowImpl}
     *
     * @param stateAccessor a {@link Supplier} that provides the latest immutable state
     * @param transactionChecker the {@link TransactionChecker} that pre-processes the bytes of a transaction
     * @param ingestChecker the {@link IngestChecker} with specific checks of an ingest-workflow
     * @param submissionManager the {@link SubmissionManager} to submit transactions to the platform
     * @param configProvider the {@link ConfigProvider} to provide the configuration
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public IngestWorkflowImpl(
            @NonNull final Supplier<AutoCloseableWrapper<State>> stateAccessor,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final IngestChecker ingestChecker,
            @NonNull final SubmissionManager submissionManager,
            @NonNull final ConfigProvider configProvider) {
        this.stateAccessor = requireNonNull(stateAccessor);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.ingestChecker = requireNonNull(ingestChecker);
        this.submissionManager = requireNonNull(submissionManager);
        this.configProvider = requireNonNull(configProvider);
    }

    @Override
    public void submitTransaction(@NonNull final Bytes requestBuffer, @NonNull final BufferedData responseBuffer) {
        requireNonNull(requestBuffer);
        requireNonNull(responseBuffer);

        ResponseCodeEnum result = ResponseCodeEnum.OK;
        long estimatedFee = 0L;

        // Grab (and reference count) the state, so we have a consistent view of things
        try (final var wrappedState = stateAccessor.get()) {
            // 0. Node state pre-checks
            ingestChecker.checkNodeState();

            // 1.-6. Parse and check the transaction
            final var tx = transactionChecker.parse(requestBuffer);
            final var state = wrappedState.get();
            final var configuration = configProvider.getConfiguration();
            final var transactionInfo = ingestChecker.runAllChecks(state, tx, configuration);

            // 7. Submit to platform
            submissionManager.submit(transactionInfo.txBody(), requestBuffer);
        } catch (final InsufficientBalanceException e) {
            estimatedFee = e.getEstimatedFee();
            result = e.responseCode();
        } catch (final PreCheckException e) {
            result = e.responseCode();
        } catch (final HandleException e) {
            // Conceptually, this should never happen, because we should use PreCheckException only during pre-checks
            // But we catch it here to play it safe
            result = e.getStatus();
        } catch (final Exception e) {
            logger.error("Possibly CATASTROPHIC failure while running the ingest workflow", e);
            result = ResponseCodeEnum.FAIL_INVALID;
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
