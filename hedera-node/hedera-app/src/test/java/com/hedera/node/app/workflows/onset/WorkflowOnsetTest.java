package com.hedera.node.app.workflows.onset;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.workflows.common.PreCheckException;
import com.hedera.services.context.CurrentPlatformStatus;
import com.hedera.services.context.NodeInfo;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopic;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.system.PlatformStatus;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.in;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowOnsetTest {

    @Mock private NodeInfo nodeInfo;
    @Mock(strictness = LENIENT) private CurrentPlatformStatus currentPlatformStatus;
    @Mock private OnsetChecker checker;

    @Mock(strictness = LENIENT) private Parser<Query> queryParser;
    @Mock(strictness = LENIENT) private Parser<Transaction> txParser;
    @Mock(strictness = LENIENT) private Parser<SignedTransaction> signedParser;
    @Mock(strictness = LENIENT) private Parser<TransactionBody> txBodyParser;

    private SessionContext ctx;

    private Transaction tx;
    private ByteString signedTransactionBytes;
    private SignatureMap signatureMap;
    private SignedTransaction signedTx;
    private ByteString bodyBytes;
    private TransactionBody txBody;

    private ByteBuffer inputBuffer;
    private WorkflowOnset onset;

    @BeforeEach
    void setup() throws InvalidProtocolBufferException {
        when(currentPlatformStatus.get()).thenReturn(PlatformStatus.ACTIVE);

        inputBuffer = ByteBuffer.allocate(0);

        final var content = ConsensusCreateTopicTransactionBody.newBuilder().build();
        txBody = TransactionBody.newBuilder().setConsensusCreateTopic(content).build();

        bodyBytes = ByteString.copyFrom("bodyBytes", StandardCharsets.UTF_8);
        signatureMap = SignatureMap.newBuilder().build();
        signedTx = SignedTransaction.newBuilder().setBodyBytes(bodyBytes).setSigMap(signatureMap).build();

        signedTransactionBytes = ByteString.copyFrom("signedTransactionBytes", StandardCharsets.UTF_8);

        tx = Transaction.newBuilder().setSignedTransactionBytes(signedTransactionBytes).build();

        when(txBodyParser.parseFrom(bodyBytes)).thenReturn(txBody);
        when(signedParser.parseFrom(signedTransactionBytes)).thenReturn(signedTx);
        when(txParser.parseFrom(inputBuffer)).thenReturn(tx);

        ctx = new SessionContext(queryParser, txParser, signedParser, txBodyParser);
        onset = new WorkflowOnset(nodeInfo, currentPlatformStatus, checker);
    }

    @Test
    void testConstructorWithIllegalArguments() {
        assertThatThrownBy(() -> new WorkflowOnset(null, currentPlatformStatus, checker))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WorkflowOnset(nodeInfo, null, checker))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WorkflowOnset(nodeInfo, currentPlatformStatus, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testParseAndCheckSucceeds() throws PreCheckException {
        // when
        final var result = onset.parseAndCheck(ctx, inputBuffer);

        // then
        assertThat(result.txBody()).isEqualTo(txBody);
        assertThat(result.signatureMap()).isEqualTo(signatureMap);
        assertThat(result.functionality()).isEqualTo(HederaFunctionality.ConsensusCreateTopic);
    }

    @Test
    void testParseAndChechWithDeprecatedFieldsSucceeds(
            @Mock Parser<Transaction> localTxParser,
            @Mock Parser<SignedTransaction> localSignedParser,
            @Mock Parser<TransactionBody> localBodyParser) throws InvalidProtocolBufferException, PreCheckException {
        // given
        final var content = ConsensusDeleteTopicTransactionBody.newBuilder().build();
        final var localTxBody = TransactionBody.newBuilder().setConsensusDeleteTopic(content).build();

        final var localBodyBytes = ByteString.copyFrom("bodyBytes", StandardCharsets.UTF_8);

        final var localTx = Transaction.newBuilder().setBodyBytes(localBodyBytes).setSigMap(signatureMap).build();

        when(localBodyParser.parseFrom(localBodyBytes)).thenReturn(localTxBody);
        when(localTxParser.parseFrom(inputBuffer)).thenReturn(localTx);

        final var localCtx = new SessionContext(queryParser, localTxParser, localSignedParser, localBodyParser);

        // when
        final var result = onset.parseAndCheck(localCtx, inputBuffer);

        // then
        assertThat(result.txBody()).isEqualTo(localTxBody);
        assertThat(result.signatureMap()).isEqualTo(signatureMap);
        assertThat(result.functionality()).isEqualTo(HederaFunctionality.ConsensusDeleteTopic);
    }

    @Test
    void testParseAndCheckWithZeroStakeFails() {
        // given
        when(nodeInfo.isSelfZeroStake()).thenReturn(true);

        // then
        assertThatThrownBy(() -> onset.parseAndCheck(ctx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_NODE_ACCOUNT);
    }

    @ParameterizedTest
    @EnumSource(PlatformStatus.class)
    void testParseAndCheckWithInactivePlatformFails(final PlatformStatus status) {
        if (status != PlatformStatus.ACTIVE) {
            // given
            final var inactivePlatformStatus = mock(CurrentPlatformStatus.class);
            when(inactivePlatformStatus.get()).thenReturn(status);
            onset = new WorkflowOnset(nodeInfo, inactivePlatformStatus, checker);

            // then
            assertThatThrownBy(() -> onset.parseAndCheck(ctx, inputBuffer))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", PLATFORM_NOT_ACTIVE);
        }
    }

    @Test
    void testParseAndCheckWithTransactionParseFails(@Mock Parser<Transaction> localTxParser) throws InvalidProtocolBufferException {
        // given
        when(localTxParser.parseFrom(inputBuffer)).thenThrow(new InvalidProtocolBufferException("Expected failure"));
        final var localCtx = new SessionContext(queryParser, localTxParser, signedParser, txBodyParser);

        // then
        assertThatThrownBy(() -> onset.parseAndCheck(localCtx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION);
    }

    @Test
    void testParseAndCheckWithTransactionCheckFails(@Mock OnsetChecker localChecker) throws PreCheckException {
        // given
        doThrow(new PreCheckException(TRANSACTION_OVERSIZE)).when(localChecker).checkTransaction(tx);
        final var localOnset = new WorkflowOnset(nodeInfo, currentPlatformStatus, localChecker);

        // then
        assertThatThrownBy(() -> localOnset.parseAndCheck(ctx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_OVERSIZE);
    }

    @Test
    void testParseAndCheckWithSignedTransactionParseFails(@Mock Parser<SignedTransaction> localSignedParser) throws InvalidProtocolBufferException {
        // given
        when(localSignedParser.parseFrom(signedTransactionBytes)).thenThrow(new InvalidProtocolBufferException("Expected failure"));
        final var localCtx = new SessionContext(queryParser, txParser, localSignedParser, txBodyParser);

        // then
        assertThatThrownBy(() -> onset.parseAndCheck(localCtx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION);
    }

    @Test
    void testParseAndCheckWithSignedTransactionCheckFails(@Mock OnsetChecker localChecker) throws PreCheckException {
        // given
        doThrow(new PreCheckException(TRANSACTION_HAS_UNKNOWN_FIELDS)).when(localChecker).checkSignedTransaction(signedTx);
        final var localOnset = new WorkflowOnset(nodeInfo, currentPlatformStatus, localChecker);

        // then
        assertThatThrownBy(() -> localOnset.parseAndCheck(ctx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_HAS_UNKNOWN_FIELDS);
    }

    @Test
    void testParseAndCheckWithTransactionBodyParseFails(@Mock Parser<TransactionBody> localTxBodyParser) throws InvalidProtocolBufferException {
        // given
        when(localTxBodyParser.parseFrom(bodyBytes)).thenThrow(new InvalidProtocolBufferException("Expected failure"));
        final var localCtx = new SessionContext(queryParser, txParser, signedParser, localTxBodyParser);

        // then
        assertThatThrownBy(() -> onset.parseAndCheck(localCtx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION_BODY);
    }

    @Test
    void testParseAndCheckWithTransactionBodyCheckFails(@Mock OnsetChecker localChecker) throws PreCheckException {
        // given
        doThrow(new PreCheckException(INVALID_TRANSACTION_ID)).when(localChecker).checkTransactionBody(txBody);
        final var localOnset = new WorkflowOnset(nodeInfo, currentPlatformStatus, localChecker);

        // then
        assertThatThrownBy(() -> localOnset.parseAndCheck(ctx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION_ID);
    }

    @Test
    void testParseAndCheckWithUnknownHederaFunctionalityFails(@Mock Parser<TransactionBody> localTxBodyParser) throws InvalidProtocolBufferException {
        // given
        final var localTxBody = TransactionBody.newBuilder().build();
        when(localTxBodyParser.parseFrom(bodyBytes)).thenReturn(localTxBody);
        final var localCtx = new SessionContext(queryParser, txParser, signedParser, localTxBodyParser);

        // then
        assertThatThrownBy(() -> onset.parseAndCheck(localCtx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION_BODY);
    }
}
