/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NetworkGetExecutionTime;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandler;
import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.stats.HapiOpCounters;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.ingest.SubmissionManager;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileGetInfoQuery;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.NetworkGetExecutionTimeQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.utility.AutoCloseableWrapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.nio.ByteBuffer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class QueryWorkflowImplTest {

    private static final int BUFFER_SIZE = 1024 * 6;

    @Mock
    private HederaState state;

    @Mock
    private NodeInfo nodeInfo;

    @Mock(strictness = LENIENT)
    private CurrentPlatformStatus currentPlatformStatus;

    @Mock(strictness = LENIENT)
    private Function<ResponseType, AutoCloseableWrapper<HederaState>> stateAccessor;

    @Mock
    private ThrottleAccumulator throttleAccumulator;

    @Mock
    private SubmissionManager submissionManager;

    @Mock(strictness = LENIENT)
    private QueryChecker checker;

    @Mock(strictness = LENIENT)
    FileGetInfoHandler handler;

    @Mock(strictness = LENIENT)
    private QueryDispatcher dispatcher;

    @Mock
    private HapiOpCounters opCounters;

    @Mock
    private FeeAccumulator feeAccumulator;

    @Mock
    private QueryContextImpl queryContext;

    @Mock(strictness = LENIENT)
    private Parser<Query> queryParser;

    @Mock
    private Parser<Transaction> txParser;

    @Mock
    private Parser<SignedTransaction> signedParser;

    @Mock
    private Parser<TransactionBody> txBodyParser;

    private Query query;
    private Transaction payment;
    private TransactionBody txBody;
    private AccountID payer;
    private SessionContext ctx;
    private ByteBuffer requestBuffer;

    private QueryWorkflowImpl workflow;

    @BeforeEach
    void setup() throws InvalidProtocolBufferException, PreCheckException {
        when(currentPlatformStatus.get()).thenReturn(PlatformStatus.ACTIVE);
        when(stateAccessor.apply(any())).thenReturn(new AutoCloseableWrapper<>(state, () -> {}));
        requestBuffer = ByteBuffer.wrap(new byte[] {1, 2, 3});
        payment = Transaction.newBuilder().build();
        final var queryHeader = QueryHeader.newBuilder().setPayment(payment).build();
        query = Query.newBuilder()
                .setFileGetInfo(FileGetInfoQuery.newBuilder().setHeader(queryHeader))
                .build();
        when(queryParser.parseFrom(requestBuffer)).thenReturn(query);
        ctx = new SessionContext(queryParser, txParser, signedParser, txBodyParser);

        payer = AccountID.newBuilder().setAccountNum(42L).build();
        final var transactionID = TransactionID.newBuilder().setAccountID(payer).build();
        txBody = TransactionBody.newBuilder().setTransactionID(transactionID).build();
        when(checker.validateCryptoTransfer(ctx, payment)).thenReturn(txBody);

        when(handler.extractHeader(query)).thenReturn(queryHeader);
        when(handler.createEmptyResponse(any())).thenAnswer((Answer<Response>) invocation -> {
            final var header = (ResponseHeader) invocation.getArguments()[0];
            return Response.newBuilder()
                    .setFileGetInfo(
                            FileGetInfoResponse.newBuilder().setHeader(header).build())
                    .build();
        });

        final var responseHeader = ResponseHeader.newBuilder()
                .setResponseType(ANSWER_ONLY)
                .setNodeTransactionPrecheckCode(OK)
                .build();
        final var fileGetInfo =
                FileGetInfoResponse.newBuilder().setHeader(responseHeader).build();
        final var response = Response.newBuilder().setFileGetInfo(fileGetInfo).build();

        when(dispatcher.getHandler(query)).thenReturn(handler);
        when(dispatcher.getResponse(any(), eq(query), eq(responseHeader), eq(queryContext)))
                .thenReturn(response);

        workflow = new QueryWorkflowImpl(
                nodeInfo,
                currentPlatformStatus,
                stateAccessor,
                throttleAccumulator,
                submissionManager,
                checker,
                dispatcher,
                opCounters,
                feeAccumulator,
                queryContext);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        null,
                        currentPlatformStatus,
                        stateAccessor,
                        throttleAccumulator,
                        submissionManager,
                        checker,
                        dispatcher,
                        opCounters,
                        feeAccumulator,
                        queryContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        nodeInfo,
                        null,
                        stateAccessor,
                        throttleAccumulator,
                        submissionManager,
                        checker,
                        dispatcher,
                        opCounters,
                        feeAccumulator,
                        queryContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        null,
                        throttleAccumulator,
                        submissionManager,
                        checker,
                        dispatcher,
                        opCounters,
                        feeAccumulator,
                        queryContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        null,
                        submissionManager,
                        checker,
                        dispatcher,
                        opCounters,
                        feeAccumulator,
                        queryContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        throttleAccumulator,
                        null,
                        checker,
                        dispatcher,
                        opCounters,
                        feeAccumulator,
                        queryContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        throttleAccumulator,
                        submissionManager,
                        null,
                        dispatcher,
                        opCounters,
                        feeAccumulator,
                        queryContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        throttleAccumulator,
                        submissionManager,
                        checker,
                        null,
                        opCounters,
                        feeAccumulator,
                        queryContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        throttleAccumulator,
                        submissionManager,
                        checker,
                        dispatcher,
                        null,
                        feeAccumulator,
                        queryContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        throttleAccumulator,
                        submissionManager,
                        checker,
                        dispatcher,
                        opCounters,
                        null,
                        queryContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        throttleAccumulator,
                        submissionManager,
                        checker,
                        dispatcher,
                        opCounters,
                        feeAccumulator,
                        null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testHandleQueryWithIllegalParameters() {
        // given
        final var requestBuffer = ByteBuffer.wrap(new byte[] {1, 2, 3});
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        // then
        assertThatThrownBy(() -> workflow.handleQuery(null, requestBuffer, responseBuffer))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> workflow.handleQuery(ctx, null, responseBuffer))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> workflow.handleQuery(ctx, requestBuffer, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSuccessIfPaymentNotRequired() throws InvalidProtocolBufferException, PreCheckException {
        given(dispatcher.validate(any(), any())).willReturn(OK);
        // given
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        // when
        workflow.handleQuery(ctx, requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(OK);
        assertThat(header.getResponseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.getCost()).isEqualTo(0L);
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters).countAnswered(FileGetInfo);
    }

    @Test
    void testSuccessIfPaymentRequired() throws InvalidProtocolBufferException, PreCheckException {
        given(feeAccumulator.computePayment(any(), any(), any())).willReturn(new FeeObject(100L, 0L, 100L));
        given(handler.requiresNodePayment(any())).willReturn(true);
        given(dispatcher.validate(any(), any())).willReturn(OK);
        // given
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        // when
        workflow.handleQuery(ctx, requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(OK);
        assertThat(header.getResponseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.getCost()).isEqualTo(0L);
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters).countAnswered(FileGetInfo);
    }

    @Test
    void testParsingFails(@Mock Parser<Query> localQueryParser) throws InvalidProtocolBufferException {
        // given
        when(localQueryParser.parseFrom(requestBuffer))
                .thenThrow(new InvalidProtocolBufferException("Expected failure"));
        final SessionContext localContext = new SessionContext(localQueryParser, txParser, signedParser, txBodyParser);
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        // then
        assertThatThrownBy(() -> workflow.handleQuery(localContext, requestBuffer, responseBuffer))
                .isInstanceOf(StatusRuntimeException.class)
                .hasFieldOrPropertyWithValue("status", Status.INVALID_ARGUMENT);
        verify(opCounters, never()).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    @Test
    void testUnrecognizableQueryTypeFails(@Mock Parser<Query> localQueryParser) throws InvalidProtocolBufferException {
        // given
        final var query = Query.newBuilder().build();
        when(localQueryParser.parseFrom(requestBuffer)).thenReturn(query);
        final SessionContext localContext = new SessionContext(localQueryParser, txParser, signedParser, txBodyParser);
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        // then
        assertThatThrownBy(() -> workflow.handleQuery(localContext, requestBuffer, responseBuffer))
                .isInstanceOf(StatusRuntimeException.class)
                .hasFieldOrPropertyWithValue("status", Status.INVALID_ARGUMENT);
        verify(opCounters, never()).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    @SuppressWarnings("JUnitMalformedDeclaration")
    @Test
    void testMissingHeaderFails(@Mock QueryHandler localHandler, @Mock QueryDispatcher localDispatcher) {
        // given
        when(localDispatcher.getHandler(query)).thenReturn(localHandler);
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        workflow = new QueryWorkflowImpl(
                nodeInfo,
                currentPlatformStatus,
                stateAccessor,
                throttleAccumulator,
                submissionManager,
                checker,
                localDispatcher,
                opCounters,
                feeAccumulator,
                queryContext);

        // then
        assertThatThrownBy(() -> workflow.handleQuery(ctx, requestBuffer, responseBuffer))
                .isInstanceOf(StatusRuntimeException.class)
                .hasFieldOrPropertyWithValue("status", Status.INVALID_ARGUMENT);
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    @Test
    void testInactiveNodeFails(@Mock NodeInfo localNodeInfo) throws InvalidProtocolBufferException {
        // given
        when(localNodeInfo.isSelfZeroStake()).thenReturn(true);
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        workflow = new QueryWorkflowImpl(
                localNodeInfo,
                currentPlatformStatus,
                stateAccessor,
                throttleAccumulator,
                submissionManager,
                checker,
                dispatcher,
                opCounters,
                feeAccumulator,
                queryContext);

        // when
        workflow.handleQuery(ctx, requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(INVALID_NODE_ACCOUNT);
        assertThat(header.getResponseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.getCost()).isZero();
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    @Test
    void testInactivePlatformFails(@Mock CurrentPlatformStatus localCurrentPlatformStatus)
            throws InvalidProtocolBufferException {
        // given
        when(localCurrentPlatformStatus.get()).thenReturn(PlatformStatus.MAINTENANCE);
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        workflow = new QueryWorkflowImpl(
                nodeInfo,
                localCurrentPlatformStatus,
                stateAccessor,
                throttleAccumulator,
                submissionManager,
                checker,
                dispatcher,
                opCounters,
                feeAccumulator,
                queryContext);

        // when
        workflow.handleQuery(ctx, requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(PLATFORM_NOT_ACTIVE);
        assertThat(header.getResponseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.getCost()).isZero();
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    @Test
    void testUnsupportedResponseTypeFails() throws InvalidProtocolBufferException {
        // given
        final var localRequestBuffer = ByteBuffer.wrap(new byte[] {4, 5, 6});
        final var queryHeader =
                QueryHeader.newBuilder().setResponseType(ANSWER_STATE_PROOF).build();
        final var query = Query.newBuilder()
                .setFileGetInfo(
                        FileGetInfoQuery.newBuilder().setHeader(queryHeader).build())
                .build();
        when(queryParser.parseFrom(localRequestBuffer)).thenReturn(query);
        when(handler.extractHeader(query)).thenReturn(queryHeader);
        when(dispatcher.getHandler(query)).thenReturn(handler);
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        // when
        workflow.handleQuery(ctx, localRequestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
        assertThat(header.getResponseType()).isEqualTo(ANSWER_STATE_PROOF);
        assertThat(header.getCost()).isZero();
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    @Test
    void testThrottleFails() throws InvalidProtocolBufferException {
        // given
        when(throttleAccumulator.shouldThrottleQuery(eq(FileGetInfo), any())).thenReturn(true);
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        // when
        workflow.handleQuery(ctx, requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(BUSY);
        assertThat(header.getResponseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.getCost()).isZero();
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    @Test
    void testPaidQueryWithInvalidCryptoTransferFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
        when(checker.validateCryptoTransfer(ctx, payment)).thenThrow(new PreCheckException(INSUFFICIENT_TX_FEE));
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        // when
        workflow.handleQuery(ctx, requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(INSUFFICIENT_TX_FEE);
        assertThat(header.getResponseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.getCost()).isZero();
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    @Test
    void testPaidQueryWithInvalidAccountsFails(@Mock QueryChecker localChecker)
            throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
        when(localChecker.validateCryptoTransfer(ctx, payment)).thenThrow(new PreCheckException(INSUFFICIENT_TX_FEE));
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        workflow = new QueryWorkflowImpl(
                nodeInfo,
                currentPlatformStatus,
                stateAccessor,
                throttleAccumulator,
                submissionManager,
                localChecker,
                dispatcher,
                opCounters,
                feeAccumulator,
                queryContext);

        // when
        workflow.handleQuery(ctx, requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(INSUFFICIENT_TX_FEE);
        assertThat(header.getResponseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.getCost()).isZero();
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    @Test
    void testPaidQueryWithInsufficientPermissionFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
        doThrow(new PreCheckException(NOT_SUPPORTED)).when(checker).checkPermissions(payer, FileGetInfo);
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        // when
        workflow.handleQuery(ctx, requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
        assertThat(header.getResponseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.getCost()).isZero();
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    @Test
    void testUnpaidQueryWithRestrictedFunctionalityFails() throws InvalidProtocolBufferException {
        // given
        final var localRequestBuffer = ByteBuffer.wrap(new byte[] {4, 5, 6});
        final var queryHeader =
                QueryHeader.newBuilder().setResponseType(COST_ANSWER).build();
        final var query = Query.newBuilder()
                .setNetworkGetExecutionTime(NetworkGetExecutionTimeQuery.newBuilder()
                        .setHeader(queryHeader)
                        .build())
                .build();
        when(queryParser.parseFrom(localRequestBuffer)).thenReturn(query);
        when(handler.extractHeader(query)).thenReturn(queryHeader);
        when(dispatcher.getHandler(query)).thenReturn(handler);
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        // when
        workflow.handleQuery(ctx, localRequestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
        assertThat(header.getResponseType()).isEqualTo(COST_ANSWER);
        assertThat(header.getCost()).isZero();
        verify(opCounters).countReceived(NetworkGetExecutionTime);
        verify(opCounters, never()).countAnswered(NetworkGetExecutionTime);
    }

    @Test
    void testQuerySpecificValidationFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        doThrow(new PreCheckException(ACCOUNT_FROZEN_FOR_TOKEN))
                .when(dispatcher)
                .validate(any(), eq(query));
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        // when
        workflow.handleQuery(ctx, requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(ACCOUNT_FROZEN_FOR_TOKEN);
        assertThat(header.getResponseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.getCost()).isZero();
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    @Test
    void testPaidQueryWithFailingSubmissionFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
        doThrow(new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED))
                .when(submissionManager)
                .submit(txBody, payment.toByteArray(), ctx.txBodyParser());
        given(feeAccumulator.computePayment(any(), any(), any())).willReturn(new FeeObject(100L, 0L, 100L));
        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        // when
        workflow.handleQuery(ctx, requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        assertThat(response.getFileGetInfo()).isNotNull();
        final var header = response.getFileGetInfo().getHeader();
        assertThat(header.getNodeTransactionPrecheckCode()).isEqualTo(PLATFORM_TRANSACTION_NOT_CREATED);
        assertThat(header.getResponseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.getCost()).isEqualTo(200L);
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters, never()).countAnswered(FileGetInfo);
    }

    private static Response parseResponse(ByteBuffer responseBuffer) throws InvalidProtocolBufferException {
        final byte[] bytes = new byte[responseBuffer.position()];
        responseBuffer.get(0, bytes);
        return Response.parseFrom(bytes);
    }
}
