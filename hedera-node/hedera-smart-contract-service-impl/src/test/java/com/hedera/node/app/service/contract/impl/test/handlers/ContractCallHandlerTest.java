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
package com.hedera.node.app.service.contract.impl.test.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractCallHandlerTest extends ContractHandlerTestBase {
    private ContractCallHandler subject = new ContractCallHandler();

//    @Test
//    @DisplayName("Fails for invalid payer account")
//    void invalidPayer() {
//        final var txn = contractCallTransaction();
//        given(keyLookup.getKey(payer))
//                .willReturn(KeyOrLookupFailureReason.withFailureReason(INVALID_ACCOUNT_ID));
//        final var meta = subject.preHandle(txn, txn.getTransactionID().getAccountID(), keyLookup);
//        basicMetaAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
//        assertEquals(null, meta.payerKey());
//    }
//
//    @Test
//    @DisplayName("Succeeds for valid payer account")
//    void validPayer() {
//        final var txn = contractCallTransaction();
//        final var meta = subject.preHandle(txn, txn.getTransactionID().getAccountID(), keyLookup);
//        basicMetaAssertions(meta, 0, false, OK);
//        assertEquals(payerKey, meta.payerKey());
//    }
//
//    @Test
//    void callHandle() {
//        final var txn = contractCallTransaction();
//        final var meta = subject.preHandle(txn, txn.getTransactionID().getAccountID(), keyLookup);
//        assertThrows(UnsupportedOperationException.class, () -> subject.handle(meta));
//    }
//
//    private TransactionBody contractCallTransaction() {
//        final var transactionID =
//                TransactionID.newBuilder()
//                        .setAccountID(payer)
//                        .setTransactionValidStart(consensusTimestamp);
//        return TransactionBody.newBuilder()
//                .setTransactionID(transactionID)
//                .setContractCall(
//                        ContractCallTransactionBody.newBuilder()
//                                .setGas(1_234)
//                                .setAmount(1_234L)
//                                .setContractID(targetContract))
//                .build();
//    }
}
