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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetAccountDetails;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NetworkGetExecutionTime;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER_STATE_PROOF;
import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.stats.HapiOpCounters;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.ingest.SubmissionManager;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.utility.AutoCloseableWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implementation of {@link QueryWorkflow} */
public final class QueryWorkflowImpl implements QueryWorkflow {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryWorkflowImpl.class);

    private static final EnumSet<ResponseType> UNSUPPORTED_RESPONSE_TYPES =
            EnumSet.of(ANSWER_STATE_PROOF, COST_ANSWER_STATE_PROOF);
    private static final List<HederaFunctionality> RESTRICTED_FUNCTIONALITIES =
            List.of(NetworkGetExecutionTime, GetAccountDetails);

    private final NodeInfo nodeInfo;
    private final CurrentPlatformStatus currentPlatformStatus;
    private final Function<ResponseType, AutoCloseableWrapper<HederaState>> stateAccessor;
    private final ThrottleAccumulator throttleAccumulator;
    private final SubmissionManager submissionManager;
    private final QueryChecker checker;
    private final QueryDispatcher dispatcher;
    private final HapiOpCounters opCounters;

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
     * @param opCounters the {@link HapiOpCounters} with workflow-specific metrics
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
            @NonNull final HapiOpCounters opCounters) {
        this.nodeInfo = requireNonNull(nodeInfo);
        this.currentPlatformStatus = requireNonNull(currentPlatformStatus);
        this.stateAccessor = requireNonNull(stateAccessor);
        this.throttleAccumulator = requireNonNull(throttleAccumulator);
        this.submissionManager = requireNonNull(submissionManager);
        this.checker = requireNonNull(checker);
        this.dispatcher = requireNonNull(dispatcher);
        this.opCounters = requireNonNull(opCounters);
    }

    @Override
    public void handleQuery(
            @NonNull final SessionContext session,
            @NonNull final ByteBuffer requestBuffer,
            @NonNull final ByteBuffer responseBuffer) {
        requireNonNull(session);
        requireNonNull(requestBuffer);
        requireNonNull(responseBuffer);

        // 1. Parse and check header
        final Query query;
        try {
            query = session.queryParser().parseFrom(requestBuffer);
        } catch (InvalidProtocolBufferException e) {
            throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Received query: {}", query);
        }

        final var functionality =
                MiscUtils.functionalityOfQuery(query)
                        .orElseThrow(() -> new StatusRuntimeException(Status.INVALID_ARGUMENT));
        opCounters.countReceived(functionality);

        final var handler = dispatcher.getHandler(query);
        final var queryHeader = handler.extractHeader(query);
        if (queryHeader == null) {
            throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
        }
        final ResponseType responseType = queryHeader.getResponseType();

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
            if (throttleAccumulator.shouldThrottleQuery(functionality, query)) {
                throw new PreCheckException(BUSY);
            }

            final var state = wrappedState.get();
            final var paymentRequired = handler.requiresNodePayment(responseType);
            Transaction allegedPayment = null;
            TransactionBody txBody = null;
            if (paymentRequired) {
                // 3.i Validate CryptoTransfer
                allegedPayment = queryHeader.getPayment();
                txBody = checker.validateCryptoTransfer(session, allegedPayment);
                final var payer = txBody.getTransactionID().getAccountID();

                // 3.ii Check permissions
                checker.checkPermissions(payer, functionality);

                // 3.iii Calculate costs
                // TODO: Integrate fee-engine (calculate fee) (#4207)
                fee = 0L;

                // 3.iv Check account balances
                checker.validateAccountBalances(payer, txBody, fee);
            } else {
                if (RESTRICTED_FUNCTIONALITIES.contains(functionality)) {
                    throw new PreCheckException(NOT_SUPPORTED);
                }
            }

            // 4. Check validity
            dispatcher.validate(state, query);

            // 5. Submit payment to platform
            if (paymentRequired) {
                submissionManager.submit(
                        txBody, allegedPayment.toByteArray(), session.txBodyParser());
            }

            if (handler.needsAnswerOnlyCost(responseType)) {
                // 6.i Estimate costs
                // TODO: Integrate fee-engine (estimate fee) (#4207)
                fee = 0L;
                final var header = createResponseHeader(responseType, OK, fee);
                response = handler.createEmptyResponse(header);
            } else {
                // 6.ii Find response
                final var header = createResponseHeader(responseType, OK, fee);
                response = dispatcher.getResponse(state, query, header);
            }

            opCounters.countAnswered(functionality);

        } catch (InsufficientBalanceException e) {
            final var header =
                    createResponseHeader(responseType, e.responseCode(), e.getEstimatedFee());
            response = handler.createEmptyResponse(header);
        } catch (PreCheckException e) {
            final var header = createResponseHeader(responseType, e.responseCode(), fee);
            response = handler.createEmptyResponse(header);
        }

        responseBuffer.put(response.toByteArray());
    }

    private static ResponseHeader createResponseHeader(
            @NonNull final ResponseType type,
            @NonNull final ResponseCodeEnum responseCode,
            final long fee) {
        return ResponseHeader.newBuilder()
                .setResponseType(type)
                .setNodeTransactionPrecheckCode(responseCode)
                .setCost(fee)
                .build();
    }
}
