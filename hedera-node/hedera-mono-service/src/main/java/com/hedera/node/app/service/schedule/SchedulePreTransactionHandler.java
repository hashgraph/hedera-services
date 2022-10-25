package com.hedera.node.app.service.schedule;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;

public interface SchedulePreTransactionHandler extends PreTransactionHandler {
    /**
     * Creates a new Schedule by submitting the transaction
     */
    TransactionMetadata preHandleCreateSchedule(TransactionBody txn);

    /**
     * Signs a new Schedule by submitting the transaction
     */
    TransactionMetadata preHandleSignSchedule(TransactionBody txn);

    /**
     * Deletes a new Schedule by submitting the transaction
     */
    TransactionMetadata preHandleDeleteSchedule(TransactionBody txn);
}
