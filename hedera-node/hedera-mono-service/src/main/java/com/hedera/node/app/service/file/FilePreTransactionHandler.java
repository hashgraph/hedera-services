package com.hedera.node.app.service.file;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;

public interface FilePreTransactionHandler extends PreTransactionHandler {
    /**
     * Creates a file
     */
    TransactionMetadata preHandleCreateFile(TransactionBody txn);

    /**
     * Updates a file
     */
    TransactionMetadata preHandleUpdateFile(TransactionBody txn);

    /**
     * Deletes a file
     */
    TransactionMetadata preHandleDeleteFile(TransactionBody txn);

    /**
     * Appends to a file
     */
    TransactionMetadata preHandleAppendContent(TransactionBody txn);

    /**
     * Deletes a file if the submitting account has network admin privileges
     */
    TransactionMetadata preHandleSystemDelete(TransactionBody txn);

    /**
     * Undeletes a file if the submitting account has network admin privileges
     */
    TransactionMetadata preHandleSystemUndelete(TransactionBody txn);
}
