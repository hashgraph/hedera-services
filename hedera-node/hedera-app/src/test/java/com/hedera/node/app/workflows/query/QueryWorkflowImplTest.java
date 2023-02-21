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
import static com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
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
        when(dispatcher.getResponse(any(), eq(query), eq(responseHeader))).thenReturn(response);

        workflow = new QueryWorkflowImpl(
                nodeInfo,
                currentPlatformStatus,
                stateAccessor,
                throttleAccumulator,
                submissionManager,
                checker,
                dispatcher,
                metrics);
    }

    //    @SuppressWarnings("ConstantConditions")
    //    @Test
    //    void testConstructorWithIllegalParameters() {
    //        assertThatThrownBy(
    //                        () ->
    //                                new QueryWorkflowImpl(
    //                                        null,
    //                                        currentPlatformStatus,
    //                                        stateAccessor,
    //                                        throttleAccumulator,
    //                                        submissionManager,
    //                                        checker,
    //                                        dispatcher,
    //                                        opCounters))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(
    //                        () ->
    //                                new QueryWorkflowImpl(
    //                                        nodeInfo,
    //                                        null,
    //                                        stateAccessor,
    //                                        throttleAccumulator,
    //                                        submissionManager,
    //                                        checker,
    //                                        dispatcher,
    //                                        opCounters))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(
    //                        () ->
    //                                new QueryWorkflowImpl(
    //                                        nodeInfo,
    //                                        currentPlatformStatus,
    //                                        null,
    //                                        throttleAccumulator,
    //                                        submissionManager,
    //                                        checker,
    //                                        dispatcher,
    //                                        opCounters))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(
    //                        () ->
    //                                new QueryWorkflowImpl(
    //                                        nodeInfo,
    //                                        currentPlatformStatus,
    //                                        stateAccessor,
    //                                        null,
    //                                        submissionManager,
    //                                        checker,
    //                                        dispatcher,
    //                                        opCounters))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(
    //                        () ->
    //                                new QueryWorkflowImpl(
    //                                        nodeInfo,
    //                                        currentPlatformStatus,
    //                                        stateAccessor,
    //                                        throttleAccumulator,
    //                                        null,
    //                                        checker,
    //                                        dispatcher,
    //                                        opCounters))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(
    //                        () ->
    //                                new QueryWorkflowImpl(
    //                                        nodeInfo,
    //                                        currentPlatformStatus,
    //                                        stateAccessor,
    //                                        throttleAccumulator,
    //                                        submissionManager,
    //                                        null,
    //                                        dispatcher,
    //                                        opCounters))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(
    //                        () ->
    //                                new QueryWorkflowImpl(
    //                                        nodeInfo,
    //                                        currentPlatformStatus,
    //                                        stateAccessor,
    //                                        throttleAccumulator,
    //                                        submissionManager,
    //                                        checker,
    //                                        null,
    //                                        opCounters))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(
    //                        () ->
    //                                new QueryWorkflowImpl(
    //                                        nodeInfo,
    //                                        currentPlatformStatus,
    //                                        stateAccessor,
    //                                        throttleAccumulator,
    //                                        submissionManager,
    //                                        checker,
    //                                        dispatcher,
    //                                        null))
    //                .isInstanceOf(NullPointerException.class);
    //    }
    //
    //    @SuppressWarnings("ConstantConditions")
    //    @Test
    //    void testHandleQueryWithIllegalParameters() {
    //        // given
    //        final var requestBuffer = ByteBuffer.wrap(new byte[] {1, 2, 3});
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //
    //        // then
    //        assertThatThrownBy(() -> workflow.handleQuery(null, requestBuffer, responseBuffer))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(() -> workflow.handleQuery(ctx, null, responseBuffer))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(() -> workflow.handleQuery(ctx, requestBuffer, null))
    //                .isInstanceOf(NullPointerException.class);
    //    }
    //
    //    @Test
    //    void testSuccess() throws InvalidProtocolBufferException {
    //        // given
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //
    //        // when
    //        workflow.handleQuery(ctx, requestBuffer, responseBuffer);
    //
    //        // then
    //        final var response = parseResponse(responseBuffer);
    //        assertThat(response.fileGetInfo()).isNotNull();
    //        final var header = response.fileGetInfo().orElseThrow().header();
    //        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(OK);
    //        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
    //        // TODO: Expected costs need to be updated once fee calculation was integrated
    //        assertThat(header.cost()).isZero();
    //        verify(opCounters).countReceived(FileGetInfo);
    //        verify(opCounters).countAnswered(FileGetInfo);
    //    }
    //
    //    @Test
    //    void testParsingFails(@Mock Parser<Query> localQueryParser)
    //            throws InvalidProtocolBufferException {
    //        // given
    //        when(localQueryParser.parseFrom(requestBuffer))
    //                .thenThrow(new InvalidProtocolBufferException("Expected failure"));
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //
    //        // then
    //        assertThatThrownBy(() -> workflow.handleQuery(ctx, requestBuffer, responseBuffer))
    //                .isInstanceOf(StatusRuntimeException.class)
    //                .hasFieldOrPropertyWithValue("status", Status.INVALID_ARGUMENT);
    //        verify(opCounters, never()).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }
    //
    //    @Test
    //    void testUnrecognizableQueryTypeFails(@Mock Parser<Query> localQueryParser)
    //            throws InvalidProtocolBufferException {
    //        // given
    //        final var query = Query.newBuilder().build();
    //        when(localQueryParser.parseFrom(requestBuffer)).thenReturn(query);
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //
    //        // then
    //        assertThatThrownBy(() -> workflow.handleQuery(ctx, requestBuffer, responseBuffer))
    //                .isInstanceOf(StatusRuntimeException.class)
    //                .hasFieldOrPropertyWithValue("status", Status.INVALID_ARGUMENT);
    //        verify(opCounters, never()).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }
    //
    //    @SuppressWarnings("JUnitMalformedDeclaration")
    //    @Test
    //    void testMissingHeaderFails(
    //            @Mock QueryHandler localHandler, @Mock QueryDispatcher localDispatcher) {
    //        // given
    //        when(localDispatcher.getHandler(query)).thenReturn(localHandler);
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //        workflow =
    //                new QueryWorkflowImpl(
    //                        nodeInfo,
    //                        currentPlatformStatus,
    //                        stateAccessor,
    //                        throttleAccumulator,
    //                        submissionManager,
    //                        checker,
    //                        localDispatcher,
    //                        opCounters);
    //
    //        // then
    //        assertThatThrownBy(() -> workflow.handleQuery(ctx, requestBuffer, responseBuffer))
    //                .isInstanceOf(StatusRuntimeException.class)
    //                .hasFieldOrPropertyWithValue("status", Status.INVALID_ARGUMENT);
    //        verify(opCounters).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }
    //
    //    @Test
    //    void testInactiveNodeFails(@Mock NodeInfo localNodeInfo) throws InvalidProtocolBufferException {
    //        // given
    //        when(localNodeInfo.isSelfZeroStake()).thenReturn(true);
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //        workflow =
    //                new QueryWorkflowImpl(
    //                        localNodeInfo,
    //                        currentPlatformStatus,
    //                        stateAccessor,
    //                        throttleAccumulator,
    //                        submissionManager,
    //                        checker,
    //                        dispatcher,
    //                        opCounters);
    //
    //        // when
    //        workflow.handleQuery(ctx, requestBuffer, responseBuffer);
    //
    //        // then
    //        final var response = parseResponse(responseBuffer);
    //        assertThat(response.fileGetInfo()).isNotNull();
    //        final var header = response.fileGetInfo().orElseThrow().header();
    //        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(INVALID_NODE_ACCOUNT);
    //        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
    //        assertThat(header.cost()).isZero();
    //        verify(opCounters).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }
    //
    //    @Test
    //    void testInactivePlatformFails(@Mock CurrentPlatformStatus localCurrentPlatformStatus)
    //            throws InvalidProtocolBufferException {
    //        // given
    //        when(localCurrentPlatformStatus.get()).thenReturn(PlatformStatus.MAINTENANCE);
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //        workflow =
    //                new QueryWorkflowImpl(
    //                        nodeInfo,
    //                        localCurrentPlatformStatus,
    //                        stateAccessor,
    //                        throttleAccumulator,
    //                        submissionManager,
    //                        checker,
    //                        dispatcher,
    //                        opCounters);
    //
    //        // when
    //        workflow.handleQuery(ctx, requestBuffer, responseBuffer);
    //
    //        // then
    //        final var response = parseResponse(responseBuffer);
    //        assertThat(response.fileGetInfo()).isNotNull();
    //        final var header = response.fileGetInfo().orElseThrow().header();
    //        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(PLATFORM_NOT_ACTIVE);
    //        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
    //        assertThat(header.cost()).isZero();
    //        verify(opCounters).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }
    //
    //    @Test
    //    void testUnsupportedResponseTypeFails() throws InvalidProtocolBufferException {
    //        // given
    //        final var localRequestBuffer = ByteBuffer.wrap(new byte[] {4, 5, 6});
    //        final var queryHeader =
    //                QueryHeader.newBuilder().responseType(ANSWER_STATE_PROOF).build();
    //        final var query =
    //                Query.newBuilder()
    //                        .fileGetInfo(
    //                                FileGetInfoQuery.newBuilder().header(queryHeader).build())
    //                        .build();
    //        when(handler.extractHeader(query)).thenReturn(queryHeader);
    //        when(dispatcher.getHandler(query)).thenReturn(handler);
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //
    //        // when
    //        workflow.handleQuery(ctx, localRequestBuffer, responseBuffer);
    //
    //        // then
    //        final var response = parseResponse(responseBuffer);
    //        assertThat(response.fileGetInfo()).isNotNull();
    //        final var header = response.fileGetInfo().orElseThrow().header();
    //        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
    //        assertThat(header.responseType()).isEqualTo(ANSWER_STATE_PROOF);
    //        assertThat(header.cost()).isZero();
    //        verify(opCounters).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }
    //
    //    @Test
    //    void testThrottleFails() throws InvalidProtocolBufferException {
    //        // given
    //        when(throttleAccumulator.shouldThrottle(FileGetInfo)).thenReturn(true);
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //
    //        // when
    //        workflow.handleQuery(ctx, requestBuffer, responseBuffer);
    //
    //        // then
    //        final var response = parseResponse(responseBuffer);
    //        assertThat(response.fileGetInfo()).isNotNull();
    //        final var header = response.fileGetInfo().orElseThrow().header();
    //        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(BUSY);
    //        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
    //        assertThat(header.cost()).isZero();
    //        verify(opCounters).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }
    //
    //    @Test
    //    void testPaidQueryWithInvalidCryptoTransferFails()
    //            throws PreCheckException, InvalidProtocolBufferException {
    //        // given
    //        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
    //        when(checker.validateCryptoTransfer(ctx, payment))
    //                .thenThrow(new PreCheckException(INSUFFICIENT_TX_FEE));
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //
    //        // when
    //        workflow.handleQuery(ctx, requestBuffer, responseBuffer);
    //
    //        // then
    //        final var response = parseResponse(responseBuffer);
    //        assertThat(response.fileGetInfo()).isNotNull();
    //        final var header = response.fileGetInfo().orElseThrow().header();
    //        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(INSUFFICIENT_TX_FEE);
    //        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
    //        assertThat(header.cost()).isZero();
    //        verify(opCounters).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }
    //
    //    @Test
    //    void testPaidQueryWithInvalidAccountsFails(@Mock QueryChecker localChecker)
    //            throws PreCheckException, InvalidProtocolBufferException {
    //        // given
    //        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
    //        when(localChecker.validateCryptoTransfer(ctx, payment))
    //                .thenThrow(new PreCheckException(INSUFFICIENT_TX_FEE));
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //        workflow =
    //                new QueryWorkflowImpl(
    //                        nodeInfo,
    //                        currentPlatformStatus,
    //                        stateAccessor,
    //                        throttleAccumulator,
    //                        submissionManager,
    //                        localChecker,
    //                        dispatcher,
    //                        opCounters);
    //
    //        // when
    //        workflow.handleQuery(ctx, requestBuffer, responseBuffer);
    //
    //        // then
    //        final var response = parseResponse(responseBuffer);
    //        assertThat(response.fileGetInfo()).isNotNull();
    //        final var header = response.fileGetInfo().orElseThrow().header();
    //        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(INSUFFICIENT_TX_FEE);
    //        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
    //        assertThat(header.cost()).isZero();
    //        verify(opCounters).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }
    //
    //    @Test
    //    void testPaidQueryWithInsufficientPermissionFails()
    //            throws PreCheckException, InvalidProtocolBufferException {
    //        // given
    //        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
    //        doThrow(new PreCheckException(NOT_SUPPORTED))
    //                .when(checker)
    //                .checkPermissions(payer, FileGetInfo);
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //
    //        // when
    //        workflow.handleQuery(ctx, requestBuffer, responseBuffer);
    //
    //        // then
    //        final var response = parseResponse(responseBuffer);
    //        assertThat(response.fileGetInfo()).isNotNull();
    //        final var header = response.fileGetInfo().orElseThrow().header();
    //        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
    //        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
    //        assertThat(header.cost()).isZero();
    //        verify(opCounters).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }
    //
    //    @Test
    //    void testUnpaidQueryWithRestrictedFunctionalityFails() throws InvalidProtocolBufferException {
    //        // given
    //        final var localRequestBuffer = ByteBuffer.wrap(new byte[] {4, 5, 6});
    //        final var queryHeader = QueryHeader.newBuilder().responseType(COST_ANSWER).build();
    //        final var query =
    //                Query.newBuilder()
    //                        .networkGetExecutionTime(
    //                                NetworkGetExecutionTimeQuery.newBuilder()
    //                                        .header(queryHeader)
    //                                        .build())
    //                        .build();
    //        when(handler.extractHeader(query)).thenReturn(queryHeader);
    //        when(dispatcher.getHandler(query)).thenReturn(handler);
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //
    //        // when
    //        workflow.handleQuery(ctx, localRequestBuffer, responseBuffer);
    //
    //        // then
    //        final var response = parseResponse(responseBuffer);
    //        assertThat(response.fileGetInfo()).isNotNull();
    //        final var header = response.fileGetInfo().orElseThrow().header();
    //        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
    //        assertThat(header.responseType()).isEqualTo(COST_ANSWER);
    //        assertThat(header.cost()).isZero();
    //        verify(opCounters).countReceived(NetworkGetExecutionTime);
    //        verify(opCounters, never()).countAnswered(NetworkGetExecutionTime);
    //    }
    //
    //    @Test
    //    void testQuerySpecificValidationFails()
    //            throws PreCheckException, InvalidProtocolBufferException {
    //        // given
    //        doThrow(new PreCheckException(ACCOUNT_FROZEN_FOR_TOKEN))
    //                .when(dispatcher)
    //                .validate(state, query);
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //
    //        // when
    //        workflow.handleQuery(ctx, requestBuffer, responseBuffer);
    //
    //        // then
    //        final var response = parseResponse(responseBuffer);
    //        assertThat(response.fileGetInfo()).isNotNull();
    //        final var header = response.fileGetInfo().orElseThrow().header();
    //        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(ACCOUNT_FROZEN_FOR_TOKEN);
    //        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
    //        assertThat(header.cost()).isZero();
    //        verify(opCounters).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }
    //
    //    @Test
    //    void testPaidQueryWithFailingSubmissionFails()
    //            throws PreCheckException, InvalidProtocolBufferException {
    //        // given
    //        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
    //        doThrow(new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED))
    //                .when(submissionManager)
    //                .submit(txBody, payment.toByteArray(), ctx.txBodyParser());
    //        final var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    //
    //        // when
    //        workflow.handleQuery(ctx, requestBuffer, responseBuffer);
    //
    //        // then
    //        final var response = parseResponse(responseBuffer);
    //        assertThat(response.fileGetInfo()).isNotNull();
    //        final var header = response.fileGetInfo().orElseThrow().header();
    //        assertThat(header.nodeTransactionPrecheckCode())
    //                .isEqualTo(PLATFORM_TRANSACTION_NOT_CREATED);
    //        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
    //        assertThat(header.cost()).isZero();
    //        verify(opCounters).countReceived(FileGetInfo);
    //        verify(opCounters, never()).countAnswered(FileGetInfo);
    //    }

    private static Response parseResponse(ByteBuffer responseBuffer) throws IOException {
        final byte[] bytes = new byte[responseBuffer.position()];
        responseBuffer.get(0, bytes);
        return Response.PROTOBUF.parse(DataBuffer.wrap(bytes));
    }
}
