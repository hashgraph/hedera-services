package com.hedera.node.app.workflows.ingest;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.common.InsufficientBalanceException;
import com.hedera.node.app.workflows.common.PreCheckException;
import com.hedera.node.app.workflows.common.SubmissionManager;
import com.hedera.node.app.workflows.onset.OnsetResult;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hedera.services.stats.HapiOpCounters;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopic;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CreateTransactionRecord;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.anyOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestWorkflowImplTest {

    private static final TransactionBody TRANSACTION_BODY = TransactionBody.getDefaultInstance();
    private static final SignatureMap SIGNATURE_MAP = SignatureMap.getDefaultInstance();
    private static final OnsetResult ONSET_RESULT = new OnsetResult(
            TRANSACTION_BODY, SIGNATURE_MAP, ConsensusCreateTopic
    );

    @Mock private WorkflowOnset onset;
    @Mock private IngestChecker checker;
    @Mock private ThrottleAccumulator throttleAccumulator;
    @Mock private SubmissionManager submissionManager;
    @Mock private HapiOpCounters opCounters;

    @Mock private ByteBuffer requestBuffer;

    @Mock private Parser<Query> queryParser;
    @Mock private Parser<Transaction> txParser;
    @Mock private Parser<SignedTransaction> signedParser;
    @Mock private Parser<TransactionBody> txBodyParser;

    private SessionContext ctx;
    private IngestWorkflowImpl workflow;

    @BeforeEach
    void setup() {
        ctx = new SessionContext(queryParser, txParser, signedParser, txBodyParser);
        workflow = new IngestWorkflowImpl(onset, checker, throttleAccumulator, submissionManager, opCounters);
    }

    @Test
    void testConstructorWithInvalidArguments() {
        assertThatThrownBy(() -> new IngestWorkflowImpl(null, checker, throttleAccumulator, submissionManager, opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(onset, null, throttleAccumulator, submissionManager, opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(onset, checker, null, submissionManager, opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(onset, checker, throttleAccumulator, null, opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(onset, checker, throttleAccumulator, submissionManager, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSuccess() throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(onset.parseAndCheck(any(), any())).thenReturn(ONSET_RESULT);
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.handleTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(OK);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager).submit(ctx, TRANSACTION_BODY, requestBuffer);
        verify(opCounters).countSubmitted(ConsensusCreateTopic);
    }

    @Test
    void testOnsetFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(onset.parseAndCheck(any(), any())).thenThrow(new PreCheckException(INVALID_TRANSACTION));
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.handleTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(INVALID_TRANSACTION);
        assertThat(response.getCost()).isZero();
        verify(opCounters, never()).countReceived(any());
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @Test
    void testSemanticFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(onset.parseAndCheck(any(), any())).thenReturn(ONSET_RESULT);
        doThrow(new PreCheckException(NOT_SUPPORTED)).when(checker).checkTransactionSemantic(TRANSACTION_BODY, ConsensusCreateTopic);
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.handleTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @Test
    void testPayerSignatureFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(onset.parseAndCheck(any(), any())).thenReturn(ONSET_RESULT);
        doThrow(new PreCheckException(INVALID_PAYER_SIGNATURE)).when(checker).checkPayerSignature(eq(TRANSACTION_BODY), eq(SIGNATURE_MAP), any());
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.handleTransaction(ctx, requestBuffer, responseBuffer);

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
        when(onset.parseAndCheck(any(), any())).thenReturn(ONSET_RESULT);
        doThrow(new InsufficientBalanceException(INSUFFICIENT_ACCOUNT_BALANCE, 42L)).when(checker).checkSolvency(eq(TRANSACTION_BODY), eq(ConsensusCreateTopic), any());
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.handleTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(INSUFFICIENT_ACCOUNT_BALANCE);
        assertThat(response.getCost()).isEqualTo(42L);
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @Test
    void testThrottleFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(onset.parseAndCheck(any(), any())).thenReturn(ONSET_RESULT);
        when(throttleAccumulator.shouldThrottle(ConsensusCreateTopic)).thenReturn(true);
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.handleTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(BUSY);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    private static TransactionResponse parseResponse(ByteBuffer responseBuffer) throws InvalidProtocolBufferException {
        final byte[] bytes = new byte[responseBuffer.position()];
        responseBuffer.get(0, bytes);
        return TransactionResponse.parseFrom(bytes);
    }
}
