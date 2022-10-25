package com.hedera.node.app.service.util;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;

public interface UtilPreTransactionHandler extends PreTransactionHandler {
    /**
     * Generates a pseudorandom number.
     */
    TransactionMetadata preHandlePrng(TransactionBody txn);
}
