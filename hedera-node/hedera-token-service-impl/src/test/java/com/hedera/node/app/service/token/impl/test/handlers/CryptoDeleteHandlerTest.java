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
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
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

    @Mock private Account deleteAccount;

    @Mock private Account transferAccount;

    private CryptoDeleteHandler subject = new CryptoDeleteHandler();

    @Test
    void preHandlesCryptoDeleteIfNoReceiverSigRequired() {
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum)))
                .willReturn(deleteAccount);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(transferAccountNum)))
                .willReturn(transferAccount);
        given(deleteAccount.key()).willReturn(accountKey);
        given(transferAccount.key()).willReturn(accountKey);
        given(transferAccount.receiverSigRequired()).willReturn(false);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var context = new PreHandleContext(readableStore, txn, id);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        assertEquals(accountHederaKey, context.getPayerKey());
        basicMetaAssertions(context, 1, false, OK);
        assertIterableEquals(List.of(accountKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void preHandlesCryptoDeleteIfReceiverSigRequiredVanilla() {
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum)))
                .willReturn(deleteAccount);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(transferAccountNum)))
                .willReturn(transferAccount);
        given(deleteAccount.key()).willReturn(accountKey);
        given(transferAccount.key()).willReturn(accountKey);
        given(transferAccount.receiverSigRequired()).willReturn(true);

        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

        final var context = new PreHandleContext(readableStore, txn, id);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        basicMetaAssertions(context, 2, false, OK);
        assertEquals(accountHederaKey, context.getPayerKey());
        assertIterableEquals(List.of(accountKey, accountKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void doesntAddBothKeysAccountsSameAsPayerForCryptoDelete() {
        final var txn = deleteAccountTransaction(id, id);

        final var context = new PreHandleContext(readableStore, txn, id);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        basicMetaAssertions(context, 0, false, OK);
        assertEquals(accountHederaKey, context.getPayerKey());
        assertIterableEquals(List.of(), context.getRequiredNonPayerKeys());
    }

    @Test
    void doesntAddTransferKeyIfAccountSameAsPayerForCryptoDelete() {
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum)))
                .willReturn(deleteAccount);
        given(deleteAccount.key()).willReturn(accountKey);

        final var txn = deleteAccountTransaction(deleteAccountId, id);

        final var context = new PreHandleContext(readableStore, txn, id);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        assertEquals(accountHederaKey, context.getPayerKey());
        basicMetaAssertions(context, 1, false, OK);
        assertIterableEquals(List.of(accountKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void doesntAddDeleteKeyIfAccountSameAsPayerForCryptoDelete() {
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(transferAccountNum)))
                .willReturn(transferAccount);
        given(transferAccount.key()).willReturn(accountKey);
        given(transferAccount.receiverSigRequired()).willReturn(true);

        final var txn = deleteAccountTransaction(id, transferAccountId);

        final var context = new PreHandleContext(readableStore, txn, id);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        basicMetaAssertions(context, 1, false, OK);
        assertEquals(accountHederaKey, context.getPayerKey());
        assertIterableEquals(List.of(accountKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void failsWithResponseCodeIfAnyAccountMissingForCryptoDelete() {
        /* ------ payerAccount missing, so deleteAccount and transferAccount will not be added  ------ */
        final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(null);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum)))
                .willReturn(deleteAccount);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(transferAccountNum)))
                .willReturn(transferAccount);
        given(deleteAccount.key()).willReturn(accountKey);

        final var context1 = new PreHandleContext(readableStore, txn, id);
        subject.preHandle(context1);
        basicMetaAssertions(context1, 0, true, INVALID_PAYER_ACCOUNT_ID);
        assertNull(context1.getPayerKey());
        assertIterableEquals(List.of(), context1.getRequiredNonPayerKeys());

        /* ------ deleteAccount missing, so transferAccount will not be added ------ */
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.key()).willReturn(accountKey);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum)))
                .willReturn(null);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(transferAccountNum)))
                .willReturn(transferAccount);

        final var context2 = new PreHandleContext(readableStore, txn, id);
        subject.preHandle(context2);

        basicMetaAssertions(context2, 0, true, INVALID_ACCOUNT_ID);
        assertEquals(accountHederaKey, context2.getPayerKey());
        assertIterableEquals(List.of(), context2.getRequiredNonPayerKeys());

        /* ------ transferAccount missing ------ */
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum)))
                .willReturn(deleteAccount);
        given(deleteAccount.key()).willReturn(accountKey);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(transferAccountNum)))
                .willReturn(null);

        final var context3 = new PreHandleContext(readableStore, txn, id);
        subject.preHandle(context3);

        basicMetaAssertions(context3, 1, true, INVALID_TRANSFER_ACCOUNT_ID);
        assertEquals(accountHederaKey, context3.getPayerKey());
        assertIterableEquals(List.of(accountKey), context3.getRequiredNonPayerKeys());
    }

    @Test
    void doesntExecuteIfAccountIdIsDefaultInstance() {
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(deleteAccountNum)))
                .willReturn(deleteAccount);
        given(deleteAccount.key()).willReturn(accountKey);

        final var txn = deleteAccountTransaction(deleteAccountId, AccountID.DEFAULT);

        final var context = new PreHandleContext(readableStore, txn, id);
        subject.preHandle(context);

        assertEquals(txn, context.getTxn());
        basicMetaAssertions(context, 1, false, OK);
        assertEquals(accountHederaKey, context.getPayerKey());
        assertIterableEquals(List.of(accountKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle());
    }

    private TransactionBody deleteAccountTransaction(
            final AccountID deleteAccountId, final AccountID transferAccountId) {
        final var transactionID =
                TransactionID.newBuilder().accountID(id).transactionValidStart(consensusTimestamp);
        final var deleteTxBody =
                CryptoDeleteTransactionBody.newBuilder()
                        .deleteAccountID(deleteAccountId)
                        .transferAccountID(transferAccountId);

        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoDelete(deleteTxBody)
                .build();
    }
}
