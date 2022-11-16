/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.config.EntityNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;

public class StaticNetworkContextTest {
    private final EntityNumbers entityNumbers = new MockEntityNumbers();
    private final AccountID treasury = asAccount("0.0.2");
    private StaticNetworkContext subject;

    @Test
    void waivesTargetSigForTreasuryUpdateOfNonTreasurySystemAccount() {
        // setup:
        final var txn = cryptoUpdateTransaction(entityNumbers.accounts().systemAdmin());

        // expect:
        assertTrue(subject.isTargetAccountSignatureWaived(txn, treasury));
    }

    @Test
    void waivesNewKeySigForTreasuryUpdateOfNonTreasurySystemAccount() {
        // setup:
        final var txn = cryptoUpdateTransaction(entityNumbers.accounts().systemAdmin());

        // expect:
        assertTrue(subject.isNewKeySignatureWaived(txn, treasury));
    }

    @Test
    void doesntWaiveNewKeySigForTreasuryUpdateOfTreasurySystemAccount() {
        // setup:
        final var txn = cryptoUpdateTransaction(entityNumbers.accounts().treasury());

        // expect:
        assertFalse(subject.isNewKeySignatureWaived(txn, treasury));
    }

    @Test
    void doesntWaiveNewKeySigForTreasuryUpdateOfNonSystemAccount() {
        // setup:
        final var txn = cryptoUpdateTransaction(1234L);

        // expect:
        assertFalse(subject.isNewKeySignatureWaived(txn, treasury));
    }

    @Test
    void doesntWaivesNewKeySigForCivilianUpdate() {
        // setup:
        final var txn = cryptoUpdateTransaction(1234L);

        // expect:
        assertFalse(subject.isNewKeySignatureWaived(txn, treasury));
    }

    private TransactionBody cryptoUpdateTransaction(final long accountToUpdate) {
        final var transactionID = TransactionID.newBuilder();
        final var updateTxnBody =
                CryptoUpdateTransactionBody.newBuilder()
                        .setAccountIDToUpdate(asAccount("0.0." + accountToUpdate))
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoUpdateAccount(updateTxnBody)
                .build();
    }
}
