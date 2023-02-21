/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl.test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoSignatureWaiversImplTest {
    @Mock HederaAccountNumbers accountNumbers;
    private CryptoSignatureWaiversImpl subject;

    @BeforeEach
    void setUp() {
        subject = new CryptoSignatureWaiversImpl(accountNumbers);
    }

    @Test
    void notImplementedStuffIsntImplemented() {
        final var account = AccountID.newBuilder().accountNum(3000).build();
        final var txn = cryptoUpdateTransaction(account, account);
        assertThrows(
                NotImplementedException.class, () -> subject.isNewKeySignatureWaived(txn, account));
        assertThrows(
                NotImplementedException.class,
                () -> subject.isTargetAccountSignatureWaived(txn, account));
    }

    private TransactionBody cryptoUpdateTransaction(
            final AccountID payerId, final AccountID accountToUpdate) {
        final var transactionID = TransactionID.newBuilder().accountID(payerId);
        final var updateTxnBody =
                CryptoUpdateTransactionBody.newBuilder().accountIDToUpdate(accountToUpdate).build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoUpdateAccount(updateTxnBody)
                .build();
    }
}
