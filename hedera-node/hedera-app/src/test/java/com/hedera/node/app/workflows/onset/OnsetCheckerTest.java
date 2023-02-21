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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.RecordCache;
import com.hedera.pbj.runtime.io.Bytes;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnsetCheckerTest extends AppTestBase {
    /** Value for {@link GlobalDynamicProperties#maxMemoUtf8Bytes()} for this test */
    private static final int MAX_MEMO_SIZE = 2 * 1024;
    /** Value for {@link GlobalDynamicProperties#minTxnDuration()} for this test */
    private static final long MIN_DURATION = 10L;
    /** Value for {@link GlobalDynamicProperties#maxTxnDuration()} for this test */
    private static final long MAX_DURATION = 120L;

    /** Value for {@link TransactionBody#memo()} for most tests */
    private static final Bytes CONTENT = Bytes.wrap("Hello world!");
    /** The standard {@link TransactionBody#transactionValidDuration()} for most tests */
    private static final Duration ONE_MINUTE = Duration.newBuilder().seconds(60).build();

    // These are mocks to use for dependencies of the OnsetChecker
    @Mock private RecordCache recordCache;
    @Mock(strictness = LENIENT) private GlobalDynamicProperties dynamicProperties;
    @Mock private Metrics metrics;
    @Mock private Counter deprecationCounter;

    /** The standard {@link TransactionID} used in most tests */
    private TransactionID transactionID;
    /** The standard {@link OnsetChecker} used in most tests */
    private OnsetChecker checker;

    @BeforeEach
    void setup() {
        when(metrics.getOrCreate(any())).thenReturn(deprecationCounter);
        when(dynamicProperties.minTxnDuration()).thenReturn(MIN_DURATION);
        when(dynamicProperties.maxTxnDuration()).thenReturn(MAX_DURATION);
        when(dynamicProperties.maxMemoUtf8Bytes()).thenReturn(MAX_MEMO_SIZE);

        final var payerId = AccountID.newBuilder().accountNum(1L).build();
        final var now = Timestamp.newBuilder().seconds(Instant.now().getEpochSecond()).build();
            transactionID =
                    TransactionID.newBuilder()
                            .accountID(payerId)
                            .transactionValidStart(now)
                            .build();

        checker = new OnsetChecker(recordCache, dynamicProperties, metrics);
    }

        @Test
        @SuppressWarnings("ConstantConditions")
        @DisplayName("Verify that the constructor throws NPE for null arguments")
        void testConstructorWithIllegalArguments() {
            assertThatThrownBy(() -> new OnsetChecker(null, dynamicProperties, metrics))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new OnsetChecker(recordCache, null, metrics))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new OnsetChecker(recordCache, dynamicProperties, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Nested
        @DisplayName("Tests for checkTransaction")
        class CheckTransactionTest {
            @Test
            @DisplayName("Happy Path. Given a valid transaction, the checker should not throw any exception")
            void goodTransaction() {
                // Given a valid transaction
                final var tx = Transaction.newBuilder().signedTransactionBytes(CONTENT).build();

                // Then the checker should not throw any exception
                assertThatCode(() -> checker.checkTransaction(tx)).doesNotThrowAnyException();
                // And the deprecated transaction counter should not be incremented because we did
                // not use a deprecated transaction in the call
                verify(deprecationCounter, never()).increment();
            }

            @Test
            @DisplayName("Given a deprecated transaction, it is still good and succeeds")
            void deprecatedTransaction() {
                // Given a deprecated transaction (doesn't use signedTransactionBytes)
                final var tx = Transaction.newBuilder().bodyBytes(CONTENT).build();

                // Then the checker should not throw any exception
                assertThatCode(() -> checker.checkTransaction(tx)).doesNotThrowAnyException();
                // And the deprecated transaction counter should be incremented!
                verify(deprecationCounter).increment();
            }

            @Test
            @DisplayName("A transaction using both signed bytes and body bytes is invalid")
            void badTransactionWithSignedBytesAndBodyBytes() {
                // Given a transaction using both signed bytes and body bytes
                final var tx =
                        Transaction.newBuilder()
                                .signedTransactionBytes(CONTENT)
                                .bodyBytes(CONTENT)
                                .build();

                // When we check the transaction, then we find it is invalid
                assertThatThrownBy(() -> checker.checkTransaction(tx))
                        .isInstanceOf(PreCheckException.class)
                        .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION);
                // And the deprecated transaction counter is not incremented
                verify(deprecationCounter, never()).increment();
            }

            @Test
            @DisplayName("A transaction using both signed bytes and sig map is invalid")
            void badTransactionWithSignedBytesAndSigMap() {
                // Given a transaction using both signed bytes (new style) and sig map (deprecated style)
                final var signatureMap = SignatureMap.newBuilder().build();
                final var tx = Transaction.newBuilder()
                        .signedTransactionBytes(CONTENT)
                        .sigMap(signatureMap)
                        .build();

                // Then the checker should throw a PreCheckException
                assertThatThrownBy(() -> checker.checkTransaction(tx))
                        .isInstanceOf(PreCheckException.class)
                        .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION);
                // And the deprecated transaction counter is not incremented
                verify(deprecationCounter, never()).increment();
            }

            @Test
            @DisplayName("A transaction with no bytes at all fails")
            void badTransactionWithNoBytes() {
                // Given a transaction with no bytes at all
                final var tx = Transaction.newBuilder().build();

                // Then the checker should throw a PreCheckException
                assertThatThrownBy(() -> checker.checkTransaction(tx))
                        .isInstanceOf(PreCheckException.class)
                        .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION_BODY);
                // And the deprecated transaction counter is not incremented
                verify(deprecationCounter, never()).increment();
            }

            @Test
            @SuppressWarnings("ConstantConditions")
            @DisplayName("The checkTransaction method does not accept null arguments")
            void checkTransactionWithIllegalArguments() {
                assertThatThrownBy(() -> checker.checkTransaction(null))
                        .isInstanceOf(NullPointerException.class);
            }
        }

    @Nested
    @DisplayName("Tests for checkTransactionBody")
    class CheckTransactionBodyTest {

        @Test
        @SuppressWarnings("ConstantConditions")
        @DisplayName("The checkTransactionBody method does not accept null arguments")
        void testCheckTransactionBodyWithIllegalArguments() {
            assertThatThrownBy(() -> checker.checkTransactionBody(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Happy Path. Given a valid transaction body, the checker should not throw any exception")
        void testCheckTransactionBodySuccess() throws PreCheckException {
            // Given a valid transaction body
            final var txBody =
                    TransactionBody.newBuilder()
                            .transactionID(transactionID)
                            .transactionValidDuration(ONE_MINUTE)
                            .build();

            // When we check the transaction body
            final var result = checker.checkTransactionBody(txBody);

            // Then we find it is OK
            assertThat(result).isEqualTo(OK);
        }

        @Test
        @DisplayName("A transaction body must have a transaction ID")
        void testCheckTransactionBodyWithoutTransactionIDFails() {
            // Given a transaction body without a transaction ID
            final var txBody = TransactionBody.newBuilder().transactionValidDuration(ONE_MINUTE).build();

            // Then the checker should throw a PreCheckException
            assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION_ID);
        }

        @Test
        @DisplayName("A transaction ID with an alias as the payer is plausible")
        void testCheckTransactionBodyWithAliasAsPayer() throws PreCheckException {
            // Given a transaction ID with an alias as the payer
            final var payerId = AccountID.newBuilder().alias(Bytes.wrap("alias")).build();
            final var txBody = transactionBodyWithPayer(payerId);

            // When we check the transaction body
            final var result = checker.checkTransactionBody(txBody);

            // Then we find it is OK
            assertThat(result).isEqualTo(OK);
        }

        @Test
        @DisplayName("A transaction ID with an invalid account number fails")
        void testCheckTransactionBodyWithInvalidAccountNumFails() {
            // Given a transaction ID with an account number that is not valid (0 is not a valid number)
            final var payerId = AccountID.newBuilder().accountNum(0L).build();
            final var txBody = transactionBodyWithPayer(payerId);

            // Then the checker should throw a PreCheckException
            assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", PAYER_ACCOUNT_NOT_FOUND);
        }

        @Test
        @DisplayName("A transaction ID with an invalid realm number fails")
        void testCheckTransactionBodyWithInvalidRealmNumFails() {
            // Given a transaction ID with a negative realm (which is not valid)
            final var payerId = AccountID.newBuilder().accountNum(1L).realmNum(-1L).build();
            final var txBody = transactionBodyWithPayer(payerId);

            // Then the checker should throw a PreCheckException
            assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", PAYER_ACCOUNT_NOT_FOUND);
        }

        @Test
        @DisplayName("A transaction ID with an invalid shard number fails")
        void testCheckTransactionBodyWithInvalidShardNumFails() {
            // Given a transaction ID with a negative shard (which is not valid)
            final var payerId = AccountID.newBuilder().accountNum(1L).shardNum(-1L).build();
            final var txBody = transactionBodyWithPayer(payerId);

            // Then the checker should throw a PreCheckException
            assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", PAYER_ACCOUNT_NOT_FOUND);
        }

        @Test
        @DisplayName("A transaction body with too large of a memo fails")
        void testCheckTransactionBodyWithTooLargeMemoFails() {
            // Given a transaction body with a memo that is too large
            final var memo = randomString(MAX_MEMO_SIZE + 1);
            final var txBody = TransactionBody.newBuilder()
                    .transactionID(transactionID)
                    .transactionValidDuration(ONE_MINUTE)
                    .memo(memo)
                    .build();

            // Then the checker should throw a PreCheckException
            assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", MEMO_TOO_LONG);
        }

        // NOTE! This test will not be the case forever! We have an issue to fix
        // this, and allow zero bytes in the memo field.
        @Test
        @DisplayName("A transaction body with a zero byte in the memo fails")
        void testCheckTransactionBodyWithZeroByteMemoFails() {
            // Given a transaction body with a memo that contains a zero byte
            final var memo = "Hello World \0";
            final var txBody =
                    TransactionBody.newBuilder()
                            .transactionID(transactionID)
                            .transactionValidDuration(ONE_MINUTE)
                            .memo(memo)
                            .build();

            // Then the checker should throw a PreCheckException
            assertThatThrownBy(() -> checker.checkTransactionBody(txBody))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", INVALID_ZERO_BYTE_IN_STRING);
        }

        @Test
        @DisplayName("A transaction body that has already been submitted is rejected")
        void testCheckTransactionBodyDuplicateFails() throws PreCheckException {
            // Given a record cache that has already seen this transaction ID,
            // and a transaction body with that transaction ID
            when(recordCache.isReceiptPresent(transactionID)).thenReturn(true);
            final var txBody =
                    TransactionBody.newBuilder()
                            .transactionID(transactionID)
                            .transactionValidDuration(ONE_MINUTE)
                            .build();

            // When we check the transaction body
            final var result = checker.checkTransactionBody(txBody);

            // Then the record cache shows this transaction already exists
            // and the check fails
            assertThat(result).isEqualTo(DUPLICATE_TRANSACTION);
        }

        @Test
        @DisplayName("A transaction fee that is less than 0 is completely implausible")
        void testCheckTransactionBodyWithInvalidFeeFails() throws PreCheckException {
            // Given a transaction body with a negative fee
            final var txBody =
                    TransactionBody.newBuilder()
                            .transactionID(transactionID)
                            .transactionValidDuration(ONE_MINUTE)
                            .transactionFee(-1L)
                            .build();

            // When we check the transaction body
            final var result = checker.checkTransactionBody(txBody);

            // Then we find it has insufficient transaction fees.
            assertThat(result).isEqualTo(INSUFFICIENT_TX_FEE);
        }

        @Test
        @DisplayName("A transaction body with less duration than the minimum will simply fail")
        void testCheckTransactionBodyWithTooSmallDurationFails() throws PreCheckException {
            // Given a transaction body with a duration that is too small
            final var duration = Duration.newBuilder().seconds(MIN_DURATION - 1).build();
            final var txBody =
                    TransactionBody.newBuilder()
                            .transactionID(transactionID)
                            .transactionValidDuration(duration)
                            .build();

            // When we check the transaction body
            final var result = checker.checkTransactionBody(txBody);

            // Then we find it has an invalid transaction duration
            assertThat(result).isEqualTo(INVALID_TRANSACTION_DURATION);
        }

        @Test
        @DisplayName("A transaction body with a longer duration than the maximum will simply fail")
        void testCheckTransactionBodyWithTooLargeDurationFails() throws PreCheckException {
            // Given a transaction body with a duration that is too large
            final var duration = Duration.newBuilder().seconds(MAX_DURATION + 1).build();
            final var txBody =
                    TransactionBody.newBuilder()
                            .transactionID(transactionID)
                            .transactionValidDuration(duration)
                            .build();

            // When we check the transaction body
            final var result = checker.checkTransactionBody(txBody);

            // Then we find it has an invalid transaction duration
            assertThat(result).isEqualTo(INVALID_TRANSACTION_DURATION);
        }

        @Test
        void testCheckTransactionBodyWithExpiredTimeFails() throws PreCheckException {
            // Given a transaction body who's valid start time is in the past
            final var payerId = AccountID.newBuilder().accountNum(1L).build();
            final var past = Timestamp.newBuilder()
                    .seconds(Instant.now().getEpochSecond() - 100)
                    .build();

            transactionID =
                    TransactionID.newBuilder()
                            .accountID(payerId)
                            .transactionValidStart(past)
                            .build();

            final var txBody =
                    TransactionBody.newBuilder()
                            .transactionID(transactionID)
                            .transactionValidDuration(ONE_MINUTE)
                            .build();

            // When we check the transaction body
            final var result = checker.checkTransactionBody(txBody);

            // Then we find it has an expired transaction start time
            assertThat(result).isEqualTo(TRANSACTION_EXPIRED);
        }

        @Test
        void testCheckTransactionBodyWithFutureStartFails() throws PreCheckException {
            // Given a transaction body who's valid start time is in the future
            final var payerId = AccountID.newBuilder().accountNum(1L).build();
            final var future = Timestamp.newBuilder()
                    .seconds(Instant.now().getEpochSecond() + 100)
                    .build();

            transactionID =
                    TransactionID.newBuilder()
                            .accountID(payerId)
                            .transactionValidStart(future)
                            .build();

            final var txBody =
                    TransactionBody.newBuilder()
                            .transactionID(transactionID)
                            .transactionValidDuration(ONE_MINUTE)
                            .build();

            // When we check the transaction body
            final var result = checker.checkTransactionBody(txBody);

            // Then we find it has an invalid transaction start time
            assertThat(result).isEqualTo(INVALID_TRANSACTION_START);
        }

        private TransactionBody transactionBodyWithPayer(AccountID payerId) {
            final var now = Timestamp.newBuilder().seconds(Instant.now().getEpochSecond()).build();
            transactionID =
                    TransactionID.newBuilder()
                            .accountID(payerId)
                            .transactionValidStart(now)
                            .build();
            return TransactionBody.newBuilder()
                            .transactionID(transactionID)
                            .transactionValidDuration(ONE_MINUTE)
                            .build();
        }
    }
}
