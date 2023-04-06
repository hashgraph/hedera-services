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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteHandler;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class CryptoDeleteHandlerTest extends CryptoHandlerTestBase {
    private final AccountID deleteAccountId =
            AccountID.newBuilder().accountNum(3213).build();
    private final AccountID transferAccountId =
            AccountID.newBuilder().accountNum(32134).build();
    private final Long deleteAccountNum = deleteAccountId.accountNum();
    private final Long transferAccountNum = transferAccountId.accountNum();

    @Mock
    private MerkleAccount deleteAccount;

    @Mock
    private MerkleAccount transferAccount;

    private CryptoDeleteHandler subject = new CryptoDeleteHandler();

    @Test
    void preHandlesCryptoDeleteIfNoReceiverSigRequired() {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(transferAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(false);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        assertEquals(payerKey, context.getPayerKey());
        basicMetaAssertions(context, 1, false, OK);
        assertIterableEquals(List.of(keyUsed), context.getRequiredNonPayerKeys());
    }

    @Test
    void preHandlesCryptoDeleteIfReceiverSigRequiredVanilla() {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(transferAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(true);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        basicMetaAssertions(context, 2, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(List.of(keyUsed, keyUsed), context.getRequiredNonPayerKeys());
    }

    @Test
    void doesntAddBothKeysAccountsSameAsPayerForCryptoDelete() {
        final var txn = deleteAccountTransaction(payer, payer);

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        basicMetaAssertions(context, 0, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(List.of(), context.getRequiredNonPayerKeys());
    }

    @Test
    void doesntAddTransferKeyIfAccountSameAsPayerForCryptoDelete() {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);

        final var txn = deleteAccountTransaction(deleteAccountId, payer);

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        assertEquals(payerKey, context.getPayerKey());
        basicMetaAssertions(context, 1, false, OK);
        assertIterableEquals(List.of(keyUsed), context.getRequiredNonPayerKeys());
    }

    @Test
    void doesntAddDeleteKeyIfAccountSameAsPayerForCryptoDelete() {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(transferAccount);
        given(transferAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(true);

        final var txn = deleteAccountTransaction(payer, transferAccountId);

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        basicMetaAssertions(context, 1, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(List.of(keyUsed), context.getRequiredNonPayerKeys());
    }

    @Test
    void failsWithResponseCodeIfAnyAccountMissingForCryptoDelete() {
        final var keyUsed = (JKey) payerKey;

        /* ------ payerAccount missing, so deleteAccount and transferAccount will not be added  ------ */
        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);
        given(accounts.get(EntityNumVirtualKey.fromLong(payerNum))).willReturn(null);
        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(transferAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);

        final var context1 = new PreHandleContext(store, txn, payer);
        subject.preHandle(context1);
        basicMetaAssertions(context1, 0, true, INVALID_PAYER_ACCOUNT_ID);
        assertNull(context1.getPayerKey());
        assertIterableEquals(List.of(), context1.getRequiredNonPayerKeys());

        /* ------ deleteAccount missing, so transferAccount will not be added ------ */
        given(accounts.get(EntityNumVirtualKey.fromLong(payerNum))).willReturn(payerAccount);
        given(payerAccount.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(null);
        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(transferAccount);

        final var context2 = new PreHandleContext(store, txn, payer);
        subject.preHandle(context2);

        basicMetaAssertions(context2, 0, true, INVALID_ACCOUNT_ID);
        assertEquals(payerKey, context2.getPayerKey());
        assertIterableEquals(List.of(), context2.getRequiredNonPayerKeys());

        /* ------ transferAccount missing ------ */
        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(null);

        final var context3 = new PreHandleContext(store, txn, payer);
        subject.preHandle(context3);

        basicMetaAssertions(context3, 1, true, INVALID_TRANSFER_ACCOUNT_ID);
        assertEquals(payerKey, context3.getPayerKey());
        assertIterableEquals(List.of(keyUsed), context3.getRequiredNonPayerKeys());
    }

    @Test
    void doesntExecuteIfAccountIdIsDefaultInstance() {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);

        final var txn = deleteAccountTransaction(deleteAccountId, AccountID.DEFAULT);

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        basicMetaAssertions(context, 1, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(List.of(keyUsed), context.getRequiredNonPayerKeys());
    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle());
    }

    private TransactionBody deleteAccountTransaction(
            final AccountID deleteAccountId, final AccountID transferAccountId) {
        final var transactionID = TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
        final var deleteTxBody = CryptoDeleteTransactionBody.newBuilder()
                .deleteAccountID(deleteAccountId)
                .transferAccountID(transferAccountId);

        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoDelete(deleteTxBody)
                .build();
    }
}
