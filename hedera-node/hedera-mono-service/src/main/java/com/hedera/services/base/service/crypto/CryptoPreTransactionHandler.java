package com.hedera.services.base.service.crypto;

import com.hedera.services.base.metadata.TransactionMetadata;
import com.hedera.services.base.service.PreTransactionHandler;
import com.hederahashgraph.api.proto.java.Transaction;

/**
 * A definition of an interface for validating all transactions defined in the protobuf "CryptoService"
 * in pre-handle including signature verification.
 */
public interface CryptoPreTransactionHandler extends PreTransactionHandler {
    TransactionMetadata cryptoCreate(final Transaction txn);
}
