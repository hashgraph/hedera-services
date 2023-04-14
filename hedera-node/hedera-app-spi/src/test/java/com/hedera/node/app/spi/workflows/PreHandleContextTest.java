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

package com.hedera.node.app.spi.workflows;

import static com.hedera.node.app.spi.workflows.PreHandleContextListUpdatesTest.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.accounts.Account;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.key.HederaKey;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleContextTest {
    private static final AccountID PAYER = AccountID.newBuilder().accountNum(3L).build();

    @Mock
    private HederaKey payerKey;

    @Mock
    private HederaKey otherKey;

    @Mock
    AccountAccess accountAccess;

    @Mock
    Account account;

    private PreHandleContext subject;

    @Test
    void gettersWork() throws PreCheckException {
        given(accountAccess.getAccountById(PAYER)).willReturn(account);
        given(account.getKey()).willReturn(payerKey);
        final var txn = createAccountTransaction();
        subject = new PreHandleContext(accountAccess, txn).requireKey(otherKey);

        assertEquals(txn, subject.body());
        assertEquals(payerKey, subject.payerKey());
        assertEquals(Set.of(otherKey), subject.requiredNonPayerKeys());
    }

    private TransactionBody createAccountTransaction() {
        final var transactionID = TransactionID.newBuilder()
                .accountID(PAYER)
                .transactionValidStart(Timestamp.newBuilder().seconds(123_456L).build());
        final var createTxnBody = CryptoCreateTransactionBody.newBuilder()
                .key(A_COMPLEX_KEY)
                .receiverSigRequired(true)
                .memo("Create Account")
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoCreateAccount(createTxnBody)
                .build();
    }
}
