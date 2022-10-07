/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.crypto;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(LogCaptureExtension.class)
class CryptoDeleteTransitionLogicTest {
    private final AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
    private final AccountID target = AccountID.newBuilder().setAccountNum(9_999L).build();
    private final boolean withKnownTreasury = true;

    private HederaLedger ledger;
    private TransactionBody cryptoDeleteTxn;
    private SigImpactHistorian sigImpactHistorian;
    private TransactionContext txnCtx;
    private SignedTxnAccessor accessor;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private CryptoDeleteTransitionLogic subject;

    @BeforeEach
    void setup() {
        txnCtx = mock(TransactionContext.class);
        ledger = mock(HederaLedger.class);
        accessor = mock(SignedTxnAccessor.class);
        sigImpactHistorian = mock(SigImpactHistorian.class);

        given(ledger.allTokenBalancesVanish(target)).willReturn(true);

        subject = new CryptoDeleteTransitionLogic(ledger, sigImpactHistorian, txnCtx);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(cryptoDeleteTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void rejectsTargetAsBeneficiary() {
        givenValidTxnCtx(target);

        // expect:
        assertEquals(
                TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT,
                subject.semanticCheck().apply(cryptoDeleteTxn));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(cryptoDeleteTxn));
    }

    @Test
    void translatesMissingAccount() {
        givenValidTxnCtx();
        willThrow(MissingEntityException.class).given(ledger).delete(any(), any());

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
    }

    @Test
    void translatesDeletedAccount() {
        givenValidTxnCtx();
        willThrow(DeletedAccountException.class).given(ledger).delete(any(), any());

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(ACCOUNT_DELETED);
    }

    @Test
    void followsHappyPath() {
        givenValidTxnCtx();

        // when:
        subject.doStateTransition();

        // then:
        verify(ledger).delete(target, payer);
        verify(txnCtx).recordBeneficiaryOfDeleted(target.getAccountNum(), payer.getAccountNum());
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(target.getAccountNum());
    }

    @Test
    void rejectsDetachedAccountAsTarget() {
        // setup:
        givenValidTxnCtx();
        given(ledger.isDetached(target)).willReturn(true);

        // when:
        subject.doStateTransition();

        // when:
        verify(ledger, never()).delete(target, payer);
        verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    }

    @Test
    void rejectsDetachedAccountAsReceiver() {
        // setup:
        var receiver = IdUtils.asAccount("0.0.7676");

        givenValidTxnCtx(receiver);
        given(ledger.isDetached(receiver)).willReturn(true);

        // when:
        subject.doStateTransition();

        // when:
        verify(ledger, never()).delete(target, receiver);
        verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    }

    @Test
    void capturesFailInvalid() {
        // setup:
        givenValidTxnCtx();
        given(ledger.isKnownTreasury(target)).willThrow(RuntimeException.class);
        // and:
        var desired = "Avoidable exception! java.lang.RuntimeException: null";

        // when:
        subject.doStateTransition();

        // when:
        verify(txnCtx).setStatus(FAIL_INVALID);
        assertThat(logCaptor.warnLogs(), contains(desired));
    }

    @Test
    void rejectsDeletionOfKnownTreasury() {
        // setup:
        givenValidTxnCtx();
        given(ledger.isKnownTreasury(target)).willReturn(withKnownTreasury);

        // when:
        subject.doStateTransition();

        // when:
        verify(ledger, never()).delete(target, payer);
        verify(txnCtx).setStatus(ACCOUNT_IS_TREASURY);
    }

    @Test
    void rejectsIfTargetHasNonZeroTokenBalances() {
        givenValidTxnCtx();
        given(ledger.allTokenBalancesVanish(target)).willReturn(false);

        // when:
        subject.doStateTransition();

        // then:
        verify(ledger, never()).delete(target, payer);
        verify(txnCtx).setStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
    }

    @Test
    void rejectsIfTargetMissing() {
        givenDeleteTxnMissingTarget();

        // when:
        ResponseCodeEnum validity = subject.semanticCheck().apply(cryptoDeleteTxn);

        // then:
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, validity);
    }

    @Test
    void rejectsIfTransferMissing() {
        givenDeleteTxnMissingTransfer();

        // when:
        ResponseCodeEnum validity = subject.semanticCheck().apply(cryptoDeleteTxn);

        // then:
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, validity);
    }

    private void givenValidTxnCtx() {
        givenValidTxnCtx(payer);
    }

    private void givenValidTxnCtx(AccountID transfer) {
        cryptoDeleteTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDelete(
                                CryptoDeleteTransactionBody.newBuilder()
                                        .setDeleteAccountID(target)
                                        .setTransferAccountID(transfer)
                                        .build())
                        .build();
        given(accessor.getTxn()).willReturn(cryptoDeleteTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }

    private void givenDeleteTxnMissingTarget() {
        cryptoDeleteTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDelete(
                                CryptoDeleteTransactionBody.newBuilder()
                                        .setTransferAccountID(asAccount("0.0.1234"))
                                        .build())
                        .build();
    }

    private void givenDeleteTxnMissingTransfer() {
        cryptoDeleteTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDelete(
                                CryptoDeleteTransactionBody.newBuilder()
                                        .setDeleteAccountID(asAccount("0.0.1234"))
                                        .build())
                        .build();
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payer)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
                .build();
    }
}
