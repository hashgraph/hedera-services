package com.hedera.node.app.service.freeze;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;

public interface FreezePreTransactionHandler extends PreTransactionHandler {
    /**
     * Freezes the nodes by submitting the transaction. The grpc server returns the
     * TransactionResponse
     */
    TransactionMetadata preHandleFreeze(TransactionBody txn);
}
