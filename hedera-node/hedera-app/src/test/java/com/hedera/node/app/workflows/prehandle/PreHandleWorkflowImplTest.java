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

package com.hedera.node.app.workflows.prehandle;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.fixtures.workflows.BadTransactionScenarios;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.fixtures.ImmediateExecutorService;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.ReceiptCache;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
final class PreHandleWorkflowImplTest extends AppTestBase implements BadTransactionScenarios {
    /**
     * The executor to use for running the workflow. For these unit tests, we will use an immediate executor,
     * which will execute the
     */
    private final ExecutorService executor = new ImmediateExecutorService();

    /**
     * We use a mocked dispatcher, so it is easy to fake out interaction between the workflow and some
     * "hypothetical" transaction handlers.
     */
    @Mock
    private TransactionDispatcher dispatcher;

    /**
     * We use a mocked transaction checker, so it is easy to fake out the success or failure of the
     * transaction checker.
     */
    @Mock
    private TransactionChecker transactionChecker;

    /**
     * We use a mocked signature verifier, so it is easy to fake out the success or failure of signature
     * verification.
     */
    @Mock
    private SignatureVerifier signatureVerifier;

    /** We use a real functional store factory with our standard test data set. Needed by the workflow. */
    private ReadableStoreFactory storeFactory;

    /** The workflow under test. */
    private PreHandleWorkflow workflow;

    @BeforeEach
    void setUp() {
        final var fakeHederaState = new FakeHederaState();
        fakeHederaState.addService(TokenService.NAME,
                new MapReadableKVState<>("ACCOUNTS", Map.of(EntityNumVirtualKey.fromLong(ALICE.accountID().accountNumOrThrow()), ALICE.account())),
                new MapReadableKVState<String, Long>("ALIASES", Collections.emptyMap()));

        storeFactory = new ReadableStoreFactory(fakeHederaState);
        final var receiptCache = fakeHederaState.getReceiptCache();
        workflow = new PreHandleWorkflowImpl(executor, dispatcher, transactionChecker, signatureVerifier, receiptCache);
    }

    /**
     * Null arguments are not permitted to the constructor.
     */
    @Test
    @DisplayName("Null constructor args throw NPE")
    @SuppressWarnings("DataFlowIssue") // Suppress the warning about null args
    void nullConstructorArgsTest() {
        final var receiptCache = mock(ReceiptCache.class);
        assertThatThrownBy(
                () -> new PreHandleWorkflowImpl(null, dispatcher, transactionChecker, signatureVerifier, receiptCache))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                () -> new PreHandleWorkflowImpl(executor, null, transactionChecker, signatureVerifier, receiptCache))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                () -> new PreHandleWorkflowImpl(executor, dispatcher, null, signatureVerifier, receiptCache))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                () -> new PreHandleWorkflowImpl(executor, dispatcher, transactionChecker, null, receiptCache))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                () -> new PreHandleWorkflowImpl(executor, dispatcher, transactionChecker, signatureVerifier, null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Null arguments are not permitted to the preHandle method
     */
    @Test
    @DisplayName("Null pre-handle args throw NPE")
    @SuppressWarnings("DataFlowIssue") // Suppress the warning about null args
    void nullPreHandleArgsTest() {
        final List<Transaction> list = List.of(new SwirldTransaction(new byte[10]));
        final var itr = list.iterator();
        final var creator = NODE_1.nodeAccountID();
        assertThatThrownBy(() -> workflow.preHandle(null, creator, itr))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> workflow.preHandle(storeFactory, null, itr))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> workflow.preHandle(storeFactory, creator, null))
                .isInstanceOf(NullPointerException.class);
    }

    // TODO Write test to validate that we skip system transactions

    @Nested
    @DisplayName("Due-diligence tests")
    final class DueDiligenceTests implements BadTransactionScenarios {

        /**
         * A dishonest node may send, in an event, a transaction that cannot be parsed. It might just
         * be random bytes. Or no bytes. Or too many bytes. In all of those cases, we should immediately
         * terminate with a {@link PreHandleResult} that as a status, a payer for the node that sent the
         * transaction, and nothing else.
         *
         * <p>Or, after successfully parsing the transaction from protobuf bytes, we perform a whole set of syntactic
         * checks on the transaction using the {@link TransactionChecker}. We don't need to verify every possible
         * bad transaction here (since the tests for {@link TransactionChecker} do that). If **any** failure happens
         * due to a syntactic check, we should immediately terminate with a {@link PreHandleResult} that has the status
         * of the failure and the payer should be node (as it failed due-diligence checks).
         *
         * <p>Both cases look the same to the handler.
         */
        @Test
        @DisplayName("Fail pre-handle with an attempt to parse invalid protobuf bytes")
        void preHandleBadBytes() throws PreCheckException {
            // Given a transaction that has bad bytes (and therefore fails to parse)
            final Transaction platformTx = new SwirldTransaction(randomByteArray(123));
            when(transactionChecker.parse(any(Bytes.class))).thenThrow(new PreCheckException(INVALID_TRANSACTION));

            // When we try to pre-handle the transaction
            workflow.preHandle(storeFactory, NODE_1.nodeAccountID(), List.of(platformTx).iterator());

            // Then we get a failure with INVALID_TRANSACTION
            final PreHandleResult result = platformTx.getMetadata();
            assertThat(result.status()).isEqualTo(INVALID_TRANSACTION);
            assertThat(result.payer()).isEqualTo(NODE_1.nodeAccountID());
        }

        /**
         * It may be that while performing syntactics check we encounter some random {@link Throwable}. If that happens,
         * then the {@link PreHandleResult} will have a status of {@link ResponseCodeEnum#UNKNOWN} and the payer will be
         * the node. But we'll end up trying this again later during the handle flow.
         */
        @Test
        @DisplayName("Fail pre-handle with failed syntactic check with an unknown exception")
        void preHandleFailedSyntacticCheckWithUnknownException() throws PreCheckException {
            // Given a transaction that fails due-diligence checks for some random throwable
            final Transaction platformTx = new SwirldTransaction(randomByteArray(123));
            when(transactionChecker.parseAndCheck(any(Bytes.class))).thenThrow(new AssertionError("Random"));

            // When we pre-handle the transaction
            workflow.preHandle(storeFactory, NODE_1.nodeAccountID(), List.of(platformTx).iterator());

            // The throwable is caught, and we get an UNKNOWN status code
            final PreHandleResult result = platformTx.getMetadata();
            assertThat(result.status()).isEqualTo(UNKNOWN);
            assertThat(result.payer()).isEqualTo(NODE_1.nodeAccountID());
        }

        /**
         * If a dishonest node sends the same transaction two or more times, then the node must detect this as a
         * duplicate transaction and charge the node for the duplicate transaction. We don't need payer information
         * for the duplicate. The first transaction will have the payer as contained in the {@link TransactionID},
         * but the duplicate will have the node as the payer in the {@link PreHandleResult}.
         */
        @Test
        @DisplayName("Fail pre-handle the second time because the node sent a duplicate transaction")
        void preHandleDuplicateTransaction() throws PreCheckException {
            // Given a dishonest node that sends the same valid transaction twice
            final var txInfo = scenario()
                    .withPayer(ALICE.accountID())
                    .txInfo();
            final var txBytes = asByteArray(txInfo.transaction());
            final Transaction platformTx = new SwirldTransaction(txBytes);
            final Transaction duplicatePlatformTx = new SwirldTransaction(txBytes);
            when(transactionChecker.parseAndCheck(any(Bytes.class))).thenReturn(txInfo);
            when(signatureVerifier.verify(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));

            // When we pre-handle the transactions
            workflow.preHandle(storeFactory, NODE_1.nodeAccountID(), List.of(platformTx, duplicatePlatformTx).iterator());

            // Then the first transaction is fine
            final PreHandleResult result1 = platformTx.getMetadata();
            assertThat(result1.status()).isEqualTo(OK);
            assertThat(result1.payer()).isEqualTo(ALICE.accountID());

            // But the second transaction is a duplicate
            final PreHandleResult result2 = duplicatePlatformTx.getMetadata();
            assertThat(result2.status()).isEqualTo(DUPLICATE_TRANSACTION);
            assertThat(result2.payer()).isEqualTo(NODE_1.nodeAccountID());
        }

        /**
         * It may be that when the transaction is pre-handled, it refers to an account that does not yet exist.
         * This may happen because the transaction is bad, or it may happen because we do not yet have an account
         * object (maybe another in-flight transaction will create it). But every node as part of its due-diligence
         * has to verify the payer signature on the transaction prior to submitting the transaction to the network.
         * So if the payer account does not exist, then the node failed due-diligence and should pay for the
         * transaction.
         */
        @Test
        @DisplayName("Fail pre-handle because the payer account cannot be found")
        void preHandlePayerAccountNotFound() throws PreCheckException {
            // Given a transactionID that refers to an account that does not exist
//            final var txBody = with(goodDefaultBody())
//                    .withPayer(AccountID.newBuilder().accountNum(9999).build())
//                    .transactionBody();
//            final var tx = with(txBody).build();
//            final Transaction platformTx = new SwirldTransaction(asByteArray(tx));
//            final var txInfo = new TransactionInfo(tx, txBody, mock(SignatureMap.class), Bytes.EMPTY, CRYPTO_TRANSFER);
//            when(transactionChecker.parseAndCheck(any(Bytes.class))).thenReturn(txInfo);
//            when(signatureVerifier.verify(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));
//
//            // When we pre-handle the transaction
//            workflow.preHandle(storeFactory, NODE_1.nodeAccountID(), List.of(platformTx).iterator());
//
//            // Then the transaction fails and the node is the payer
//            final PreHandleResult result1 = platformTx.getMetadata();
//            assertThat(result1.status()).isEqualTo(INVALID_PAYER_ACCOUNT_ID);
//            assertThat(result1.payer()).isEqualTo(NODE_1.nodeAccountID());
        }

        /**
         * The transaction submitted by the user may simply be missing the payer signature. Maybe the payer in
         * the transaction is a valid account ID, and maybe the account exists, but maybe the payer never signed
         * the transaction. In that case, the node failed due diligence again and should pay for the transaction.
         * True, a transaction that would put the proper key on the account may be in-flight, but we never should
         * have gotten to this point if the node had performed proper due-diligence.
         */
        @Test
        @DisplayName("Payer signature is invalid")
        void payerSignatureInvalid() {
        }
    }

    /**
     * After passing all due-diligence checks we gather signatures for signature verification.
     */
    @Nested
    @DisplayName("Transaction Handler pre-handle tests")
    final class TransactionHandlerPreHandleTests {
        /**
         * It may be that the user has submitted two or more transactions to the network by submitting two or
         * more transactions with the same {@link TransactionID} to two or more nodes. In that case, both transactions
         * will be paid for by the payer, but only the first will second, the others will be duplicates and will
         * fail with a {@link ResponseCodeEnum#DUPLICATE_TRANSACTION}.
         */
        @Test
        @DisplayName("Payer submitted duplicate transactions")
        void payerDuplicateTransaction() {
        }

        /**
         * The transaction has a valid payer (although we still may not know if the signature check succeeded). So now
         * we need to perform the other three calls per transaction handler. First, we need to perform semantic checks
         * on the transaction.
         */
        @Test
        @DisplayName("Pre-handle semantic checks fail with PreCheckException")
        void preHandleSemanticChecksFail() {
        }

        /**
         * The transaction has a valid payer (although we still may not know if the signature check succeeded), and
         * semantic checks passed. Now we will gather signatures. During this process, a {@link PreCheckException}
         * may be thrown.
         */
        @Test
        @DisplayName("Pre-handle gathering of non-payer signatures fail with PreCheckException")
        void preHandleGatherSignaturesFail() {
        }

        /**
         * The transaction has a valid payer (although we still may not know if the signature check succeeded), and
         * semantic checks passed, and we have gathered signatures. Now we will warm the cache. This should not throw
         * a {@link PreCheckException}. If any random {@link RuntimeException} is thrown, then the transaction will
         * fail with a {@link ResponseCodeEnum#UNKNOWN}.
         */
        @Test
        @DisplayName("Pre-handle warming fails with RuntimeException")
        void preHandleWarmingFails() {
        }

        /**
         * All the above lifecycle calls succeed, and we gather signatures, but then find out (asynchronously) that
         * the signature verification fails. We'll find this during the handle phase. We just need to know that
         * if the signature verification fails, the {@link PreHandleResult} will reflect that.
         */
        @Test
        @DisplayName("Signature verification fails for non-payer signatures")
        void nonPayerSignatureInvalid() {
        }
    }

    /**
     * Tests the normal happy path. A transaction is valid, the payer account exists, and all verification tests
     * pass. 🎉
     */
    @Nested
    @DisplayName("Happy Path Tests")
    final class HappyPathTests {
        @Test
        @DisplayName("Happy path")
        void happyPath() {
        }
    }
}
