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
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.handlers.CryptoUpdateHandler;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class CryptoUpdateHandlerTest extends CryptoHandlerTestBase {
    private final AccountID updateAccountId =
            AccountID.newBuilder().accountNum(32132).build();
    private final HederaKey updateAccountKey = asHederaKey(A_COMPLEX_KEY).get();

    @Mock
    private MerkleAccount updateAccount;

    private CryptoUpdateHandler subject = new CryptoUpdateHandler();

    @Test
    void cryptoUpdateVanilla() {
        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
        given(accounts.get(EntityNumVirtualKey.fromLong(updateAccountId.accountNum())))
                .willReturn(updateAccount);
        given(updateAccount.getAccountKey()).willReturn((JKey) updateAccountKey);
        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(false);
        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context, waivers);
        basicMetaAssertions(context, 2, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertTrue(context.getRequiredNonPayerKeys().contains(updateAccountKey));
    }

    @Test
    void cryptoUpdateNewSignatureKeyWaivedVanilla() {
        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
        given(accounts.get(EntityNumVirtualKey.fromLong(updateAccountId.accountNum())))
                .willReturn(updateAccount);
        given(updateAccount.getAccountKey()).willReturn((JKey) updateAccountKey);
        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(true);
        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context, waivers);
        basicMetaAssertions(context, 1, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(List.of(updateAccountKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void cryptoUpdateTargetSignatureKeyWaivedVanilla() {
        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(false);
        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(true);

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context, waivers);
        basicMetaAssertions(context, 1, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertFalse(context.getRequiredNonPayerKeys().contains(updateAccountKey));
    }

    @Test
    void cryptoUpdatePayerMissingFails() {
        final var txn = cryptoUpdateTransaction(updateAccountId, updateAccountId);
        given(accounts.get(EntityNumVirtualKey.fromLong(updateAccountId.accountNum())))
                .willReturn(null);

        given(waivers.isNewKeySignatureWaived(txn, updateAccountId)).willReturn(false);
        given(waivers.isTargetAccountSignatureWaived(txn, updateAccountId)).willReturn(true);

        final var context = new PreHandleContext(store, txn, updateAccountId);
        subject.preHandle(context, waivers);
        basicMetaAssertions(context, 0, true, INVALID_PAYER_ACCOUNT_ID);
        assertNull(context.getPayerKey());
    }

    @Test
    void cryptoUpdatePayerMissingFailsWhenNoOtherSigsRequired() {
        final var txn = cryptoUpdateTransaction(updateAccountId, updateAccountId);
        given(accounts.get(EntityNumVirtualKey.fromLong(updateAccountId.accountNum())))
                .willReturn(null);

        given(waivers.isNewKeySignatureWaived(txn, updateAccountId)).willReturn(true);
        given(waivers.isTargetAccountSignatureWaived(txn, updateAccountId)).willReturn(true);

        final var context = new PreHandleContext(store, txn, updateAccountId);
        subject.preHandle(context, waivers);
        basicMetaAssertions(context, 0, true, INVALID_PAYER_ACCOUNT_ID);
        assertNull(context.getPayerKey());
    }

    @Test
    void cryptoUpdateUpdateAccountMissingFails() {
        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
        given(accounts.get(EntityNumVirtualKey.fromLong(updateAccountId.accountNum())))
                .willReturn(null);

        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(true);
        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);

        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context, waivers);
        basicMetaAssertions(context, 0, true, INVALID_ACCOUNT_ID);
        assertEquals(payerKey, context.getPayerKey());
    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle(metaToHandle));
    }

    private TransactionBody cryptoUpdateTransaction(final AccountID payerId, final AccountID accountToUpdate) {
        if (payerId.equals(payer)) {
            setUpPayer();
        }
        final var transactionID =
                TransactionID.newBuilder().accountID(payerId).transactionValidStart(consensusTimestamp);
        final var updateTxnBody = CryptoUpdateTransactionBody.newBuilder()
                .accountIDToUpdate(accountToUpdate)
                .key(key)
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoUpdateAccount(updateTxnBody)
                .build();
    }
}
