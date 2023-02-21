package com.hedera.node.app.spi.fixtures;


import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;

public interface ScheduledTransactionFactory extends TransactionFactory {
    long FEE = 123L;
    String SCHEDULED_TXN_MEMO = "Wait for me!";

    default SchedulableTransactionBody scheduledTxn() {
        return SchedulableTransactionBody.newBuilder()
                .transactionFee(FEE)
                .memo(SCHEDULED_TXN_MEMO)
                .cryptoDelete(CryptoDeleteTransactionBody.newBuilder()
                        .deleteAccountID(asAccount("0.0.2"))
                        .transferAccountID(asAccount("0.0.75231")))
                .build();
    }

    default TransactionBody scheduleCreateTxnWith(
            final Key scheduleAdminKey,
            final String scheduleMemo,
            final AccountID payer,
            final AccountID scheduler,
            final Timestamp validStart) {
        return scheduleCreateTxnWith(scheduleAdminKey, scheduleMemo, payer, scheduler, validStart, null, null);
    }

    default TransactionBody scheduleCreateTxnWith(
            final Key scheduleAdminKey,
            final String scheduleMemo,
            final AccountID payer,
            final AccountID scheduler,
            final Timestamp validStart,
            final Timestamp expirationTime,
            final Boolean waitForExpiry) {
        final var creation = ScheduleCreateTransactionBody.newBuilder()
                .memo(scheduleMemo)
                .scheduledTransactionBody(scheduledTxn());
        if (scheduleAdminKey != null) {
            creation.adminKey(scheduleAdminKey);
        }
        if (payer != null) {
            creation.payerAccountID(payer);
        }
        if (expirationTime != null) {
            creation.expirationTime(expirationTime);
        }
        if (waitForExpiry != null) {
            creation.waitForExpiry(waitForExpiry);
        }
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(validStart)
                        .accountID(scheduler)
                        .build())
                .scheduleCreate(creation)
                .build();
    }
}
