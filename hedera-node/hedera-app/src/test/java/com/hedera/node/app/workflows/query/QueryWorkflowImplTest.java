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

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.FileGetInfoQuery;
import com.hedera.hapi.node.file.FileGetInfoResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandler;
import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.stats.HapiOpCounters;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.ingest.SubmissionManager;
import com.hedera.pbj.runtime.io.DataBuffer;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class QueryWorkflowImplTest extends AppTestBase {

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
        final var queryHeader = QueryHeader.newBuilder().payment(payment).build();
        query = Query.newBuilder()
                .fileGetInfo(FileGetInfoQuery.newBuilder().header(queryHeader))
                .build();
        ctx = new SessionContext();

        payer = AccountID.newBuilder().accountNum(42L).build();
        final var transactionID = TransactionID.newBuilder().accountID(payer).build();
        txBody = TransactionBody.newBuilder().transactionID(transactionID).build();
        when(checker.validateCryptoTransfer(ctx, payment)).thenReturn(txBody);

        when(handler.extractHeader(query)).thenReturn(queryHeader);
        when(handler.createEmptyResponse(any())).thenAnswer((Answer<Response>) invocation -> {
            final var header = (ResponseHeader) invocation.getArguments()[0];
            return Response.newBuilder()
                    .fileGetInfo(FileGetInfoResponse.newBuilder().header(header).build())
                    .build();
        });

        final var responseHeader = ResponseHeader.newBuilder()
                .responseType(ANSWER_ONLY)
                .nodeTransactionPrecheckCode(OK)
                .build();
        final var fileGetInfo =
                FileGetInfoResponse.newBuilder().header(responseHeader).build();
        final var response = Response.newBuilder().fileGetInfo(fileGetInfo).build();

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
                metrics,
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
                        metrics,
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
                         metrics,
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
                        metrics,
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
                        metrics,
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
                        metrics,
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
                        metrics,
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
                        metrics,
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
                        metrics,
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
                        metrics,
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
        assertThat(header.getCost()).isZero();
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters).countAnswered(FileGetInfo);
    }

    @Test
    void testSuccessIfPaymentRequired() throws InvalidProtocolBufferException, PreCheckException {
        given(feeAccumulator.computePayment(any(), any(), any(), any())).willReturn(new FeeObject(100L, 0L, 100L));
        given(handler.requiresNodePayment(any())).willReturn(true);
        given(dispatcher.validate(any(), any())).willReturn(OK);
        given(dispatcher.getResponse(any(), any(), any(), any()))
                .willReturn(Response.newBuilder().build());
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
        assertThat(header.getCost()).isZero();
        verify(opCounters).countReceived(FileGetInfo);
        verify(opCounters).countAnswered(FileGetInfo);
    }

    @Test
    void testSuccessIfAnswerOnlyCostRequired() throws InvalidProtocolBufferException, PreCheckException {
        given(feeAccumulator.computePayment(any(), any(), any(), any())).willReturn(new FeeObject(100L, 0L, 100L));
        given(handler.needsAnswerOnlyCost(any())).willReturn(true);
        given(dispatcher.validate(any(), any())).willReturn(OK);
        given(dispatcher.getResponse(any(), any(), any(), any()))
                .willReturn(Response.newBuilder().build());
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
        assertThat(header.getCost()).isEqualTo(200);
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
}
