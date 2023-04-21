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

package com.hedera.node.app.workflows.ingest;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BUSY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.estimatedFee;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.UncheckedSubmitBody;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.solvency.SolvencyPreCheck;
import com.hedera.node.app.spi.info.CurrentPlatformStatus;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.swirlds.common.system.PlatformStatus;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestCheckerTest extends AppTestBase {
    private static final SignatureMap MOCK_SIGNATURE_MAP =
            SignatureMap.newBuilder().build();

    private static final EntityNum MOCK_PAYER_NUM = EntityNum.fromLong(666L);
    private static final AccountID MOCK_PAYER_ID =
            AccountID.newBuilder().accountNum(MOCK_PAYER_NUM.longValue()).build();
    private static final AccountID MOCK_NODE_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(3L).build();

    @Mock
    private HederaState state;

    @Mock(strictness = LENIENT)
    NodeInfo nodeInfo;

    @Mock(strictness = LENIENT)
    CurrentPlatformStatus currentPlatformStatus;

    @Mock(strictness = LENIENT)
    TransactionChecker transactionChecker;

    @Mock(strictness = LENIENT)
    ThrottleAccumulator throttleAccumulator;

    @Mock(strictness = LENIENT)
    private SignaturePreparer signaturePreparer;

    @Mock(strictness = LENIENT)
    private SolvencyPreCheck solvencyPreCheck;

    private TransactionBody txBody;
    private Transaction tx;

    private IngestChecker subject;

    @BeforeEach
    void setUp() throws PreCheckException {
        when(currentPlatformStatus.get()).thenReturn(PlatformStatus.ACTIVE);

        txBody = TransactionBody.newBuilder()
                .uncheckedSubmit(UncheckedSubmitBody.newBuilder().build())
                .transactionID(
                        TransactionID.newBuilder().accountID(MOCK_PAYER_ID).build())
                .nodeAccountID(MOCK_NODE_ACCOUNT_ID)
                .build();
        final var signedTx = SignedTransaction.newBuilder()
                .bodyBytes(asBytes(TransactionBody.PROTOBUF, txBody))
                .build();
        tx = Transaction.newBuilder()
                .signedTransactionBytes(asBytes(SignedTransaction.PROTOBUF, signedTx))
                .build();

        final var transactionInfo =
                new TransactionInfo(tx, txBody, MOCK_SIGNATURE_MAP, HederaFunctionality.UNCHECKED_SUBMIT);
        when(transactionChecker.check(tx)).thenReturn(transactionInfo);

        subject = new IngestChecker(
                MOCK_NODE_ACCOUNT_ID,
                nodeInfo,
                currentPlatformStatus,
                transactionChecker,
                throttleAccumulator,
                solvencyPreCheck,
                signaturePreparer);
    }

    @Nested
    @DisplayName("0. Node state pre-checks")
    class NodeTests {

        @Test
        @DisplayName("When the node is ok, no exception should be thrown")
        void testNodeStateSucceeds() {
            assertThatCode(() -> subject.checkNodeState()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("When the node is zero weight, the transaction should be rejected")
        void testCheckNodeStateWithZeroWeightFails() {
            // Given a node that IS zero weight
            when(nodeInfo.isSelfZeroWeight()).thenReturn(true);

            assertThatThrownBy(() -> subject.checkNodeState())
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_NODE_ACCOUNT));
        }

        @ParameterizedTest
        @EnumSource(PlatformStatus.class)
        @DisplayName("When the platform is not ACTIVE, the transaction should be rejected")
        void testParseAndCheckWithInactivePlatformFails(final PlatformStatus status) {
            // Since the enum source is going over all states, and the ACTIVE state is
            // actually good, I need to skip that one.
            if (status != PlatformStatus.ACTIVE) {
                // Given a platform that is not ACTIVE
                when(currentPlatformStatus.get()).thenReturn(status);

                assertThatThrownBy(() -> subject.checkNodeState())
                        .isInstanceOf(PreCheckException.class)
                        .has(responseCode(PLATFORM_NOT_ACTIVE));
            }
        }
    }

    @Test
    @DisplayName("Run all checks successfully")
    void testRunAllChecksSuccessfully() throws PreCheckException {
        // given
        final var expected = new TransactionInfo(tx, txBody, MOCK_SIGNATURE_MAP, HederaFunctionality.UNCHECKED_SUBMIT);

        // when
        final var actual = subject.runAllChecks(state, tx);

        // then
        Assertions.assertThat(actual).isEqualTo(expected);
    }

    @Nested
    @DisplayName("1. Check the syntax")
    class SyntaxCheckTests {
        /**
         * It is not necessary to test all the possible failure reasons, just a few to make sure that
         * the checker is passing the failure reason to the response.
         * @return a stream of arguments with the failure reason
         */
        public static Stream<Arguments> failureReasons() {
            return Stream.of(Arguments.of(INVALID_TRANSACTION), Arguments.of(INVALID_TRANSACTION_BODY));
        }

        @ParameterizedTest(name = "TransactionChecker fails with error code {0}")
        @MethodSource("failureReasons")
        @DisplayName("If the transaction fails TransactionChecker, a failure response is returned with the right error")
        void onsetFailsWithPreCheckException(ResponseCodeEnum failureReason) throws PreCheckException {
            // Given a TransactionChecker that will throw a PreCheckException with the given failure reason
            when(transactionChecker.check(any())).thenThrow(new PreCheckException(failureReason));

            // When the transaction is checked
            assertThatThrownBy(() -> subject.runAllChecks(state, tx))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(failureReason));
        }

        @Test
        @DisplayName("If some random exception is thrown from TransactionChecker, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given a WorkflowOnset that will throw a RuntimeException
            when(transactionChecker.check(any())).thenThrow(new RuntimeException("check exception"));

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> subject.runAllChecks(state, tx))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("check exception");
        }
    }

    @Nested
    @DisplayName("2. Check throttles")
    class ThrottleTests {
        @Test
        @DisplayName("When the transaction is throttled, the transaction should be rejected")
        void testThrottleFails() {
            // Given a throttle on CONSENSUS_CREATE_TOPIC transactions (i.e. it is time to throttle)
            when(throttleAccumulator.shouldThrottle(txBody)).thenReturn(true);

            // When the transaction is submitted
            assertThatThrownBy(() -> subject.runAllChecks(state, tx))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", BUSY);
        }

        @Test
        @DisplayName("If some random exception is thrown from ThrottleAccumulator, the exception is bubbled up")
        void randomException() {
            // Given a ThrottleAccumulator that will throw a RuntimeException
            when(throttleAccumulator.shouldThrottle(txBody))
                    .thenThrow(new RuntimeException("shouldThrottle exception"));

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> subject.runAllChecks(state, tx))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("shouldThrottle exception");
        }
    }

    @Nested
    @DisplayName("3. Check payer's signature")
    class PayerSignatureTests {
        public static Stream<Arguments> failureReasons() {
            return Stream.of(
                    Arguments.of(INVALID_SIGNATURE),
                    Arguments.of(KEY_PREFIX_MISMATCH),
                    Arguments.of(INVALID_ACCOUNT_ID));
        }

        @ParameterizedTest(name = "SignatureCheck fails with error code {0}")
        @MethodSource("failureReasons")
        @DisplayName("If the payer signature is invalid, the transaction should be rejected")
        void payerSignatureFails(ResponseCodeEnum failureReason) throws PreCheckException {
            doThrow(new PreCheckException(failureReason))
                    .when(signaturePreparer)
                    .syncGetPayerSigStatus(any());

            assertThatThrownBy(() -> subject.runAllChecks(state, tx))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(failureReason));
        }

        @Test
        @DisplayName("If some random exception is thrown from checking signatures, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given an IngestChecker that will throw a RuntimeException from checkPayerSignature
            doThrow(new RuntimeException("checkPayerSignature exception"))
                    .when(signaturePreparer)
                    .syncGetPayerSigStatus(any());

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> subject.runAllChecks(state, tx))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("checkPayerSignature exception");
        }
    }

    @Nested
    @DisplayName("4.a Check account status")
    class PayerAccountStatusTests {

        public static Stream<Arguments> failureReasons() {
            return Stream.of(Arguments.of(INVALID_ACCOUNT_ID), Arguments.of(ACCOUNT_DELETED));
        }

        @ParameterizedTest(name = "Check of account status fails with error code {0}")
        @MethodSource("failureReasons")
        @DisplayName("If the status of the payer account is invalid, the transaction should be rejected")
        void payerAccountStatusFails(ResponseCodeEnum failureReason) throws PreCheckException {
            doThrow(new PreCheckException(failureReason)).when(solvencyPreCheck).checkPayerAccountStatus(any(), any());

            assertThatThrownBy(() -> subject.runAllChecks(state, tx))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(failureReason));
        }

        @Test
        @DisplayName("If some random exception is thrown from account status check, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given an IngestChecker that will throw a RuntimeException from checkPayerSignature
            doThrow(new RuntimeException("checkPayerAccountStatus exception"))
                    .when(solvencyPreCheck)
                    .checkPayerAccountStatus(any(), any());

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> subject.runAllChecks(state, tx))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("checkPayerAccountStatus exception");
        }
    }

    @Nested
    @DisplayName("4.b Check payer solvency")
    class PayerBalanceTests {

        public static Stream<Arguments> failureReasons() {
            return Stream.of(
                    Arguments.of(INSUFFICIENT_TX_FEE),
                    Arguments.of(INSUFFICIENT_PAYER_BALANCE),
                    Arguments.of(FAIL_FEE));
        }

        @ParameterizedTest(name = "Check of payer's balance fails with error code {0}")
        @MethodSource("failureReasons")
        @DisplayName("If the payer has insufficient funds, the transaction should be rejected")
        void payerAccountStatusFails(ResponseCodeEnum failureReason) throws PreCheckException {
            doThrow(new InsufficientBalanceException(failureReason, 123L))
                    .when(solvencyPreCheck)
                    .checkSolvencyOfVerifiedPayer(any(), any());

            assertThatThrownBy(() -> subject.runAllChecks(state, tx))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .has(responseCode(failureReason))
                    .has(estimatedFee(123L));
        }

        @Test
        @DisplayName("If some random exception is thrown from checking solvency, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given an IngestChecker that will throw a RuntimeException from checkPayerSignature
            doThrow(new RuntimeException("checkSolvency exception"))
                    .when(solvencyPreCheck)
                    .checkSolvencyOfVerifiedPayer(any(), any());

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> subject.runAllChecks(state, tx))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("checkSolvency exception");
        }
    }
}
