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

package com.hedera.node.app.workflows.onset;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowOnsetTest {

    @Mock(strictness = LENIENT)
    private OnsetChecker checker;

    @Mock(strictness = LENIENT)
    private Parser<Query> queryParser;

    @Mock(strictness = LENIENT)
    private Parser<Transaction> txParser;

    @Mock(strictness = LENIENT)
    private Parser<SignedTransaction> signedParser;

    @Mock(strictness = LENIENT)
    private Parser<TransactionBody> txBodyParser;

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
    void setup() throws InvalidProtocolBufferException, PreCheckException {
        inputBuffer = ByteBuffer.allocate(0);

        final var content = ConsensusCreateTopicTransactionBody.newBuilder().build();
        txBody = TransactionBody.newBuilder().setConsensusCreateTopic(content).build();

        bodyBytes = ByteString.copyFrom("bodyBytes", StandardCharsets.UTF_8);
        signatureMap = SignatureMap.newBuilder().build();
        signedTx = SignedTransaction.newBuilder()
                .setBodyBytes(bodyBytes)
                .setSigMap(signatureMap)
                .build();

        signedTransactionBytes = ByteString.copyFrom("signedTransactionBytes", StandardCharsets.UTF_8);

        tx = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransactionBytes)
                .build();

        when(txBodyParser.parseFrom(bodyBytes)).thenReturn(txBody);
        when(signedParser.parseFrom(signedTransactionBytes)).thenReturn(signedTx);
        when(txParser.parseFrom(inputBuffer)).thenReturn(tx);

        ctx = new SessionContext(queryParser, txParser, signedParser, txBodyParser);

        when(checker.checkTransactionBody(any())).thenReturn(OK);

        onset = new WorkflowOnset(checker);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalArguments() {
        assertThatThrownBy(() -> new WorkflowOnset(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testParseAndCheckSucceeds() throws PreCheckException {
        // when
        final var result = onset.parseAndCheck(ctx, inputBuffer);

        // then
        assertThat(result.txBody()).isEqualTo(txBody);
        assertThat(result.errorCode()).isEqualTo(OK);
        assertThat(result.signatureMap()).isEqualTo(signatureMap);
        assertThat(result.functionality()).isEqualTo(HederaFunctionality.ConsensusCreateTopic);
    }

    @Test
    void testParseAndCheckWithByteArraySucceeds(@Mock Parser<Transaction> localTxParser)
            throws PreCheckException, InvalidProtocolBufferException {
        // when
        final var byteArray = new byte[0];
        when(localTxParser.parseFrom(byteArray)).thenReturn(tx);

        final var localCtx = new SessionContext(queryParser, localTxParser, signedParser, txBodyParser);
        final var result = onset.parseAndCheck(localCtx, byteArray);

        // then
        assertThat(result.txBody()).isEqualTo(txBody);
        assertThat(result.errorCode()).isEqualTo(OK);
        assertThat(result.signatureMap()).isEqualTo(signatureMap);
        assertThat(result.functionality()).isEqualTo(HederaFunctionality.ConsensusCreateTopic);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testParseAndCheckWithIllegalArguments() {
        // given
        final var byteArray = new byte[0];

        // then
        assertThatThrownBy(() -> onset.parseAndCheck(null, inputBuffer)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> onset.parseAndCheck(ctx, (ByteBuffer) null)).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> onset.parseAndCheck(null, byteArray)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> onset.parseAndCheck(ctx, (byte[]) null)).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings({"JUnitMalformedDeclaration", "deprecation"})
    @Test
    void testParseAndChechWithDeprecatedFieldsSucceeds(
            @Mock Parser<Transaction> localTxParser,
            @Mock Parser<SignedTransaction> localSignedParser,
            @Mock Parser<TransactionBody> localBodyParser)
            throws InvalidProtocolBufferException, PreCheckException {
        // given
        final var content = ConsensusDeleteTopicTransactionBody.newBuilder().build();
        final var localTxBody =
                TransactionBody.newBuilder().setConsensusDeleteTopic(content).build();

        final var localBodyBytes = ByteString.copyFrom("bodyBytes", StandardCharsets.UTF_8);

        final var localTx = Transaction.newBuilder()
                .setBodyBytes(localBodyBytes)
                .setSigMap(signatureMap)
                .build();

        when(localBodyParser.parseFrom(localBodyBytes)).thenReturn(localTxBody);
        when(localTxParser.parseFrom(inputBuffer)).thenReturn(localTx);

        final var localCtx = new SessionContext(queryParser, localTxParser, localSignedParser, localBodyParser);

        // when
        final var result = onset.parseAndCheck(localCtx, inputBuffer);

        // then
        assertThat(result.txBody()).isEqualTo(localTxBody);
        assertThat(result.errorCode()).isEqualTo(OK);
        assertThat(result.signatureMap()).isEqualTo(signatureMap);
        assertThat(result.functionality()).isEqualTo(HederaFunctionality.ConsensusDeleteTopic);
    }

    @Test
    void testParseAndCheckWithTransactionParseFails(@Mock Parser<Transaction> localTxParser)
            throws InvalidProtocolBufferException {
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
        final var localOnset = new WorkflowOnset(localChecker);

        // then
        assertThatThrownBy(() -> localOnset.parseAndCheck(ctx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_OVERSIZE);
    }

    @Test
    void testParseAndCheckWithSignedTransactionParseFails(@Mock Parser<SignedTransaction> localSignedParser)
            throws InvalidProtocolBufferException {
        // given
        when(localSignedParser.parseFrom(signedTransactionBytes))
                .thenThrow(new InvalidProtocolBufferException("Expected failure"));
        final var localCtx = new SessionContext(queryParser, txParser, localSignedParser, txBodyParser);

        // then
        assertThatThrownBy(() -> onset.parseAndCheck(localCtx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION);
    }

    @Test
    void testParseAndCheckWithSignedTransactionCheckFails(@Mock OnsetChecker localChecker) throws PreCheckException {
        // given
        doThrow(new PreCheckException(TRANSACTION_HAS_UNKNOWN_FIELDS))
                .when(localChecker)
                .checkSignedTransaction(signedTx);
        final var localOnset = new WorkflowOnset(localChecker);

        // then
        assertThatThrownBy(() -> localOnset.parseAndCheck(ctx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_HAS_UNKNOWN_FIELDS);
    }

    @Test
    void testParseAndCheckWithTransactionBodyParseFails(@Mock Parser<TransactionBody> localTxBodyParser)
            throws InvalidProtocolBufferException {
        // given
        when(localTxBodyParser.parseFrom(bodyBytes)).thenThrow(new InvalidProtocolBufferException("Expected failure"));
        final var localCtx = new SessionContext(queryParser, txParser, signedParser, localTxBodyParser);

        // then
        assertThatThrownBy(() -> onset.parseAndCheck(localCtx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION_BODY);
    }

    @Test
    void testParseAndCheckWithTransactionBodyCheckCatastrophicFails(@Mock OnsetChecker localChecker)
            throws PreCheckException {
        // given
        when(localChecker.checkTransactionBody(txBody)).thenThrow(new PreCheckException(INVALID_TRANSACTION_ID));
        final var localOnset = new WorkflowOnset(localChecker);

        // then
        assertThatThrownBy(() -> localOnset.parseAndCheck(ctx, inputBuffer))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION_ID);
    }

    @SuppressWarnings("JUnitMalformedDeclaration")
    @Test
    void testParseAndCheckWithTransactionBodyCheckMildFails(
            @Mock OnsetChecker localChecker, @Mock Parser<TransactionBody> localTxBodyParser)
            throws InvalidProtocolBufferException, PreCheckException {
        // given
        final var localTxBody = TransactionBody.newBuilder().build();
        when(localTxBodyParser.parseFrom(bodyBytes)).thenReturn(localTxBody);
        when(localChecker.checkTransactionBody(localTxBody)).thenReturn(DUPLICATE_TRANSACTION);
        final var localCtx = new SessionContext(queryParser, txParser, signedParser, localTxBodyParser);

        final var localOnset = new WorkflowOnset(localChecker);

        // when
        final var result = localOnset.parseAndCheck(localCtx, inputBuffer);

        // then
        assertThat(result.txBody()).isEqualTo(localTxBody);
        assertThat(result.errorCode()).isEqualTo(DUPLICATE_TRANSACTION);
        assertThat(result.signatureMap()).isEqualTo(signatureMap);
        assertThat(result.functionality()).isEqualTo(HederaFunctionality.UNRECOGNIZED);
    }

    @Test
    void testParseAndCheckWithUnknownHederaFunctionalityFails(@Mock Parser<TransactionBody> localTxBodyParser)
            throws InvalidProtocolBufferException, PreCheckException {
        // given
        final var localTxBody = TransactionBody.newBuilder().build();
        when(localTxBodyParser.parseFrom(bodyBytes)).thenReturn(localTxBody);
        final var localCtx = new SessionContext(queryParser, txParser, signedParser, localTxBodyParser);

        // when
        final var result = onset.parseAndCheck(localCtx, inputBuffer);

        // then
        assertThat(result.txBody()).isEqualTo(localTxBody);
        assertThat(result.errorCode()).isEqualTo(INVALID_TRANSACTION_BODY);
        assertThat(result.signatureMap()).isEqualTo(signatureMap);
        assertThat(result.functionality()).isEqualTo(HederaFunctionality.UNRECOGNIZED);
    }
}
