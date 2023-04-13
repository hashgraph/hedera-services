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
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
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
    void preHandlesCryptoDeleteIfNoReceiverSigRequired() throws PreCheckException {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(transferAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(false);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var context = new PreHandleContext(store, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        assertEquals(payerKey, context.payerKey());
        basicMetaAssertions(context, 0);
    }

    @Test
    void preHandlesCryptoDeleteIfReceiverSigRequiredVanilla() throws PreCheckException {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(transferAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(true);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var context = new PreHandleContext(store, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(payerKey, context.payerKey());
    }

    @Test
    void doesntAddBothKeysAccountsSameAsPayerForCryptoDelete() throws PreCheckException {
        final var txn = deleteAccountTransaction(payer, payer);

        final var context = new PreHandleContext(store, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(payerKey, context.payerKey());
        assertIterableEquals(List.of(), context.requiredNonPayerKeys());
    }

    @Test
    void doesntAddTransferKeyIfAccountSameAsPayerForCryptoDelete() throws PreCheckException {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);

        final var txn = deleteAccountTransaction(deleteAccountId, payer);

        final var context = new PreHandleContext(store, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        assertEquals(payerKey, context.payerKey());
        basicMetaAssertions(context, 0);
        assertEquals(0, context.requiredNonPayerKeys().size());
    }

    @Test
    void doesntAddDeleteKeyIfAccountSameAsPayerForCryptoDelete() throws PreCheckException {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(transferAccount);
        given(transferAccount.getAccountKey()).willReturn(keyUsed);
        given(transferAccount.isReceiverSigRequired()).willReturn(true);

        final var txn = deleteAccountTransaction(payer, transferAccountId);

        final var context = new PreHandleContext(store, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(payerKey, context.payerKey());
    }

    @Test
    void failsWithResponseCodeIfAnyAccountMissingForCryptoDelete() throws PreCheckException {
        final var keyUsed = (JKey) payerKey;

        /* ------ payerAccount missing, so deleteAccount and transferAccount will not be added  ------ */
        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);
        given(accounts.get(EntityNumVirtualKey.fromLong(payerNum))).willReturn(null);
        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(transferAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);

        assertThrowsPreCheck(() -> new PreHandleContext(store, txn), INVALID_PAYER_ACCOUNT_ID);

        /* ------ deleteAccount missing, so transferAccount will not be added ------ */
        given(accounts.get(EntityNumVirtualKey.fromLong(payerNum))).willReturn(payerAccount);
        given(payerAccount.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(null);
        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(transferAccount);

        final var context2 = new PreHandleContext(store, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context2), INVALID_ACCOUNT_ID);

        /* ------ transferAccount missing ------ */
        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);
        given(accounts.get(EntityNumVirtualKey.fromLong(transferAccountNum))).willReturn(null);

        final var context3 = new PreHandleContext(store, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context3), INVALID_TRANSFER_ACCOUNT_ID);
    }

    @Test
    void doesntExecuteIfAccountIdIsDefaultInstance() throws PreCheckException {
        final var keyUsed = (JKey) payerKey;

        given(accounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum))).willReturn(deleteAccount);
        given(deleteAccount.getAccountKey()).willReturn(keyUsed);

        final var txn = deleteAccountTransaction(deleteAccountId, AccountID.DEFAULT);

        final var context = new PreHandleContext(store, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(payerKey, context.payerKey());
        assertIterableEquals(List.of(), context.requiredNonPayerKeys());
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
