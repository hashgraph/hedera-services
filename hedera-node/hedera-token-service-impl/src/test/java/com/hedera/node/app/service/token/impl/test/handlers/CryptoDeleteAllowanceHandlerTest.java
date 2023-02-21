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

class CryptoDeleteAllowanceHandlerTest extends CryptoHandlerTestBase {
    //    private final TokenID nft = asToken("0.0.56789");
    //    private final AccountID owner = asAccount("0.0.123456");
    //    private final HederaKey ownerKey = asHederaKey(A_COMPLEX_KEY).get();
    //
    //    @Mock
    //    private MerkleAccount ownerAccount;
    //
    //    private CryptoDeleteAllowanceHandler subject = new CryptoDeleteAllowanceHandler();
    //
    //    @Test
    //    void cryptoDeleteAllowanceVanilla() {
    //        given(accounts.get(owner.getAccountNum())).willReturn(ownerAccount);
    //        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
    //
    //        final var txn = cryptoDeleteAllowanceTransaction(payer);
    //        final var context = new PreHandleContext(store, txn, payer);
    //        subject.preHandle(context);
    //        basicMetaAssertions(context, 1, false, OK);
    //        assertEquals(payerKey, context.getPayerKey());
    //        assertIterableEquals(List.of(ownerKey), context.getRequiredNonPayerKeys());
    //    }
    //
    //    @Test
    //    void cryptoDeleteAllowanceDoesntAddIfOwnerSameAsPayer() {
    //        given(accounts.get(owner.getAccountNum())).willReturn(ownerAccount);
    //        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
    //
    //        final var txn = cryptoDeleteAllowanceTransaction(owner);
    //        final var context = new PreHandleContext(store, txn, owner);
    //        subject.preHandle(context);
    //        basicMetaAssertions(context, 0, false, OK);
    //        assertEquals(ownerKey, context.getPayerKey());
    //        assertIterableEquals(List.of(), context.getRequiredNonPayerKeys());
    //    }
    //
    //    @Test
    //    void cryptoDeleteAllowanceFailsIfPayerOrOwnerNotExist() {
    //        var txn = cryptoDeleteAllowanceTransaction(owner);
    //        given(accounts.get(owner.getAccountNum())).willReturn(null);
    //
    //        final var context1 = new PreHandleContext(store, txn, owner);
    //        subject.preHandle(context1);
    //        basicMetaAssertions(context1, 0, true, INVALID_PAYER_ACCOUNT_ID);
    //        assertNull(context1.getPayerKey());
    //        assertIterableEquals(List.of(), context1.getRequiredNonPayerKeys());
    //
    //        txn = cryptoDeleteAllowanceTransaction(payer);
    //        final var context2 = new PreHandleContext(store, txn, payer);
    //        subject.preHandle(context2);
    //        basicMetaAssertions(context2, 0, true, INVALID_ALLOWANCE_OWNER_ID);
    //        assertEquals(payerKey, context2.getPayerKey());
    //        assertIterableEquals(List.of(), context2.getRequiredNonPayerKeys());
    //    }
    //
    //    @Test
    //    void handleNotImplemented() {
    //        assertThrows(UnsupportedOperationException.class, () -> subject.handle(metaToHandle));
    //    }
    //
    //    private TransactionBody cryptoDeleteAllowanceTransaction(final AccountID id) {
    //        final var transactionID =
    //                TransactionID.newBuilder().setAccountID(id).setTransactionValidStart(consensusTimestamp);
    //        final var allowanceTxnBody = CryptoDeleteAllowanceTransactionBody.newBuilder()
    //                .addNftAllowances(NftRemoveAllowance.newBuilder()
    //                        .setOwner(owner)
    //                        .setTokenId(nft)
    //                        .addAllSerialNumbers(List.of(1L, 2L, 3L))
    //                        .build())
    //                .build();
    //        return TransactionBody.newBuilder()
    //                .setTransactionID(transactionID)
    //                .setCryptoDeleteAllowance(allowanceTxnBody)
    //                .build();
    //    }
}
