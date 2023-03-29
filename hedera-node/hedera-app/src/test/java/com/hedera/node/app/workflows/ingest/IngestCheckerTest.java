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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.UncheckedSubmitBody;
import com.hedera.node.app.service.mono.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.node.app.service.mono.txns.submission.SolvencyPrecheck;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestCheckerTest {
    private static final SignatureMap MOCK_SIGNATURE_MAP =
            SignatureMap.newBuilder().build();

    private static final EntityNum MOCK_PAYER_NUM = EntityNum.fromLong(666L);
    private static final com.hedera.hapi.node.base.AccountID MOCK_PAYER_ID = toPbj(MOCK_PAYER_NUM.toGrpcAccountId());
    private static final AccountID MOCK_NODE_ACCOUNT_ID =
            AccountID.newBuilder().setAccountNum(3L).build();
    private static final TransactionBody MOCK_TXN = TransactionBody.newBuilder()
            .uncheckedSubmit(UncheckedSubmitBody.newBuilder().build())
            .transactionID(TransactionID.newBuilder().accountID(MOCK_PAYER_ID).build())
            .build();
    private static final Transaction MOCK_TRANSACTION = Transaction.newBuilder()
            .signedTransactionBytes(SignedTransaction.newBuilder()
                    .bodyBytes(MOCK_TXN.uncheckedSubmitOrThrow().transactionBytes())
                    .build()
                    .bodyBytes())
            .build();

    @Mock
    private HederaState state;

    @Mock
    private SignaturePreparer signaturePreparer;

    @Mock
    private SolvencyPrecheck solvencyPrecheck;

    private IngestChecker subject;

    @BeforeEach
    void setUp() {
        subject = new IngestChecker(MOCK_NODE_ACCOUNT_ID.getAccountNum(), solvencyPrecheck, signaturePreparer);
    }

    @Test
    void throwsOnInvalidPayerSignatureStatus() {
        given(signaturePreparer.syncGetPayerSigStatus(any())).willReturn(INVALID_SIGNATURE);

        assertFailsWithPrecheck(
                INVALID_SIGNATURE,
                () -> subject.checkPayerSignature(state, MOCK_TRANSACTION, MOCK_SIGNATURE_MAP, MOCK_PAYER_ID));
    }

    @Test
    void happyPathWithValidPayerSignatureStatus() {
        given(signaturePreparer.syncGetPayerSigStatus(any())).willReturn(OK);

        assertDoesNotThrow(
                () -> subject.checkPayerSignature(state, MOCK_TRANSACTION, MOCK_SIGNATURE_MAP, MOCK_PAYER_ID));
    }

    @Test
    void checksSolvencyWithMonoHelperHappyPath() {
        given(solvencyPrecheck.payerAccountStatus(MOCK_PAYER_NUM)).willReturn(fromPbj(OK));
        given(solvencyPrecheck.solvencyOfVerifiedPayer(any(), eq(false)))
                .willReturn(new TxnValidityAndFeeReq(fromPbj(OK), 123L));

        assertDoesNotThrow(() -> subject.checkSolvency(MOCK_TRANSACTION));
    }

    @Test
    void propagatesBadPayerAccountViaPreCheckException() {
        given(solvencyPrecheck.payerAccountStatus(MOCK_PAYER_NUM)).willReturn(fromPbj(PAYER_ACCOUNT_DELETED));

        assertFailsWithPrecheck(PAYER_ACCOUNT_DELETED, () -> subject.checkSolvency(MOCK_TRANSACTION));
    }

    @Test
    void propagatesInsolventPayerAccountViaInsufficientBalanceException() {
        final ArgumentCaptor<SignedTxnAccessor> captor = forClass(SignedTxnAccessor.class);
        given(solvencyPrecheck.payerAccountStatus(MOCK_PAYER_NUM)).willReturn(fromPbj(OK));
        final var solvencySummary = new TxnValidityAndFeeReq(INSUFFICIENT_TX_FEE, 123L);
        given(solvencyPrecheck.solvencyOfVerifiedPayer(captor.capture(), eq(false)))
                .willReturn(solvencySummary);

        assertFailsWithInsufficientBalance(
                ResponseCodeEnum.INSUFFICIENT_TX_FEE, 123L, () -> subject.checkSolvency(MOCK_TRANSACTION));
        final var accessor = captor.getValue();
        assertEquals(MOCK_PAYER_ID, toPbj(accessor.getPayer()));
    }

    private static void assertFailsWithPrecheck(final ResponseCodeEnum expected, final ExceptionalRunnable runnable) {
        final var e = assertThrows(PreCheckException.class, runnable::run);
        assertEquals(expected, e.responseCode());
    }

    private static void assertFailsWithInsufficientBalance(
            final ResponseCodeEnum expected, final long expectedFee, final ExceptionalRunnable runnable) {
        final var e = assertThrows(InsufficientBalanceException.class, runnable::run);
        assertEquals(expected, e.responseCode());
        assertEquals(expectedFee, e.getEstimatedFee());
    }

    private interface ExceptionalRunnable {
        void run() throws PreCheckException;
    }
}
