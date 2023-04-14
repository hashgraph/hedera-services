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

import static com.hedera.hapi.node.base.ResponseCodeEnum.BUSY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.utility.AutoCloseableWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.function.Supplier;
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
class IngestWorkflowImplTest extends AppTestBase {
    /** The workflow to be tested. */
    private IngestWorkflowImpl workflow;

    /**
     * The request. For testing purposes, the bytes in this buffer are not important. The {@link TransactionChecker} is
     * stubbed to always return a valid parsed object.
     */
    private Bytes requestBuffer;

    /** The buffer to write responses into. */
    private final BufferedData responseBuffer = BufferedData.allocate(1024 * 6);

    /** The actual bytes inside the requestBuffer */
    private byte[] requestBytes;

    /** The request transaction body */
    private TransactionBody transactionBody;

    // The following fields are all mocked dependencies of the workflow.
    @Mock(strictness = LENIENT)
    Account account;

    @Mock(strictness = LENIENT)
    NodeInfo nodeInfo;

    @Mock(strictness = LENIENT)
    HederaState state;

    @Mock(strictness = LENIENT)
    ReadableAccountStore accountStore;

    @Mock(strictness = LENIENT)
    CurrentPlatformStatus currentPlatformStatus;

    @Mock(strictness = LENIENT)
    Supplier<AutoCloseableWrapper<HederaState>> stateAccessor;

    @Mock(strictness = LENIENT)
    TransactionChecker transactionChecker;

    @Mock(strictness = LENIENT)
    IngestChecker checker;

    @Mock(strictness = LENIENT)
    ThrottleAccumulator throttleAccumulator;

    @Mock(strictness = LENIENT)
    SubmissionManager submissionManager;

    @Mock(strictness = LENIENT)
    Metrics metrics;

    @Mock(strictness = LENIENT)
    Counter countSubmitted;

    @BeforeEach
    void setup() throws PreCheckException {
        // The request buffer, with basically random bytes
        requestBytes = randomBytes(10);
        requestBuffer = Bytes.wrap(requestBytes);
        transactionBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(AccountID.newBuilder().accountNum(1001).build())
                        .build())
                .build();

        // The account will have the following state
        // TODO Anything here??

        // The platform status is always active
        when(currentPlatformStatus.get()).thenReturn(PlatformStatus.ACTIVE);
        // The state is going to always be empty
        when(stateAccessor.get()).thenReturn(new AutoCloseableWrapper<>(state, () -> {}));
        // The account store will always return the same mocked account.
        // TODO I don't like this, because we should test flows where the account doesn't exist,
        // etc.
        when(accountStore.getAccountById(any())).thenReturn(account);
        // TODO Mock out the metrics to return objects we can inspect later
        when(metrics.getOrCreate(any())).thenReturn(countSubmitted);

        // Mock out the onset to always return a valid parsed object
        final var onsetResult = new TransactionInfo(
                Transaction.newBuilder().body(transactionBody).build(),
                transactionBody,
                SignatureMap.newBuilder().build(),
                HederaFunctionality.CONSENSUS_CREATE_TOPIC);
        when(transactionChecker.parseAndCheck(requestBuffer)).thenReturn(onsetResult);

        // Create the workflow we are going to test with
        workflow = new IngestWorkflowImpl(
                nodeInfo,
                currentPlatformStatus,
                stateAccessor,
                transactionChecker,
                checker,
                throttleAccumulator,
                submissionManager,
                metrics);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithInvalidArguments() {
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        null,
                        currentPlatformStatus,
                        stateAccessor,
                        transactionChecker,
                        checker,
                        throttleAccumulator,
                        submissionManager,
                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        null,
                        stateAccessor,
                        transactionChecker,
                        checker,
                        throttleAccumulator,
                        submissionManager,
                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        null,
                        transactionChecker,
                        checker,
                        throttleAccumulator,
                        submissionManager,
                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        null,
                        checker,
                        throttleAccumulator,
                        submissionManager,
                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        transactionChecker,
                        null,
                        throttleAccumulator,
                        submissionManager,
                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        transactionChecker,
                        checker,
                        null,
                        submissionManager,
                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        transactionChecker,
                        checker,
                        throttleAccumulator,
                        null,
                        metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IngestWorkflowImpl(
                        nodeInfo,
                        currentPlatformStatus,
                        stateAccessor,
                        transactionChecker,
                        checker,
                        throttleAccumulator,
                        submissionManager,
                        null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("When everything goes right, the transaction should be submitted")
    void testSuccess() throws PreCheckException, IOException {
        // When the transaction is submitted
        workflow.submitTransaction(requestBuffer, responseBuffer);

        // Then we get a response that is OK
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(OK);
        // The cost *MUST* be zero, it is only non-zero for insufficient balance errors
        assertThat(response.cost()).isZero();
        // And that the transaction and its bytes were actually passed to the submission manager
        verify(submissionManager).submit(transactionBody, requestBytes);
        // And that the metrics for counting submitted transactions was incremented
        verify(countSubmitted).increment();
    }

    @Nested
    @DisplayName("0. Node state pre-checks")
    class NodeTests {
        @Test
        @DisplayName("When the node is zero stake, the transaction should be rejected")
        void testParseAndCheckWithZeroStakeFails() throws IOException, PreCheckException {
            // Given a node that IS zero stake
            when(nodeInfo.isSelfZeroStake()).thenReturn(true);

            // When the transaction is submitted
            workflow.submitTransaction(requestBuffer, responseBuffer);

            // Then the request is rejected with INVALID_NODE_ACCOUNT
            final TransactionResponse response = parseResponse(responseBuffer);
            assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(INVALID_NODE_ACCOUNT);
            // The cost *MUST* be zero, it is only non-zero for insufficient balance errors
            assertThat(response.cost()).isZero();
            // And the transaction was not submitted
            verify(submissionManager, never()).submit(any(), any());
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }

        @ParameterizedTest
        @EnumSource(PlatformStatus.class)
        @DisplayName("When the platform is not ACTIVE, the transaction should be rejected (except for ACTIVE)")
        void testParseAndCheckWithInactivePlatformFails(final PlatformStatus status)
                throws IOException, PreCheckException {
            // Since the enum source is going over all states, and the ACTIVE state is
            // actually good, I need to skip that one.
            if (status != PlatformStatus.ACTIVE) {
                // Given a platform that is not ACTIVE
                when(currentPlatformStatus.get()).thenReturn(status);

                // When the transaction is submitted
                workflow.submitTransaction(requestBuffer, responseBuffer);

                // Then the response fails with PLATFORM_NOT_ACTIVE
                final TransactionResponse response = parseResponse(responseBuffer);
                assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(PLATFORM_NOT_ACTIVE);
                // The cost *MUST* be zero, it is only non-zero for insufficient balance errors
                assertThat(response.cost()).isZero();
                // And the transaction is not submitted to the platform
                verify(submissionManager, never()).submit(any(), any());
                // And the metrics for counting submitted transactions was not incremented
                verify(countSubmitted, never()).increment();
            }
        }
    }

    @Nested
    @DisplayName("1. Parse the TransactionBody and check the syntax")
    class OnsetTests {
        /**
         * It is not necessary to test all the possible failure reasons, just a few to make sure that
         * the workflow is passing the failure reason to the response.
         * @return a stream of arguments with the failure reason
         */
        public static Stream<Arguments> failureReasons() {
            return Stream.of(
                    Arguments.of(INVALID_TRANSACTION),
                    Arguments.of(INVALID_TRANSACTION_BODY),
                    Arguments.of(TRANSACTION_HAS_UNKNOWN_FIELDS),
                    Arguments.of(TRANSACTION_OVERSIZE));
        }

        @ParameterizedTest(name = "WorkflowOnset fails with error code {0}")
        @MethodSource("failureReasons")
        @DisplayName("If the transaction fails WorkflowOnset, a failure response is returned with the right error")
        void onsetFailsWithPreCheckException(ResponseCodeEnum failureReason) throws PreCheckException, IOException {
            // Given a WorkflowOnset that will throw a PreCheckException with the given failure reason
            when(transactionChecker.parseAndCheck(any())).thenThrow(new PreCheckException(failureReason));

            // When the transaction is submitted
            workflow.submitTransaction(requestBuffer, responseBuffer);

            // Then the response fails with the given failure reason
            final TransactionResponse response = parseResponse(responseBuffer);
            assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(failureReason);
            // The cost *MUST* be zero, it is only non-zero for insufficient balance errors
            assertThat(response.cost()).isZero();
            // And the transaction is not submitted to the platform
            verify(submissionManager, never()).submit(any(), any());
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }

        @Test
        @DisplayName("If some random exception is thrown from WorkflowOnset, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given a WorkflowOnset that will throw a RuntimeException
            when(transactionChecker.parseAndCheck(any())).thenThrow(new RuntimeException("parseAndCheck exception"));

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> workflow.submitTransaction(requestBuffer, responseBuffer))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("parseAndCheck exception");
            // And the transaction is not submitted to the platform
            verify(submissionManager, never()).submit(any(), any());
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }
    }

    @Nested
    @DisplayName("2. Check throttles")
    class ThrottleTests {
        @Test
        @DisplayName("When the transaction is throttled, the transaction should be rejected")
        void testThrottleFails() throws PreCheckException, IOException {
            // Given a throttle on CONSENSUS_CREATE_TOPIC transactions (i.e. it is time to throttle)
            when(throttleAccumulator.shouldThrottle(transactionBody)).thenReturn(true);

            // When the transaction is submitted
            workflow.submitTransaction(requestBuffer, responseBuffer);

            // Then the response fails with BUSY
            final TransactionResponse response = parseResponse(responseBuffer);
            assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(BUSY);
            // The cost *MUST* be zero, it is only non-zero for insufficient balance errors
            assertThat(response.cost()).isZero();
            // And the transaction is not submitted to the platform
            verify(submissionManager, never()).submit(any(), any());
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }

        @Test
        @DisplayName("If some random exception is thrown from ThrottleAccumulator, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given a ThrottleAccumulator that will throw a RuntimeException
            when(throttleAccumulator.shouldThrottle(transactionBody))
                    .thenThrow(new RuntimeException("shouldThrottle exception"));

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> workflow.submitTransaction(requestBuffer, responseBuffer))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("shouldThrottle exception");
            // And the transaction is not submitted to the platform
            verify(submissionManager, never()).submit(any(), any());
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }
    }

    @Nested
    @DisplayName("3. Check semantics")
    class SemanticTests {

        @Test
        @DisplayName("If some random exception is thrown from checking semantics, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given an IngestChecker that will throw a RuntimeException from checkTransactionSemantics
            doThrow(new RuntimeException("checkTransactionSemantics exception"))
                    .when(checker)
                    .checkTransactionSemantics(any(), eq(HederaFunctionality.CONSENSUS_CREATE_TOPIC));

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> workflow.submitTransaction(requestBuffer, responseBuffer))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("checkTransactionSemantics exception");
            // And the transaction is not submitted to the platform
            verify(submissionManager, never()).submit(any(), any());
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }
    }

    @Nested
    @DisplayName("4. Get payer account")
    class PayerAccountTests {
        @Test
        @DisplayName("If the payer account is not found, the transaction should be rejected")
        void noSuchPayerAccount() throws PreCheckException, IOException {
            // Given an account store that is not able to find the account
            when(accountStore.getAccountById(any())).thenReturn(null);
            doThrow(new PreCheckException(PAYER_ACCOUNT_NOT_FOUND))
                    .when(checker)
                    .checkPayerSignature(any(), any(), any(), any());

            // When we submit a transaction
            workflow.submitTransaction(requestBuffer, responseBuffer);

            // Then the response will indicate the payer account was not found
            final TransactionResponse response = parseResponse(responseBuffer);
            assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(PAYER_ACCOUNT_NOT_FOUND);
            assertThat(response.cost()).isZero();
            // And the transaction is not submitted to the platform
            verify(submissionManager, never()).submit(any(), any());
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }
    }

    @Nested
    @DisplayName("5. Check payer's signature")
    class PayerSignatureTests {

        @Test
        @DisplayName("If the payer signature is invalid, the transaction should be rejected")
        void payerSignatureFails() throws PreCheckException, IOException {
            // Given a checker that will fail the payer signature check
            doThrow(new PreCheckException(INVALID_PAYER_SIGNATURE))
                    .when(checker)
                    .checkPayerSignature(any(), any(), any(), any());

            // When we submit a transaction
            workflow.submitTransaction(requestBuffer, responseBuffer);

            // Then the response will indicate the payer signature was invalid
            final TransactionResponse response = parseResponse(responseBuffer);
            assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(INVALID_PAYER_SIGNATURE);
            // And the cost will be zero
            assertThat(response.cost()).isZero();
            // And the transaction is not submitted to the platform
            verify(submissionManager, never()).submit(any(), any());
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }

        @Test
        @DisplayName("If some random exception is thrown from checking signatures, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given an IngestChecker that will throw a RuntimeException from checkPayerSignature
            doThrow(new RuntimeException("checkPayerSignature exception"))
                    .when(checker)
                    .checkPayerSignature(any(), any(), any(), any());

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> workflow.submitTransaction(requestBuffer, responseBuffer))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("checkPayerSignature exception");
            // And the transaction is not submitted to the platform
            verify(submissionManager, never()).submit(any(), any());
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }
    }

    @Nested
    @DisplayName("6. Check account balance")
    class PayerBalanceTests {

        @Test
        @DisplayName("If the payer account has insufficient funds, the transaction should be rejected")
        void insolvent() throws PreCheckException, IOException {
            // Given a checker that will fail the payer solvency check
            doThrow(new InsufficientBalanceException(INSUFFICIENT_ACCOUNT_BALANCE, 42L))
                    .when(checker)
                    .checkSolvency(any());

            // When we submit a transaction
            workflow.submitTransaction(requestBuffer, responseBuffer);

            // Then the response will indicate the payer account had insufficient funds
            final TransactionResponse response = parseResponse(responseBuffer);
            assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(INSUFFICIENT_ACCOUNT_BALANCE);
            // And the cost will be the amount of the estimated fee
            assertThat(response.cost()).isEqualTo(42L);
            // And the transaction is not submitted to the platform
            verify(submissionManager, never()).submit(any(), any());
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }

        @Test
        @DisplayName("If some random exception is thrown from checking solvency, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given an IngestChecker that will throw a RuntimeException from checkSolvency
            doThrow(new RuntimeException("checkSolvency exception"))
                    .when(checker)
                    .checkSolvency(any());

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> workflow.submitTransaction(requestBuffer, responseBuffer))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("checkSolvency exception");
            // And the transaction is not submitted to the platform
            verify(submissionManager, never()).submit(any(), any());
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }
    }

    @Nested
    @DisplayName("7. Submit to platform")
    class PlatformSubmissionTests {

        @Test
        @DisplayName("If the platform fails to onConsensusRound the transaction, the transaction should be rejected")
        void testSubmitFails() throws PreCheckException, IOException {
            // Given a SubmissionManager that will fail the submit
            doThrow(new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED))
                    .when(submissionManager)
                    .submit(any(), any());

            // When we submit a transaction
            workflow.submitTransaction(requestBuffer, responseBuffer);

            // Then the response will indicate the platform rejected the transaction
            final TransactionResponse response = parseResponse(responseBuffer);
            assertThat(response.nodeTransactionPrecheckCode()).isEqualTo(PLATFORM_TRANSACTION_NOT_CREATED);
            // And the cost will be zero
            assertThat(response.cost()).isZero();
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }

        @Test
        @DisplayName("If some random exception is thrown from submitting to the platform, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given a SubmissionManager that will throw a RuntimeException from submit
            doThrow(new RuntimeException("submit exception"))
                    .when(submissionManager)
                    .submit(any(), any());

            // When the transaction is submitted, then the exception is bubbled up
            assertThatThrownBy(() -> workflow.submitTransaction(requestBuffer, responseBuffer))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("submit exception");
            // And the metrics for counting submitted transactions was not incremented
            verify(countSubmitted, never()).increment();
        }
    }

    private static TransactionResponse parseResponse(@NonNull final BufferedData responseBuffer) throws IOException {
        responseBuffer.flip();
        return TransactionResponse.PROTOBUF.parse(responseBuffer);
    }
}
