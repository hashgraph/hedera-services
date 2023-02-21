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

import static org.junit.jupiter.api.Assertions.*;

class CryptoUpdateHandlerTest extends CryptoHandlerTestBase {
    //    private final AccountID updateAccountId = asAccount("0.0.32132");
    //    private final HederaKey updateAccountKey = asHederaKey(A_COMPLEX_KEY).get();
    //    @Mock private MerkleAccount updateAccount;
    //
    //    private CryptoUpdateHandler subject = new CryptoUpdateHandler();
    //
    //    @Test
    //    void cryptoUpdateVanilla() {
    //        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
    //        given(accounts.get(updateAccountId.getAccountNum())).willReturn(updateAccount);
    //        given(updateAccount.getAccountKey()).willReturn((JKey) updateAccountKey);
    //        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(false);
    //        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);
    //
    //        final var meta = subject.preHandle(txn, payer, store, waivers);
    //        basicMetaAssertions(meta, 2, false, OK);
    //        assertEquals(payerKey, meta.payerKey());
    //        assertTrue(meta.requiredNonPayerKeys().contains(updateAccountKey));
    //    }
    //
    //    @Test
    //    void cryptoUpdateNewSignatureKeyWaivedVanilla() {
    //        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
    //        given(accounts.get(updateAccountId.getAccountNum())).willReturn(updateAccount);
    //        given(updateAccount.getAccountKey()).willReturn((JKey) updateAccountKey);
    //        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(true);
    //        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);
    //
    //        final var meta = subject.preHandle(txn, payer, store, waivers);
    //        basicMetaAssertions(meta, 1, false, OK);
    //        assertEquals(payerKey, meta.payerKey());
    //        assertIterableEquals(List.of(updateAccountKey), meta.requiredNonPayerKeys());
    //    }
    //
    //    @Test
    //    void cryptoUpdateTargetSignatureKeyWaivedVanilla() {
    //        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
    //        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(false);
    //        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(true);
    //
    //        final var meta = subject.preHandle(txn, payer, store, waivers);
    //        basicMetaAssertions(meta, 1, false, OK);
    //        assertEquals(payerKey, meta.payerKey());
    //        assertFalse(meta.requiredNonPayerKeys().contains(updateAccountKey));
    //    }
    //
    //    @Test
    //    void cryptoUpdatePayerMissingFails() {
    //        final var txn = cryptoUpdateTransaction(updateAccountId, updateAccountId);
    //        given(accounts.get(updateAccountId.getAccountNum())).willReturn(null);
    //
    //        given(waivers.isNewKeySignatureWaived(txn, updateAccountId)).willReturn(false);
    //        given(waivers.isTargetAccountSignatureWaived(txn, updateAccountId)).willReturn(true);
    //
    //        final var meta = subject.preHandle(txn, updateAccountId, store, waivers);
    //        basicMetaAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
    //        assertNull(meta.payerKey());
    //    }
    //
    //    @Test
    //    void cryptoUpdatePayerMissingFailsWhenNoOtherSigsRequired() {
    //        final var txn = cryptoUpdateTransaction(updateAccountId, updateAccountId);
    //        given(accounts.get(updateAccountId.getAccountNum())).willReturn(null);
    //
    //        given(waivers.isNewKeySignatureWaived(txn, updateAccountId)).willReturn(true);
    //        given(waivers.isTargetAccountSignatureWaived(txn, updateAccountId)).willReturn(true);
    //
    //        final var meta = subject.preHandle(txn, updateAccountId, store, waivers);
    //        basicMetaAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
    //        assertNull(meta.payerKey());
    //    }
    //
    //    @Test
    //    void cryptoUpdateUpdateAccountMissingFails() {
    //        final var txn = cryptoUpdateTransaction(payer, updateAccountId);
    //        given(accounts.get(updateAccountId.getAccountNum())).willReturn(null);
    //
    //        given(waivers.isNewKeySignatureWaived(txn, payer)).willReturn(true);
    //        given(waivers.isTargetAccountSignatureWaived(txn, payer)).willReturn(false);
    //
    //        final var meta = subject.preHandle(txn, payer, store, waivers);
    //        basicMetaAssertions(meta, 0, true, INVALID_ACCOUNT_ID);
    //        assertEquals(payerKey, meta.payerKey());
    //    }
    //
    //    @Test
    //    void handleNotImplemented() {
    //        assertThrows(UnsupportedOperationException.class, () -> subject.handle(metaToHandle));
    //    }
    //
    //    private TransactionBody cryptoUpdateTransaction(
    //            final AccountID payerId, final AccountID accountToUpdate) {
    //        if (payerId.equals(payer)) {
    //            setUpPayer();
    //        }
    //        final var transactionID =
    //                TransactionID.newBuilder()
    //                        .setAccountID(payerId)
    //                        .setTransactionValidStart(consensusTimestamp);
    //        final var updateTxnBody =
    //                CryptoUpdateTransactionBody.newBuilder()
    //                        .setAccountIDToUpdate(accountToUpdate)
    //                        .setKey(key)
    //                        .build();
    //        return TransactionBody.newBuilder()
    //                .setTransactionID(transactionID)
    //                .setCryptoUpdateAccount(updateTxnBody)
    //                .build();
    //    }
}
