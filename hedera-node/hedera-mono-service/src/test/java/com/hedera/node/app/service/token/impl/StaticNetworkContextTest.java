package com.hedera.node.app.service.token.impl;

import com.hedera.services.config.EntityNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        final var transactionID =
                TransactionID.newBuilder();
        final var updateTxnBody =
                CryptoUpdateTransactionBody.newBuilder()
                        .setAccountIDToUpdate(asAccount("0.0."+ accountToUpdate))
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoUpdateAccount(updateTxnBody)
                .build();
    }


}
