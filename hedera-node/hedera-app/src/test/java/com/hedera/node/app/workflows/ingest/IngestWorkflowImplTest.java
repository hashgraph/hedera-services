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

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.stats.HapiOpCounters;
import com.hedera.node.app.service.token.entity.Account;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.StoreCache;
import com.hedera.node.app.workflows.onset.OnsetResult;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.DataBuffer;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestWorkflowImplTest extends AppTestBase {

    private static final TransactionBody TRANSACTION_BODY = TransactionBody.newBuilder().build();
    private static final SignatureMap SIGNATURE_MAP = SignatureMap.newBuilder().build();
    private static final OnsetResult ONSET_RESULT =
            new OnsetResult(TRANSACTION_BODY, OK, SIGNATURE_MAP, HederaFunctionality.CONSENSUS_CREATE_TOPIC);

    @Mock private NodeInfo nodeInfo;

    @Mock(strictness = LENIENT)
    private CurrentPlatformStatus currentPlatformStatus;

    @Mock(strictness = LENIENT)
    private Supplier<AutoCloseableWrapper<HederaState>> stateAccessor;

    @Mock(strictness = LENIENT)
    private StoreCache storeCache;

    @Mock(strictness = LENIENT)
    private WorkflowOnset onset;

    @Mock private IngestChecker checker;

    @Mock private ThrottleAccumulator throttleAccumulator;
    @Mock private SubmissionManager submissionManager;

    @Mock private ByteBuffer requestBuffer;
    
    private SessionContext ctx;
    private IngestWorkflowImpl workflow;

    @SuppressWarnings("JUnitMalformedDeclaration")
    @BeforeEach
    void setup(
            @Mock(strictness = LENIENT) HederaState state,
            @Mock(strictness = LENIENT) ReadableAccountStore accountStore,
            @Mock Account account)
            throws PreCheckException {
        when(currentPlatformStatus.get()).thenReturn(PlatformStatus.ACTIVE);
        when(stateAccessor.get()).thenReturn(new AutoCloseableWrapper<>(state, () -> {}));
        when(storeCache.getAccountStore(state)).thenReturn(accountStore);
        when(accountStore.getAccount(any())).thenReturn(Optional.of(account));
        when(onset.parseAndCheck(any(), any(ByteBuffer.class))).thenReturn(ONSET_RESULT);

        ctx = new SessionContext();
        workflow =
                new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        storeCache,
                        onset,
                        checker,
                        throttleAccumulator,
                        submissionManager,
                        metrics);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithInvalidArguments() {
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        null,
                                        currentPlatformStatus,
                                        stateAccessor,
                                        storeCache,
                                        onset,
                                        checker,
                                        throttleAccumulator,
                                        submissionManager,
                                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        nodeInfo,
                                        null,
                                        stateAccessor,
                                        storeCache,
                                        onset,
                                        checker,
                                        throttleAccumulator,
                                        submissionManager,
                                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        nodeInfo,
                                        currentPlatformStatus,
                                        null,
                                        storeCache,
                                        onset,
                                        checker,
                                        throttleAccumulator,
                                        submissionManager,
                                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        nodeInfo,
                                        currentPlatformStatus,
                                        stateAccessor,
                                        null,
                                        onset,
                                        checker,
                                        throttleAccumulator,
                                        submissionManager,
                                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        nodeInfo,
                                        currentPlatformStatus,
                                        stateAccessor,
                                        storeCache,
                                        null,
                                        checker,
                                        throttleAccumulator,
                                        submissionManager,
                                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        nodeInfo,
                                        currentPlatformStatus,
                                        stateAccessor,
                                        storeCache,
                                        onset,
                                        null,
                                        throttleAccumulator,
                                        submissionManager,
                                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        nodeInfo,
                                        currentPlatformStatus,
                                        stateAccessor,
                                        storeCache,
                                        onset,
                                        checker,
                                        null,
                                        submissionManager,
                                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        nodeInfo,
                                        currentPlatformStatus,
                                        stateAccessor,
                                        storeCache,
                                        onset,
                                        checker,
                                        throttleAccumulator,
                                        null,
                                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        nodeInfo,
                                        currentPlatformStatus,
                                        stateAccessor,
                                        storeCache,
                                        onset,
                                        checker,
                                        throttleAccumulator,
                                        submissionManager,
                                        null))
                .isInstanceOf(NullPointerException.class);
    }

//    @Test
//    void testSuccess() throws PreCheckException, IOException {
//        // given
//        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
//
//        // when
//        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);
//
//        // then
//        final TransactionResponse response = parseResponse(responseBuffer);
//        assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(OK);
//        assertThat(response.cost()).isZero();
//
//        verify(metrics).countReceived(ConsensusCreateTopic);
//        verify(submissionManager).submit(TRANSACTION_BODY, requestBuffer, any());
//        verify(counterMetric("txSubmitted")).get(ConsensusCreateTopic);
//    }

//    @Test
//    void testParseAndCheckWithZeroStakeFails()
//            throws IOException, PreCheckException {
//        // given
//        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
//        when(nodeInfo.isSelfZeroStake()).thenReturn(true);
//
//        // when
//        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);
//
//        // then
//        final TransactionResponse response = parseResponse(responseBuffer);
//        assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(INVALID_NODE_ACCOUNT);
//        assertThat(response.cost()).isZero();
//        verify(metrics, never()).countReceived(any());
//        verify(submissionManager, never()).submit(any(), any(), any());
//        verify(metrics, never()).countSubmitted(any());
//    }
//
//    @ParameterizedTest
//    @EnumSource(PlatformStatus.class)
//    void testParseAndCheckWithInactivePlatformFails(final PlatformStatus status)
//            throws IOException, PreCheckException {
//        if (status != PlatformStatus.ACTIVE) {
//            // given
//            final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
//            final var inactivePlatformStatus = mock(CurrentPlatformStatus.class);
//            when(inactivePlatformStatus.get()).thenReturn(status);
//            workflow =
//                    new IngestWorkflowImpl(
//                            nodeInfo,
//                            inactivePlatformStatus,
//                            stateAccessor,
//                            storeCache,
//                            onset,
//                            checker,
//                            throttleAccumulator,
//                            submissionManager,
//                            metrics);
//
//            // when
//            workflow.submitTransaction(ctx, requestBuffer, responseBuffer);
//
//            // then
//            final TransactionResponse response = parseResponse(responseBuffer);
//            assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(PLATFORM_NOT_ACTIVE);
//            assertThat(response.cost()).isZero();
//            verify(metrics, never()).countReceived(any());
//            verify(submissionManager, never()).submit(any(), any(), any());
//            verify(metrics, never()).countSubmitted(any());
//        }
//    }
//
//    @Test
//    void testOnsetFails(@Mock WorkflowOnset localOnset)
//            throws PreCheckException, IOException {
//        // given
//        when(localOnset.parseAndCheck(any(), any(ByteBuffer.class)))
//                .thenThrow(new PreCheckException(INVALID_TRANSACTION));
//        workflow =
//                new IngestWorkflowImpl(
//                        nodeInfo,
//                        currentPlatformStatus,
//                        stateAccessor,
//                        storeCache,
//                        localOnset,
//                        checker,
//                        throttleAccumulator,
//                        submissionManager,
//                        metrics);
//        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
//
//        // when
//        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);
//
//        // then
//        final TransactionResponse response = parseResponse(responseBuffer);
//        assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(INVALID_TRANSACTION);
//        assertThat(response.cost()).isZero();
//        verify(metrics, never()).countReceived(any());
//        verify(submissionManager, never()).submit(any(), any(), any());
//        verify(metrics, never()).countSubmitted(any());
//    }
//
//    @Test
//    void testThrottleFails() throws PreCheckException, IOException {
//        // given
//        when(throttleAccumulator.shouldThrottle(ConsensusCreateTopic)).thenReturn(true);
//        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
//
//        // when
//        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);
//
//        // then
//        final TransactionResponse response = parseResponse(responseBuffer);
//        assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(BUSY);
//        assertThat(response.cost()).isZero();
//        verify(metrics).countReceived(ConsensusCreateTopic);
//        verify(submissionManager, never()).submit(any(), any(), any());
//        verify(metrics, never()).countSubmitted(any());
//    }
//
//    @SuppressWarnings("JUnitMalformedDeclaration")
//    @Test
//    void testPayerAccountNotFoundFails(
//            @Mock StoreCache localStoreCache, @Mock ReadableAccountStore localAccountStore)
//            throws PreCheckException, IOException {
//        // given
//        when(localStoreCache.getAccountStore(any())).thenReturn(localAccountStore);
//        when(localAccountStore.getAccount(any())).thenReturn(Optional.empty());
//        workflow =
//                new IngestWorkflowImpl(
//                        nodeInfo,
//                        currentPlatformStatus,
//                        stateAccessor,
//                        localStoreCache,
//                        onset,
//                        checker,
//                        throttleAccumulator,
//                        submissionManager,
//                        metrics);
//        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
//
//        // when
//        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);
//
//        // then
//        final TransactionResponse response = parseResponse(responseBuffer);
//        assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(PAYER_ACCOUNT_NOT_FOUND);
//        assertThat(response.cost()).isZero();
//        verify(metrics).countReceived(ConsensusCreateTopic);
//        verify(submissionManager, never()).submit(TRANSACTION_BODY, requestBuffer, txBodyParser);
//        verify(metrics, never()).countSubmitted(ConsensusCreateTopic);
//    }
//
//    @Test
//    void testPayerSignatureFails() throws PreCheckException, IOException {
//        // given
//        doThrow(new PreCheckException(INVALID_PAYER_SIGNATURE))
//                .when(checker)
//                .checkPayerSignature(eq(TRANSACTION_BODY), eq(SIGNATURE_MAP), any());
//        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
//
//        // when
//        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);
//
//        // then
//        final TransactionResponse response = parseResponse(responseBuffer);
//        assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(INVALID_PAYER_SIGNATURE);
//        assertThat(response.cost()).isZero();
//        verify(metrics).countReceived(ConsensusCreateTopic);
//        verify(submissionManager, never()).submit(any(), any(), any());
//        verify(metrics, never()).countSubmitted(any());
//    }
//
//    @Test
//    void testSolvencyFails() throws PreCheckException, IOException {
//        // given
//        doThrow(new InsufficientBalanceException(INSUFFICIENT_ACCOUNT_BALANCE, 42L))
//                .when(checker)
//                .checkSolvency(eq(TRANSACTION_BODY), eq(ConsensusCreateTopic), any());
//        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
//
//        // when
//        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);
//
//        // then
//        final TransactionResponse response = parseResponse(responseBuffer);
//        assertThat(response.nodeTransactionPrecheckCode())
//                .isEqualTo(INSUFFICIENT_ACCOUNT_BALANCE);
//        assertThat(response.cost()).isEqualTo(42L);
//        verify(metrics).countReceived(ConsensusCreateTopic);
//        verify(submissionManager, never()).submit(any(), any(), any());
//        verify(metrics, never()).countSubmitted(any());
//    }
//
//    @Test
//    void testSubmitFails() throws PreCheckException, IOException {
//        // given
//        doThrow(new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED))
//                .when(submissionManager)
//                .submit(eq(TRANSACTION_BODY), eq(requestBuffer), any());
//        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
//
//        // when
//        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);
//
//        // then
//        final TransactionResponse response = parseResponse(responseBuffer);
//        assertThat(response.nodeTransactionPrecheckCode())
//                .isEqualTo(PLATFORM_TRANSACTION_NOT_CREATED);
//        assertThat(response.cost()).isZero();
//        verify(metrics).countReceived(ConsensusCreateTopic);
//        verify(metrics, never()).countSubmitted(any());
//    }

    private static TransactionResponse parseResponse(ByteBuffer responseBuffer)
            throws IOException {
        final byte[] bytes = new byte[responseBuffer.position()];
        responseBuffer.get(0, bytes);
        return TransactionResponse.PROTOBUF.parse(DataBuffer.wrap(responseBuffer));
    }
}
