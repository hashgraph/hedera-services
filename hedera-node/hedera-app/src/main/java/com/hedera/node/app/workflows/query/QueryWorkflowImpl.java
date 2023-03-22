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

package com.hedera.node.app.workflows.query;

import static com.hedera.hapi.node.base.HederaFunctionality.GET_ACCOUNT_DETAILS;
import static com.hedera.hapi.node.base.HederaFunctionality.NETWORK_GET_EXECUTION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BUSY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_STATE_PROOF;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER_STATE_PROOF;
import static com.hedera.node.app.spi.HapiUtils.asTimestamp;
import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.meta.QueryContext;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.ingest.SubmissionManager;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.utility.AutoCloseableWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Implementation of {@link QueryWorkflow} */
public final class QueryWorkflowImpl implements QueryWorkflow {

    private static final Logger LOGGER = LogManager.getLogger(QueryWorkflowImpl.class);

    private static final EnumSet<ResponseType> UNSUPPORTED_RESPONSE_TYPES =
            EnumSet.of(ANSWER_STATE_PROOF, COST_ANSWER_STATE_PROOF);
    private static final List<HederaFunctionality> RESTRICTED_FUNCTIONALITIES =
            List.of(NETWORK_GET_EXECUTION_TIME, GET_ACCOUNT_DETAILS);

    private final NodeInfo nodeInfo;
    private final CurrentPlatformStatus currentPlatformStatus;
    private final Function<ResponseType, AutoCloseableWrapper<HederaState>> stateAccessor;
    private final ThrottleAccumulator throttleAccumulator;
    private final SubmissionManager submissionManager;
    private final QueryChecker checker;
    private final QueryDispatcher dispatcher;

    /** A map of counter metrics for each type of query received */
    private final Map<HederaFunctionality, Counter> received = new EnumMap<>(HederaFunctionality.class);
    /** A map of counter metrics for each type of query answered */
    private final Map<HederaFunctionality, Counter> answered = new EnumMap<>(HederaFunctionality.class);

    private final FeeAccumulator feeAccumulator;
    private final QueryContext queryContext;

    /**
     * Constructor of {@code QueryWorkflowImpl}
     *
     * @param nodeInfo the {@link NodeInfo} of the current node
     * @param currentPlatformStatus the {@link CurrentPlatformStatus}
     * @param stateAccessor a {@link Function} that returns the latest immutable or latest signed
     *     state depending on the {@link ResponseType}
     * @param throttleAccumulator the {@link ThrottleAccumulator} for throttling
     * @param submissionManager the {@link SubmissionManager} to submit transactions to the platform
     * @param checker the {@link QueryChecker} with specific checks of an ingest-workflow
     * @param dispatcher the {@link QueryDispatcher} that will call query-specific methods
     * @param metrics the {@link Metrics} with workflow-specific metrics
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public QueryWorkflowImpl(
            @NonNull final NodeInfo nodeInfo,
            @NonNull final CurrentPlatformStatus currentPlatformStatus,
            @NonNull final Function<ResponseType, AutoCloseableWrapper<HederaState>> stateAccessor,
            @NonNull final ThrottleAccumulator throttleAccumulator,
            @NonNull final SubmissionManager submissionManager,
            @NonNull final QueryChecker checker,
            @NonNull final QueryDispatcher dispatcher,
            @NonNull final Metrics metrics,
            @NonNull final FeeAccumulator feeAccumulator,
            @NonNull final QueryContextImpl queryContext) {
        this.nodeInfo = requireNonNull(nodeInfo);
        this.currentPlatformStatus = requireNonNull(currentPlatformStatus);
        this.stateAccessor = requireNonNull(stateAccessor);
        this.throttleAccumulator = requireNonNull(throttleAccumulator);
        this.submissionManager = requireNonNull(submissionManager);
        this.checker = requireNonNull(checker);
        this.dispatcher = requireNonNull(dispatcher);
        this.feeAccumulator = requireNonNull(feeAccumulator);
        this.queryContext = requireNonNull(queryContext);

        // Create metrics for tracking each query received and answered per query type
        for (var function : HederaFunctionality.values()) {
            var name = function.name() + "Received";
            var desc = "The number of queries received for " + function.name();
            received.put(function, metrics.getOrCreate(new Counter.Config("app", name).withDescription(desc)));

            name = function.name() + "Answered";
            desc = "The number of queries answered for " + function.name();
            answered.put(function, metrics.getOrCreate(new Counter.Config("app", name).withDescription(desc)));
        }
    }

    @Override
    public void handleQuery(
            @NonNull final SessionContext session,
            @NonNull final Bytes requestBuffer,
            @NonNull final BufferedData responseBuffer) {
        requireNonNull(session);
        requireNonNull(requestBuffer);
        requireNonNull(responseBuffer);

        // 1. Parse and check header
        final Query query;
        try {
            query = Query.PROTOBUF.parse(requestBuffer.toReadableSequentialData());
        } catch (IOException e) {
            // TODO there may be other types of errors here. Please cross check with ingest parsing
            throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
        }

        final var functionality = functionOf(query);
        received.get(functionality).increment();

        final var handler = dispatcher.getHandler(query);
        final var queryHeader = handler.extractHeader(query);
        if (queryHeader == null) {
            throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
        }
        final ResponseType responseType = queryHeader.responseType();
        LOGGER.info("Started answering a {} query of type {}", functionality, responseType);
        LOGGER.info("Started answering a {} query of type {}", function, responseType);

        Response response;
        long fee = 0L;
        try (final var wrappedState = stateAccessor.apply(responseType)) {
            // Do some general pre-checks
            if (nodeInfo.isSelfZeroStake()) {
                // Zero stake nodes are currently not supported
                throw new PreCheckException(INVALID_NODE_ACCOUNT);
            }
            if (currentPlatformStatus.get() != ACTIVE) {
                throw new PreCheckException(PLATFORM_NOT_ACTIVE);
            }
            if (UNSUPPORTED_RESPONSE_TYPES.contains(responseType)) {
                throw new PreCheckException(NOT_SUPPORTED);
            }

            // 2. Check query throttles
            final var function = functionOf(query);
            if (throttleAccumulator.shouldThrottleQuery(function, query)) {
                throw new PreCheckException(BUSY);
            }

            final var state = wrappedState.get();
            final var storeFactory = new ReadableStoreFactory(state);
            final var paymentRequired = handler.requiresNodePayment(responseType);
            Transaction allegedPayment = null;
            TransactionBody txBody = null;
            if (paymentRequired) {
                // 3.i Validate CryptoTransfer
                allegedPayment = queryHeader.payment();
                txBody = checker.validateCryptoTransfer(session, allegedPayment);
                final var payer = txBody.transactionID().accountID();

                // 3.ii Check permissions
                checker.checkPermissions(payer, function);

                // 3.iii Calculate costs
                final var feeData =
                        feeAccumulator.computePayment(storeFactory, function, query, asTimestamp(Instant.now()));
                fee = totalFee(feeData);

                // 3.iv Check account balances
                checker.validateAccountBalances(payer, txBody, fee);
            } else {
                if (RESTRICTED_FUNCTIONALITIES.contains(function)) {
                    throw new PreCheckException(NOT_SUPPORTED);
                }
            }

            // 4. Check validity
            final var validity = dispatcher.validate(storeFactory, query);

            // 5. Submit payment to platform
            if (paymentRequired) {
                final var out = new ByteArrayOutputStream();
                try {
                    Transaction.PROTOBUF.write(allegedPayment, new ByteArrayDataOutput(out));
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new StatusRuntimeException(Status.INTERNAL);
                }
                submissionManager.submit(txBody, out.toByteArray());
            }

            if (handler.needsAnswerOnlyCost(responseType)) {
                // 6.i Estimate costs
                final var feeData =
                        feeAccumulator.computePayment(storeFactory, function, query, asTimestamp(Instant.now()));
                fee = totalFee(feeData);

                final var header = createResponseHeader(responseType, validity, fee);
                response = handler.createEmptyResponse(header);
            } else {
                // 6.ii Find response
                final var header = createResponseHeader(responseType, validity, fee);
                response = dispatcher.getResponse(storeFactory, query, header, queryContext);
            }

            answered.get(functionality).increment();
        } catch (InsufficientBalanceException e) {
            final var header = createResponseHeader(responseType, e.responseCode(), e.getEstimatedFee());
            response = handler.createEmptyResponse(header);
        } catch (PreCheckException e) {
            final var header = createResponseHeader(responseType, e.responseCode(), fee);
            response = handler.createEmptyResponse(header);
        }

        try {
            Response.PROTOBUF.write(response, responseBuffer);
            LOGGER.debug("Finished answering a {} query of type {}", function, responseType);
        } catch (IOException e) {
            e.printStackTrace();
            throw new StatusRuntimeException(Status.INTERNAL);
        }
    }

    private long totalFee(final FeeObject costs) {
        return costs.getNetworkFee() + costs.getServiceFee() + costs.getNodeFee();
    }

    private static ResponseHeader createResponseHeader(
            @NonNull final ResponseType type, @NonNull final ResponseCodeEnum responseCode, final long fee) {
        return ResponseHeader.newBuilder()
                .responseType(type)
                .nodeTransactionPrecheckCode(responseCode)
                .cost(fee)
                .build();
    }

    private static HederaFunctionality functionOf(@NonNull final Query query) {
        try {
            return HapiUtils.functionOf(query);
        } catch (UnknownHederaFunctionality e) {
            throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
        }
    }

    final class ByteArrayDataOutput extends WritableStreamingData {
        private final ByteArrayOutputStream out;

        public ByteArrayDataOutput(ByteArrayOutputStream out) {
            super(out);
            this.out = out;
        }

        public Bytes getBytes() {
            return Bytes.wrap(out.toByteArray());
        }
    }
}
