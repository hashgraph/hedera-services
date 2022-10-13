package com.hedera.node.app.service.token;

import com.hedera.node.app.spi.TransactionMetadata;
import com.hedera.node.app.spi.PreTransactionHandler;
import com.hederahashgraph.api.proto.java.Transaction;

/**
 * A definition of an interface for validating all transactions defined in the protobuf "CryptoService"
 * in pre-handle including signature verification.
 */
public interface CryptoPreTransactionHandler extends PreTransactionHandler {
    /**
     * pre-handle {@link com.hederahashgraph.api.proto.java.CryptoCreate} transaction
     * @param txn crypto create transaction
     * @return metadata accumulated from pre-handling the transaction
     */
    TransactionMetadata cryptoCreate(final Transaction txn);
}
