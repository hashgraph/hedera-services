/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hedera.services.txns.diligence.DuplicateClassification.NODE_DUPLICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class AwareNodeDiligenceScreenTest {
    private static final long SUBMITTING_MEMBER = 2L;
    private static final String PRETEND_MEMO = "ignored";
    private static final Instant consensusTime = Instant.ofEpochSecond(1_234_567L);
    private static final AccountID aNodeAccount = IdUtils.asAccount("0.0.3");
    private static final AccountID bNodeAccount = IdUtils.asAccount("0.0.4");
    private static final AccountID payerAccountId = IdUtils.asAccount("0.0.5");
    private static final Duration validDuration =
            Duration.newBuilder().setSeconds(1_234_567L).build();

    private SignedTxnAccessor accessor;

    @Mock private TransactionContext txnCtx;
    @Mock private OptionValidator validator;
    @Mock private BackingStore<AccountID, HederaAccount> backingAccounts;

    @LoggingTarget private LogCaptor logCaptor;

    @LoggingSubject private AwareNodeDiligenceScreen subject;

    @BeforeEach
    void setUp() {
        subject = new AwareNodeDiligenceScreen(validator, txnCtx, backingAccounts);
    }

    @Test
    void flagsMissingNodeAccount() throws InvalidProtocolBufferException {
        givenHandleCtx(aNodeAccount, aNodeAccount);
        given(txnCtx.submittingSwirldsMember()).willReturn(SUBMITTING_MEMBER);
        given(backingAccounts.contains(aNodeAccount)).willReturn(false);

        assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
        verify(txnCtx).setStatus(INVALID_NODE_ACCOUNT);
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        Matchers.startsWith(
                                "Node 0.0.3 (member #2) submitted a txn w/ missing node account"
                                        + " 0.0.3")));
    }

    @Test
    void flagsNodeSubmittingTxnWithDiffNodeAccountId() throws InvalidProtocolBufferException {
        givenHandleCtx(bNodeAccount, aNodeAccount);
        given(txnCtx.submittingSwirldsMember()).willReturn(SUBMITTING_MEMBER);
        given(backingAccounts.contains(aNodeAccount)).willReturn(true);
        handleValidPayerAccount();

        assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
        verify(txnCtx).setStatus(INVALID_NODE_ACCOUNT);
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        Matchers.startsWith(
                                "Node 0.0.4 (member #2) submitted a txn meant for node account"
                                        + " 0.0.3")));
    }

    @Test
    void flagsInvalidPayerSig() throws InvalidProtocolBufferException {
        givenHandleCtx(aNodeAccount, aNodeAccount);
        given(backingAccounts.contains(aNodeAccount)).willReturn(true);
        given(txnCtx.isPayerSigKnownActive()).willReturn(false);
        handleValidPayerAccount();

        assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
        verify(txnCtx).setStatus(INVALID_PAYER_SIGNATURE);
    }

    @Test
    void flagsNodeDuplicate() throws InvalidProtocolBufferException {
        givenHandleCtx(aNodeAccount, aNodeAccount);
        given(backingAccounts.contains(aNodeAccount)).willReturn(true);
        given(txnCtx.isPayerSigKnownActive()).willReturn(true);
        handleValidPayerAccount();

        assertTrue(subject.nodeIgnoredDueDiligence(NODE_DUPLICATE));
        verify(txnCtx).setStatus(DUPLICATE_TRANSACTION);
    }

    @Test
    void flagsInvalidDuration() throws InvalidProtocolBufferException {
        givenHandleCtx(aNodeAccount, aNodeAccount);
        given(backingAccounts.contains(aNodeAccount)).willReturn(true);
        given(txnCtx.isPayerSigKnownActive()).willReturn(true);
        given(validator.isValidTxnDuration(validDuration.getSeconds())).willReturn(false);
        handleValidPayerAccount();

        assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
        verify(txnCtx).setStatus(INVALID_TRANSACTION_DURATION);
    }

    @Test
    void flagsInvalidChronology() throws InvalidProtocolBufferException {
        givenHandleCtx(aNodeAccount, aNodeAccount);
        given(backingAccounts.contains(aNodeAccount)).willReturn(true);
        given(txnCtx.isPayerSigKnownActive()).willReturn(true);
        given(validator.isValidTxnDuration(validDuration.getSeconds())).willReturn(true);
        given(validator.chronologyStatus(accessor, consensusTime)).willReturn(TRANSACTION_EXPIRED);
        given(txnCtx.consensusTime()).willReturn(consensusTime);
        handleValidPayerAccount();

        assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
        verify(txnCtx).setStatus(TRANSACTION_EXPIRED);
    }

    @Test
    void flagsInvalidMemo() throws InvalidProtocolBufferException {
        givenHandleCtx(aNodeAccount, aNodeAccount);
        given(backingAccounts.contains(aNodeAccount)).willReturn(true);
        given(txnCtx.isPayerSigKnownActive()).willReturn(true);
        given(validator.isValidTxnDuration(validDuration.getSeconds())).willReturn(true);
        given(validator.chronologyStatus(accessor, consensusTime)).willReturn(OK);
        given(txnCtx.consensusTime()).willReturn(consensusTime);
        given(validator.rawMemoCheck(accessor.getMemoUtf8Bytes(), accessor.memoHasZeroByte()))
                .willReturn(INVALID_ZERO_BYTE_IN_STRING);
        handleValidPayerAccount();

        assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
        verify(txnCtx).setStatus(INVALID_ZERO_BYTE_IN_STRING);
    }

    @Test
    void doesntFlagWithAllOk() throws InvalidProtocolBufferException {
        givenHandleCtx(aNodeAccount, aNodeAccount);
        given(backingAccounts.contains(aNodeAccount)).willReturn(true);
        given(txnCtx.isPayerSigKnownActive()).willReturn(true);
        given(validator.isValidTxnDuration(validDuration.getSeconds())).willReturn(true);
        given(validator.chronologyStatus(accessor, consensusTime)).willReturn(OK);
        given(txnCtx.consensusTime()).willReturn(consensusTime);
        given(validator.rawMemoCheck(accessor.getMemoUtf8Bytes(), accessor.memoHasZeroByte()))
                .willReturn(OK);
        handleValidPayerAccount();

        assertFalse(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
        verify(txnCtx, never()).setStatus(any());
    }

    @Test
    void payerAccountDoesntExist() throws InvalidProtocolBufferException {
        givenHandleCtx(aNodeAccount, aNodeAccount);
        given(backingAccounts.contains(aNodeAccount)).willReturn(true);

        assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));

        verify(txnCtx).setStatus(ACCOUNT_ID_DOES_NOT_EXIST);
    }

    @Test
    void payerAccountDeleted() throws InvalidProtocolBufferException {
        givenHandleCtx(aNodeAccount, aNodeAccount);
        given(backingAccounts.contains(aNodeAccount)).willReturn(true);
        final var payerAccountRef = mock(MerkleAccount.class);
        given(payerAccountRef.isDeleted()).willReturn(true);
        given(backingAccounts.getImmutableRef(payerAccountId)).willReturn(payerAccountRef);
        given(backingAccounts.contains(payerAccountId)).willReturn(true);

        assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));

        verify(txnCtx).setStatus(PAYER_ACCOUNT_DELETED);
    }

    private void givenHandleCtx(
            final AccountID submittingNodeAccount, final AccountID designatedNodeAccount)
            throws InvalidProtocolBufferException {
        given(txnCtx.submittingNodeAccount()).willReturn(submittingNodeAccount);
        accessor = accessorWith(designatedNodeAccount);
        given(txnCtx.accessor()).willReturn(accessor);
    }

    private SignedTxnAccessor accessorWith(final AccountID designatedNodeAccount)
            throws InvalidProtocolBufferException {
        final var transactionId = TransactionID.newBuilder().setAccountID(payerAccountId);

        final var bodyBytes =
                TransactionBody.newBuilder()
                        .setMemo(PRETEND_MEMO)
                        .setTransactionValidDuration(validDuration)
                        .setNodeAccountID(designatedNodeAccount)
                        .setTransactionID(transactionId)
                        .build()
                        .toByteString();
        final var signedTxn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(
                                SignedTransaction.newBuilder()
                                        .setBodyBytes(bodyBytes)
                                        .build()
                                        .toByteString())
                        .build();
        return SignedTxnAccessor.from(signedTxn.toByteArray(), signedTxn);
    }

    /**
     * Handle the valid case of having a payer account that is not deleted. Also make sure that the
     * backing accounts recognizes that the payer account exists
     */
    private void handleValidPayerAccount() {
        final var payerAccountRef = mock(MerkleAccount.class);
        given(payerAccountRef.isDeleted()).willReturn(false);
        given(backingAccounts.getImmutableRef(payerAccountId)).willReturn(payerAccountRef);
        given(backingAccounts.contains(payerAccountId)).willReturn(true);
    }
}
