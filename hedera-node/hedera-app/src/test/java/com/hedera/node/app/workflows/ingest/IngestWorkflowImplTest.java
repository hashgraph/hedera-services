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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
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
import com.hedera.node.app.workflows.onset.OnsetResult;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.utility.AutoCloseableWrapper;
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
class IngestWorkflowImplTest {

    private static final AccountID ACCOUNT_ID =
            AccountID.newBuilder().setAccountNum(42L).build();
    private static final TransactionID TRANSACTION_ID =
            TransactionID.newBuilder().setAccountID(ACCOUNT_ID).build();
    private static final TransactionBody TRANSACTION_BODY =
            TransactionBody.newBuilder().setTransactionID(TRANSACTION_ID).build();
    private static final SignatureMap SIGNATURE_MAP = SignatureMap.newBuilder().build();
    private static final OnsetResult ONSET_RESULT =
            new OnsetResult(TRANSACTION_BODY, TRANSACTION_BODY.toByteArray(), OK, SIGNATURE_MAP, ConsensusCreateTopic);

    @Mock
    private NodeInfo nodeInfo;

    @Mock(strictness = LENIENT)
    private CurrentPlatformStatus currentPlatformStatus;

    @Mock(strictness = LENIENT)
    private Supplier<AutoCloseableWrapper<HederaState>> stateAccessor;

    @Mock(strictness = LENIENT)
    private WorkflowOnset onset;

    @Mock(strictness = LENIENT)
    ReadableAccountStore accountStore;

    @Mock
    Account account;

    @Mock
    private IngestChecker checker;

    @Mock
    private ThrottleAccumulator throttleAccumulator;

    @Mock
    private SubmissionManager submissionManager;

    @Mock
    private HapiOpCounters opCounters;

    @Mock
    private Parser<Query> queryParser;

    @Mock
    private Parser<Transaction> txParser;

    @Mock
    private Parser<SignedTransaction> signedParser;

    @Mock
    private Parser<TransactionBody> txBodyParser;

    private SessionContext ctx;
    private IngestWorkflowImpl workflow;
    private ByteBuffer requestBuffer;

    @BeforeEach
    void setup(@Mock(strictness = LENIENT) HederaState state) throws PreCheckException {
        when(currentPlatformStatus.get()).thenReturn(PlatformStatus.ACTIVE);
        when(stateAccessor.get()).thenReturn(new AutoCloseableWrapper<>(state, () -> {}));
        when(accountStore.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));

        requestBuffer = ByteBuffer.wrap(new byte[] {1, 2, 3});
        ctx = new SessionContext(queryParser, txParser, signedParser, txBodyParser);
        when(onset.parseAndCheck(ctx, requestBuffer)).thenReturn(ONSET_RESULT);

        workflow = new IngestWorkflowImpl(
                nodeInfo,
                currentPlatformStatus,
                stateAccessor,
                onset,
                checker,
                throttleAccumulator,
                submissionManager,
                opCounters);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithInvalidArguments() {
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        null,
                        currentPlatformStatus,
                        stateAccessor,
                        onset,
                        checker,
                        throttleAccumulator,
                        submissionManager,
                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        null,
                        stateAccessor,
                        onset,
                        checker,
                        throttleAccumulator,
                        submissionManager,
                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        null,
                        onset,
                        checker,
                        throttleAccumulator,
                        submissionManager,
                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        null,
                        checker,
                        throttleAccumulator,
                        submissionManager,
                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        onset,
                        null,
                        throttleAccumulator,
                        submissionManager,
                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        onset,
                        checker,
                        null,
                        submissionManager,
                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        onset,
                        checker,
                        throttleAccumulator,
                        null,
                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        onset,
                        checker,
                        throttleAccumulator,
                        submissionManager,
                        null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSuccess() throws PreCheckException, InvalidProtocolBufferException {
        // given
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer, states -> accountStore);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(OK);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager).submit(TRANSACTION_BODY, requestBuffer.array(), txBodyParser);
        verify(opCounters).countSubmitted(ConsensusCreateTopic);
    }

    @Test
    void testSuccessWithNonDirectByteBuffer(@Mock ByteBuffer localRequestBuffer)
            throws PreCheckException, InvalidProtocolBufferException {
        // given
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
        when(onset.parseAndCheck(ctx, localRequestBuffer)).thenReturn(ONSET_RESULT);

        // when
        workflow.submitTransaction(ctx, localRequestBuffer, responseBuffer, states -> accountStore);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(OK);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager).submit(eq(TRANSACTION_BODY), any(), eq(txBodyParser));
        verify(opCounters).countSubmitted(ConsensusCreateTopic);
    }

    @Test
    void testParseAndCheckWithZeroStakeFails() throws InvalidProtocolBufferException, PreCheckException {
        // given
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
        when(nodeInfo.isSelfZeroStake()).thenReturn(true);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(INVALID_NODE_ACCOUNT);
        assertThat(response.getCost()).isZero();
        verify(opCounters, never()).countReceived(any());
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @ParameterizedTest
    @EnumSource(PlatformStatus.class)
    void testParseAndCheckWithInactivePlatformFails(final PlatformStatus status)
            throws InvalidProtocolBufferException, PreCheckException {
        if (status != PlatformStatus.ACTIVE) {
            // given
            final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);
            final var inactivePlatformStatus = mock(CurrentPlatformStatus.class);
            when(inactivePlatformStatus.get()).thenReturn(status);
            workflow = new IngestWorkflowImpl(
                    nodeInfo,
                    inactivePlatformStatus,
                    stateAccessor,
                    onset,
                    checker,
                    throttleAccumulator,
                    submissionManager,
                    opCounters);

            // when
            workflow.submitTransaction(ctx, requestBuffer, responseBuffer);

            // then
            final TransactionResponse response = parseResponse(responseBuffer);
            assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(PLATFORM_NOT_ACTIVE);
            assertThat(response.getCost()).isZero();
            verify(opCounters, never()).countReceived(any());
            verify(submissionManager, never()).submit(any(), any(), any());
            verify(opCounters, never()).countSubmitted(any());
        }
    }

    @Test
    void testOnsetFails(@Mock WorkflowOnset localOnset) throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(localOnset.parseAndCheck(any(), any(ByteBuffer.class)))
                .thenThrow(new PreCheckException(INVALID_TRANSACTION));
        workflow = new IngestWorkflowImpl(
                nodeInfo,
                currentPlatformStatus,
                stateAccessor,
                localOnset,
                checker,
                throttleAccumulator,
                submissionManager,
                opCounters);
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(INVALID_TRANSACTION);
        assertThat(response.getCost()).isZero();
        verify(opCounters, never()).countReceived(any());
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @Test
    void testThrottleFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(throttleAccumulator.shouldThrottle(ConsensusCreateTopic)).thenReturn(true);
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(BUSY);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @Test
    void testPayerAccountNotFoundFails(@Mock ReadableAccountStore localAccountStore)
            throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(localAccountStore.getAccount(any())).thenReturn(Optional.empty());
        workflow = new IngestWorkflowImpl(
                nodeInfo,
                currentPlatformStatus,
                stateAccessor,
                onset,
                checker,
                throttleAccumulator,
                submissionManager,
                opCounters);
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer, states -> localAccountStore);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(PAYER_ACCOUNT_NOT_FOUND);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(ConsensusCreateTopic);
    }

    @Test
    void testPayerSignatureFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        doThrow(new PreCheckException(INVALID_PAYER_SIGNATURE))
                .when(checker)
                .checkPayerSignature(eq(TRANSACTION_BODY), eq(SIGNATURE_MAP), any());
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer, states -> accountStore);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(INVALID_PAYER_SIGNATURE);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @Test
    void testSolvencyFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        doThrow(new InsufficientBalanceException(INSUFFICIENT_ACCOUNT_BALANCE, 42L))
                .when(checker)
                .checkSolvency(eq(TRANSACTION_BODY), eq(ConsensusCreateTopic), any());
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer, states -> accountStore);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(INSUFFICIENT_ACCOUNT_BALANCE);
        assertThat(response.getCost()).isEqualTo(42L);
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @Test
    void testSubmitFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        doThrow(new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED))
                .when(submissionManager)
                .submit(eq(TRANSACTION_BODY), eq(requestBuffer.array()), any());
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer, states -> accountStore);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(PLATFORM_TRANSACTION_NOT_CREATED);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(opCounters, never()).countSubmitted(any());
    }

    private static TransactionResponse parseResponse(ByteBuffer responseBuffer) throws InvalidProtocolBufferException {
        final byte[] bytes = new byte[responseBuffer.position()];
        responseBuffer.get(0, bytes);
        return TransactionResponse.parseFrom(bytes);
    }
}
