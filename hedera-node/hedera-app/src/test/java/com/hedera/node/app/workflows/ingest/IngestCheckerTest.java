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
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.estimatedFee;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.UncheckedSubmitBody;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.info.CurrentPlatformStatus;
import com.hedera.node.app.signature.SignatureExpander;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.DeduplicationCache;
import com.hedera.node.app.state.recordcache.DeduplicationCacheImpl;
import com.hedera.node.app.throttle.SynchronizedThrottleAccumulator;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.config.api.Configuration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
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

    private static final Fees DEFAULT_FEES = new Fees(100L, 20L, 3L);

    @Mock(strictness = LENIENT)
    CurrentPlatformStatus currentPlatformStatus;

    @Mock(strictness = LENIENT)
    TransactionChecker transactionChecker;

    @Mock(strictness = LENIENT)
    private SignatureExpander signatureExpander;

    @Mock(strictness = LENIENT)
    private SignatureVerifier signatureVerifier;

    @Mock(strictness = LENIENT)
    private SolvencyPreCheck solvencyPreCheck;

    @Mock(strictness = LENIENT)
    private TransactionDispatcher dispatcher;

    @Mock(strictness = LENIENT)
    private FeeManager feeManager;

    @Mock(strictness = LENIENT)
    private Authorizer authorizer;

    @Mock(strictness = LENIENT)
    private SynchronizedThrottleAccumulator synchronizedThrottleAccumulator;

    private DeduplicationCache deduplicationCache;

    private TransactionInfo transactionInfo;
    private TransactionBody txBody;
    private Transaction tx;

    private Configuration configuration;

    private IngestChecker subject;

    @BeforeEach
    void setUp() throws PreCheckException {
        setupStandardStates();
        when(currentPlatformStatus.get()).thenReturn(PlatformStatus.ACTIVE);

        configuration = new VersionedConfigImpl(HederaTestConfigBuilder.createConfig(), 1L);

        txBody = TransactionBody.newBuilder()
                .uncheckedSubmit(UncheckedSubmitBody.newBuilder().build())
                .transactionID(TransactionID.newBuilder()
                        .accountID(ALICE.accountID())
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(Instant.now().getEpochSecond())))
                .nodeAccountID(nodeSelfAccountId)
                .build();
        final var signedTx = SignedTransaction.newBuilder()
                .bodyBytes(asBytes(TransactionBody.PROTOBUF, txBody))
                .build();
        tx = Transaction.newBuilder()
                .signedTransactionBytes(asBytes(SignedTransaction.PROTOBUF, signedTx))
                .build();

        transactionInfo = new TransactionInfo(
                tx, txBody, MOCK_SIGNATURE_MAP, tx.signedTransactionBytes(), HederaFunctionality.UNCHECKED_SUBMIT);
        when(transactionChecker.check(tx)).thenReturn(transactionInfo);

        final var configProvider = HederaTestConfigBuilder.createConfigProvider();
        this.deduplicationCache = new DeduplicationCacheImpl(configProvider);

        when(solvencyPreCheck.getPayerAccount(any(), eq(ALICE.accountID()))).thenReturn(ALICE.account());
        when(dispatcher.dispatchComputeFees(any())).thenReturn(DEFAULT_FEES);

        subject = new IngestChecker(
                nodeSelfAccountId,
                currentPlatformStatus,
                transactionChecker,
                solvencyPreCheck,
                signatureExpander,
                signatureVerifier,
                deduplicationCache,
                dispatcher,
                feeManager,
                authorizer,
                synchronizedThrottleAccumulator);
    }

    @Nested
    @DisplayName("0. Node state pre-checks")
    class NodeTests {

        @Test
        @DisplayName("When the node is ok, no exception should be thrown")
        void testNodeStateSucceeds() {
            assertThatCode(() -> subject.checkNodeState()).doesNotThrowAnyException();
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
                // When we try to parse and check a transaction, it should fail because the platform is not active
                assertThatThrownBy(() -> subject.checkNodeState())
                        .isInstanceOf(PreCheckException.class)
                        .has(responseCode(PLATFORM_NOT_ACTIVE));
            }
        }
    }

    @Test
    @DisplayName("A wrong nodeId in transaction fails")
    void testWrongNodeIdFails() {
        // Given a transaction with an unknown node ID
        final var otherNodeSelfAccountId = AccountID.newBuilder()
                .accountNum(nodeSelfAccountId.accountNumOrElse(0L) + 1L)
                .build();

        subject = new IngestChecker(
                otherNodeSelfAccountId,
                currentPlatformStatus,
                transactionChecker,
                solvencyPreCheck,
                signatureExpander,
                signatureVerifier,
                deduplicationCache,
                dispatcher,
                feeManager,
                authorizer,
                synchronizedThrottleAccumulator);

        // Then the checker should throw a PreCheckException
        assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_NODE_ACCOUNT));
    }

    @Test
    @DisplayName("Run all checks successfully")
    void testRunAllChecksSuccessfully() throws Exception {
        // given
        final var expected = new TransactionInfo(
                tx, txBody, MOCK_SIGNATURE_MAP, tx.signedTransactionBytes(), HederaFunctionality.UNCHECKED_SUBMIT);
        final var verificationResultFuture = mock(SignatureVerificationFuture.class);
        final var verificationResult = mock(SignatureVerification.class);
        when(verificationResult.passed()).thenReturn(true);
        when(verificationResultFuture.get(anyLong(), any())).thenReturn(verificationResult);
        when(signatureVerifier.verify(any(), any()))
                .thenReturn(Map.of(ALICE.account().keyOrThrow(), verificationResultFuture));

        // when
        final var actual = subject.runAllChecks(state, tx, configuration);

        // then
        assertThat(actual).isEqualTo(expected);
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
            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(failureReason));
        }

        @Test
        @DisplayName("If some random exception is thrown from TransactionChecker, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given a WorkflowOnset that will throw a RuntimeException
            when(transactionChecker.check(any())).thenThrow(new RuntimeException("check exception"));

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("check exception");
        }
    }

    @Nested
    @DisplayName("3. Deduplication")
    class DuplicationTests {
        @Test
        @DisplayName("The second of two transactions with the same transaction ID should be rejected")
        void testThrottleFails() throws PreCheckException {
            // Given a deduplication cache, and a transaction with an ID already in the deduplication cache
            final var id = txBody.transactionIDOrThrow();
            deduplicationCache.add(id);
            // When the transaction is checked, then it throws a PreCheckException due to duplication
            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", DUPLICATE_TRANSACTION);
        }
    }

    @Nested
    @DisplayName("4. Check throttles")
    class ThrottleTests {

        @Test
        @DisplayName("When the transaction is throttled, the transaction should be rejected")
        void testThrottleFails() {
            // Given a throttle on CONSENSUS_CREATE_TOPIC transactions (i.e. it is time to throttle)
            when(synchronizedThrottleAccumulator.shouldThrottle(transactionInfo, state))
                    .thenReturn(true);

            // When the transaction is submitted
            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", BUSY);
        }

        @Test
        @DisplayName("If some random exception is thrown from HapiThrottling, the exception is bubbled up")
        void randomException() {
            // Given a HapiThrottling that will throw a RuntimeException
            when(synchronizedThrottleAccumulator.shouldThrottle(transactionInfo, state))
                    .thenThrow(new RuntimeException("shouldThrottle exception"));

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("shouldThrottle exception");
        }
    }

    @Nested
    @DisplayName("5.a Check account status")
    class PayerAccountStatusTests {

        public static Stream<Arguments> failureReasons() {
            return Stream.of(Arguments.of(INVALID_ACCOUNT_ID), Arguments.of(ACCOUNT_DELETED));
        }

        @ParameterizedTest(name = "Check of account status fails with error code {0}")
        @MethodSource("failureReasons")
        @DisplayName("If the status of the payer account is invalid, the transaction should be rejected")
        void payerAccountStatusFails(ResponseCodeEnum failureReason) throws PreCheckException {
            doThrow(new PreCheckException(failureReason)).when(solvencyPreCheck).getPayerAccount(any(), any());

            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(failureReason));
        }

        @Test
        @DisplayName("If some random exception is thrown from account status check, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given an IngestChecker that will throw a RuntimeException from checkPayerSignature
            doThrow(new RuntimeException("checkPayerAccountStatus exception"))
                    .when(solvencyPreCheck)
                    .getPayerAccount(any(), any());

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("checkPayerAccountStatus exception");
        }

        // NOTE: This should never happen in real life, but we need to code defensively for it anyway.
        @Test
        @DisplayName("No key for payer in state")
        void noKeyForPayer() throws PreCheckException {
            // The tx payer is ALICE. We remove her key from state
            final var account = ALICE.account().copyBuilder().key((Key) null).build();
            when(solvencyPreCheck.getPayerAccount(any(), eq(ALICE.accountID()))).thenReturn(account);

            // When the transaction is submitted, then the exception is thrown
            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(UNAUTHORIZED));
        }
    }

    @Nested
    @DisplayName("5.b Check payer solvency")
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
        void payerAccountStatusFails(ResponseCodeEnum failureReason)
                throws PreCheckException, ExecutionException, InterruptedException, TimeoutException {
            givenValidPayerSignature();
            doThrow(new InsufficientBalanceException(failureReason, 123L))
                    .when(solvencyPreCheck)
                    .checkSolvency(any(), any(), any());

            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .has(responseCode(failureReason))
                    .has(estimatedFee(123L));
        }

        @Test
        @DisplayName("If some random exception is thrown from checking solvency, the exception is bubbled up")
        void randomException() throws PreCheckException, ExecutionException, InterruptedException, TimeoutException {
            // Given an IngestChecker that will throw a RuntimeException from checkPayerSignature
            givenValidPayerSignature();
            doThrow(new RuntimeException("checkSolvency exception"))
                    .when(solvencyPreCheck)
                    .checkSolvency(any(), any(), any());

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("checkSolvency exception");
        }

        private void givenValidPayerSignature() throws ExecutionException, InterruptedException, TimeoutException {
            final var verificationResultFuture = mock(SignatureVerificationFuture.class);
            final var verificationResult = mock(SignatureVerification.class);
            when(verificationResult.passed()).thenReturn(true);
            when(verificationResultFuture.get(anyLong(), any())).thenReturn(verificationResult);
            when(signatureVerifier.verify(any(), any()))
                    .thenReturn(Map.of(ALICE.account().keyOrThrow(), verificationResultFuture));
        }
    }

    @Nested
    @DisplayName("6. Check payer's signature")
    class PayerSignatureTests {

        @Test
        @DisplayName("Payer signature is missing")
        void noPayerSignature() {
            // If the signature verifier's returned map doesn't contain an entry for ALICE, it means she didn't have a
            // signature in the signature map to begin with.
            when(signatureVerifier.verify(any(), any())).thenReturn(Map.of());

            // When the transaction is submitted, then the exception is thrown
            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_SIGNATURE));
        }

        @Test
        @DisplayName("Payer verification fails")
        void payerVerificationFails() throws Exception {
            final var verificationResultFuture = mock(SignatureVerificationFuture.class);
            final var verificationResult = mock(SignatureVerification.class);
            when(verificationResult.passed()).thenReturn(false);
            when(verificationResultFuture.get(anyLong(), any())).thenReturn(verificationResult);
            when(signatureVerifier.verify(any(), any()))
                    .thenReturn(Map.of(ALICE.account().keyOrThrow(), verificationResultFuture));

            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_SIGNATURE));
        }

        @Test
        @DisplayName("Unexpected verification exception")
        void randomException() throws Exception {
            // Given a verification result future that throws an exception
            final var verificationResultFuture = mock(SignatureVerificationFuture.class);
            doThrow(new RuntimeException("checkPayerSignature exception"))
                    .when(verificationResultFuture)
                    .get(anyLong(), any());
            when(signatureVerifier.verify(any(), any()))
                    .thenReturn(Map.of(ALICE.account().keyOrThrow(), verificationResultFuture));

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> subject.runAllChecks(state, tx, configuration))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("checkPayerSignature exception");
        }
    }
}
