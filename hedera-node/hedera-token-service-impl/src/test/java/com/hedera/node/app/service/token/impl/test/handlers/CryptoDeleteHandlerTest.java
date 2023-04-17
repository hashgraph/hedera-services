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
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CryptoDeleteHandlerTest extends CryptoHandlerTestBase {
    private CryptoDeleteHandler subject = new CryptoDeleteHandler();

    @BeforeEach
    public void setUp() {
        super.setUp();
        readableAccounts = emptyReadableAccountStateBuilder()
                .value(EntityNumVirtualKey.fromLong(accountNum), account)
                .value(EntityNumVirtualKey.fromLong(deleteAccountNum), deleteAccount)
                .value(EntityNumVirtualKey.fromLong(transferAccountNum), transferAccount)
                .build();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableStore = new ReadableAccountStore(readableStates);
    }

    @Test
    void preHandlesCryptoDeleteIfNoReceiverSigRequired() throws PreCheckException {
        given(transferAccount.receiverSigRequired()).willReturn(false);
        given(deleteAccount.key()).willReturn(accountKey);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        assertEquals(key, context.payerKey());
        basicMetaAssertions(context, 0);
    }

    @Test
    void preHandlesCryptoDeleteIfReceiverSigRequiredVanilla() throws PreCheckException {
        given(transferAccount.key()).willReturn(key);
        given(transferAccount.receiverSigRequired()).willReturn(true);
        given(deleteAccount.key()).willReturn(accountKey);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(key, context.payerKey());
    }

    @Test
    void doesntAddBothKeysAccountsSameAsPayerForCryptoDelete() throws PreCheckException {
        final var txn = deleteAccountTransaction(id, id);

        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(key, context.payerKey());
        assertIterableEquals(List.of(), context.requiredNonPayerKeys());
    }

    @Test
    void doesntAddTransferKeyIfAccountSameAsPayerForCryptoDelete() throws PreCheckException {
        final var txn = deleteAccountTransaction(deleteAccountId, id);

        readableAccounts = emptyReadableAccountStateBuilder()
                .value(EntityNumVirtualKey.fromLong(accountNum), account)
                .value(EntityNumVirtualKey.fromLong(deleteAccountNum), deleteAccount)
                .value(EntityNumVirtualKey.fromLong(transferAccountNum), transferAccount)
                .build();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableStore = new ReadableAccountStore(readableStates);
        given(deleteAccount.key()).willReturn(accountKey);

        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        assertEquals(key, context.payerKey());
        basicMetaAssertions(context, 0);
        assertEquals(0, context.requiredNonPayerKeys().size());
    }

    @Test
    void doesntAddDeleteKeyIfAccountSameAsPayerForCryptoDelete() throws PreCheckException {
        final var txn = deleteAccountTransaction(id, transferAccountId);

        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(key, context.payerKey());
    }

    @Test
    void failsWithResponseCodeIfAnyAccountMissingForCryptoDelete() throws PreCheckException {
        /* ------ payerAccount missing, so deleteAccount and transferAccount will not be added  ------ */
        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);
        readableAccounts = emptyReadableAccountStateBuilder().build();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableStore = new ReadableAccountStore(readableStates);
        given(deleteAccount.key()).willReturn(accountKey);

        assertThrowsPreCheck(() -> new PreHandleContext(readableStore, txn), INVALID_PAYER_ACCOUNT_ID);

        /* ------ deleteAccount missing, so transferAccount will not be added ------ */
        readableAccounts = emptyReadableAccountStateBuilder()
                .value(EntityNumVirtualKey.fromLong(accountNum), account)
                .value(EntityNumVirtualKey.fromLong(transferAccountNum), transferAccount)
                .build();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableStore = new ReadableAccountStore(readableStates);

        final var context2 = new PreHandleContext(readableStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context2), INVALID_ACCOUNT_ID);

        /* ------ transferAccount missing ------ */
        readableAccounts = emptyReadableAccountStateBuilder()
                .value(EntityNumVirtualKey.fromLong(accountNum), account)
                .value(EntityNumVirtualKey.fromLong(deleteAccountNum), deleteAccount)
                .build();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableStore = new ReadableAccountStore(readableStates);

        final var context3 = new PreHandleContext(readableStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context3), INVALID_TRANSFER_ACCOUNT_ID);
    }

    @Test
    void doesntExecuteIfAccountIdIsDefaultInstance() throws PreCheckException {
        given(deleteAccount.key()).willReturn(key);

        final var txn = deleteAccountTransaction(deleteAccountId, AccountID.DEFAULT);

        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(key, context.payerKey());
        assertIterableEquals(List.of(), context.requiredNonPayerKeys());
    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle());
    }

    private TransactionBody deleteAccountTransaction(
            final AccountID deleteAccountId, final AccountID transferAccountId) {
        final var transactionID = TransactionID.newBuilder().accountID(id).transactionValidStart(consensusTimestamp);
        final var deleteTxBody = CryptoDeleteTransactionBody.newBuilder()
                .deleteAccountID(deleteAccountId)
                .transferAccountID(transferAccountId);

        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoDelete(deleteTxBody)
                .build();
    }
}
