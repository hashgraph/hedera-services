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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.records.RecordCache;
import com.hedera.node.app.service.mono.stats.HapiOpCounters;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.hapi.node.base.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnsetCheckerTest {

    private static final int MAX_TXN_SIZE = 6 * 1024;
    private static final int MAX_MEMO_SIZE = 2 * 1024;
    private static final long MIN_DURATION = 10L;
    private static final long MAX_DURATION = 120L;
    private static final ByteString CONTENT =
            ByteString.copyFrom("Hello world!", StandardCharsets.UTF_8);
    private static final Duration ONE_MINUTE = Duration.newBuilder().setSeconds(60).build();

    @Mock private RecordCache recordCache;

    @Mock(strictness = LENIENT)
    private GlobalDynamicProperties dynamicProperties;

    @Mock private HapiOpCounters counters;

    private TransactionID transactionID;

    private OnsetChecker checker;

    @BeforeEach
    void setup() {
        when(dynamicProperties.minTxnDuration()).thenReturn(MIN_DURATION);
        when(dynamicProperties.maxTxnDuration()).thenReturn(MAX_DURATION);
        when(dynamicProperties.maxMemoUtf8Bytes()).thenReturn(MAX_MEMO_SIZE);

        final var payerId = AccountID.newBuilder().setAccountNum(1L).build();
        final var now = Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build();
        transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payerId)
                        .setTransactionValidStart(now)
                        .build();

        checker = new OnsetChecker(MAX_TXN_SIZE, recordCache, dynamicProperties, counters);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalArguments() {
        assertThatThrownBy(() -> new OnsetChecker(0, recordCache, dynamicProperties, counters))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OnsetChecker(-1, recordCache, dynamicProperties, counters))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new OnsetChecker(MAX_TXN_SIZE, null, dynamicProperties, counters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OnsetChecker(MAX_TXN_SIZE, recordCache, null, counters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new OnsetChecker(MAX_TXN_SIZE, recordCache, dynamicProperties, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCheckTransactionSucceeds() {
        // given
        final var tx = Transaction.newBuilder().setSignedTransactionBytes(CONTENT).build();

        // then
        assertThatCode(() -> checker.checkTransaction(tx)).doesNotThrowAnyException();
        verify(counters, never()).countDeprecatedTxnReceived();
    }

    @SuppressWarnings("deprecation")
    @Test
    void testCheckTransactionWithDeprecatedBytesOnlySucceeds() {
        // given
        final var tx = Transaction.newBuilder().setBodyBytes(CONTENT).build();

        // then
        assertThatCode(() -> checker.checkTransaction(tx)).doesNotThrowAnyException();
        verify(counters).countDeprecatedTxnReceived();
    }

    @SuppressWarnings("deprecation")
    @Test
    void testCheckTransactionWithDeprecatedBytesFails() {
        // given
        final var tx =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(CONTENT)
                        .setBodyBytes(CONTENT)
                        .build();

        // then
        assertThatThrownBy(() -> checker.checkTransaction(tx))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION);
        verify(counters).countDeprecatedTxnReceived();
    }

    @SuppressWarnings("deprecation")
    @Test
    void testCheckTransactionWithDeprecatedSignatureMapFails() {
        // given
        final var signatureMap = SignatureMap.getDefaultInstance();
        final var tx =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(CONTENT)
                        .setSigMap(signatureMap)
                        .build();

        // then
        assertThatThrownBy(() -> checker.checkTransaction(tx))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION);
        verify(counters).countDeprecatedTxnReceived();
    }

    @Test
    void testCheckTransactionWithNoBytesFails() {
        // given
        final var tx = Transaction.newBuilder().build();

        // then
        assertThatThrownBy(() -> checker.checkTransaction(tx))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION_BODY);
        verify(counters, never()).countDeprecatedTxnReceived();
    }

    @Test
    void testCheckTransactionTooLargeFails() {
        // given
        final var message = "1".repeat(MAX_TXN_SIZE + 1);
        final var content = ByteString.copyFrom(message, StandardCharsets.UTF_8);
        final var tx = Transaction.newBuilder().setSignedTransactionBytes(content).build();

        // then
        assertThatThrownBy(() -> checker.checkTransaction(tx))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_OVERSIZE);
        verify(counters, never()).countDeprecatedTxnReceived();
    }

    @Test
    void testCheckTransactionWithUnknownFieldsFails() {
        // given
        final var field = UnknownFieldSet.Field.getDefaultInstance();
        final var unknownFields = UnknownFieldSet.newBuilder().addField(42, field).build();
        final var tx =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(CONTENT)
                        .setUnknownFields(unknownFields)
                        .build();

        // then
        assertThatThrownBy(() -> checker.checkTransaction(tx))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_HAS_UNKNOWN_FIELDS);
        verify(counters, never()).countDeprecatedTxnReceived();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testCheckTransactionWithIllegalArguments() {
        assertThatThrownBy(() -> checker.checkTransaction(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCheckSignedTransactionSuccess() {
        // given
        final var tx = SignedTransaction.newBuilder().build();

        // then
        assertThatCode(() -> checker.checkSignedTransaction(tx)).doesNotThrowAnyException();
    }

    @Test
    void testCheckSignedTransactionWithUnknownFieldsFails() {
        // given
        final var field = UnknownFieldSet.Field.getDefaultInstance();
        final var unknownFields = UnknownFieldSet.newBuilder().addField(42, field).build();
        final var tx = SignedTransaction.newBuilder().setUnknownFields(unknownFields).build();

        // then
        assertThatThrownBy(() -> checker.checkSignedTransaction(tx))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_HAS_UNKNOWN_FIELDS);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testCheckSignedTransactionWithIllegalArguments() {
        assertThatThrownBy(() -> checker.checkSignedTransaction(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCheckTransactionBodySuccess() throws PreCheckException {
        // given
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(ONE_MINUTE)
                        .build();

        // when
        final var result = checker.checkTransactionBody(txBody);

        // then
        assertThat(result).isEqualTo(OK);
    }

    @Test
    void testCheckTransactionBodyWithUnknownFieldsFails() {
        // given
        final var field = UnknownFieldSet.Field.getDefaultInstance();
        final var unknownFields = UnknownFieldSet.newBuilder().addField(42, field).build();
        final var txBody = TransactionBody.newBuilder().setUnknownFields(unknownFields).build();

        // then
        assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_HAS_UNKNOWN_FIELDS);
    }

    @Test
    void testCheckTransactionBodyWithoutTransactionIDFails() {
        // given
        final var txBody =
                TransactionBody.newBuilder().setTransactionValidDuration(ONE_MINUTE).build();

        // then
        assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION_ID);
    }

    @Test
    void testCheckTransactionBodyWithInvalidAccountNumFails() {
        // given
        final var payerId = AccountID.newBuilder().setAccountNum(0L).build();
        final var now = Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build();
        transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payerId)
                        .setTransactionValidStart(now)
                        .build();
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(ONE_MINUTE)
                        .build();

        // then
        assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", PAYER_ACCOUNT_NOT_FOUND);
    }

    @Test
    void testCheckTransactionBodyWithInvalidRealmNumFails() {
        // given
        final var payerId = AccountID.newBuilder().setAccountNum(1L).setRealmNum(-1L).build();
        final var now = Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build();
        transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payerId)
                        .setTransactionValidStart(now)
                        .build();
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(ONE_MINUTE)
                        .build();

        // then
        assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", PAYER_ACCOUNT_NOT_FOUND);
    }

    @Test
    void testCheckTransactionBodyWithInvalidShardNumFails() {
        // given
        final var payerId = AccountID.newBuilder().setAccountNum(1L).setShardNum(-1L).build();
        final var now = Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build();
        transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payerId)
                        .setTransactionValidStart(now)
                        .build();
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(ONE_MINUTE)
                        .build();

        // then
        assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", PAYER_ACCOUNT_NOT_FOUND);

        //        final var payerId = AccountID.newBuilder().setAccountNum(1L).build();
    }

    @Test
    void testCheckTransactionBodyWithTooLargeMemoFails() {
        // given
        final var memo = "1".repeat(MAX_MEMO_SIZE) + "1";
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(ONE_MINUTE)
                        .setMemo(memo)
                        .build();

        // then
        assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", MEMO_TOO_LONG);
    }

    @Test
    void testCheckTransactionBodyWithZeroByteMemoFails() {
        // given
        final var memo = "Hello World \0";
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(ONE_MINUTE)
                        .setMemo(memo)
                        .build();

        // then
        assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_ZERO_BYTE_IN_STRING);
    }

    @Test
    void testCheckTransactionBodyDuplicateFails() throws PreCheckException {
        // given
        when(recordCache.isReceiptPresent(transactionID)).thenReturn(true);
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(ONE_MINUTE)
                        .build();

        // when
        final var result = checker.checkTransactionBody(txBody);

        // then
        assertThat(result).isEqualTo(DUPLICATE_TRANSACTION);
    }

    @Test
    void testCheckTransactionBodyWithInvalidFeeFails() throws PreCheckException {
        // given
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(ONE_MINUTE)
                        .setTransactionFee(-1L)
                        .build();

        // when
        final var result = checker.checkTransactionBody(txBody);

        // then
        assertThat(result).isEqualTo(INSUFFICIENT_TX_FEE);
    }

    @Test
    void testCheckTransactionBodyWithTooSmallDurationFails() throws PreCheckException {
        // given
        final var duration = Duration.newBuilder().setSeconds(MIN_DURATION - 1).build();
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(duration)
                        .build();

        // when
        final var result = checker.checkTransactionBody(txBody);

        // then
        assertThat(result).isEqualTo(INVALID_TRANSACTION_DURATION);
    }

    @Test
    void testCheckTransactionBodyWithTooLargeDurationFails() throws PreCheckException {
        // given
        final var duration = Duration.newBuilder().setSeconds(MAX_DURATION + 1).build();
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(duration)
                        .build();

        // when
        final var result = checker.checkTransactionBody(txBody);

        // then
        assertThat(result).isEqualTo(INVALID_TRANSACTION_DURATION);
    }

    @Test
    void testCheckTransactionBodyWithExpiredTimeFails() throws PreCheckException {
        // given
        final var payerId = AccountID.newBuilder().setAccountNum(1L).build();
        final var past =
                Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() - 100).build();
        transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payerId)
                        .setTransactionValidStart(past)
                        .build();
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(ONE_MINUTE)
                        .build();

        // when
        final var result = checker.checkTransactionBody(txBody);

        // then
        assertThat(result).isEqualTo(TRANSACTION_EXPIRED);
    }

    @Test
    void testCheckTransactionBodyWithFutureStartFails() throws PreCheckException {
        // given
        final var payerId = AccountID.newBuilder().setAccountNum(1L).build();
        final var future =
                Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 100).build();
        transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payerId)
                        .setTransactionValidStart(future)
                        .build();
        final var txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setTransactionValidDuration(ONE_MINUTE)
                        .build();

        // when
        final var result = checker.checkTransactionBody(txBody);

        // then
        assertThat(result).isEqualTo(INVALID_TRANSACTION_START);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testCheckTransactionBodyWithIllegalArguments() {
        assertThatThrownBy(() -> checker.checkTransactionBody(null))
                .isInstanceOf(NullPointerException.class);
    }
}
