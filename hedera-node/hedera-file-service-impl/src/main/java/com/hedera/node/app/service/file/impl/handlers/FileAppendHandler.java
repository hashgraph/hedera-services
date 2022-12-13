package com.hedera.node.app.service.file.impl.handlers;

import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all workflow-related functionality regarding
 * {@link com.hederahashgraph.api.proto.java.HederaFunctionality#FileAppend}.
 */
public class FileAppendHandler implements TransactionHandler {

    @Override
    public void preCheck(@NonNull final TransactionBody txBody) throws PreCheckException {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * This method is called during the pre-handle workflow.
     * <p>
     * Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache, and creates the {@link TransactionMetadata} that is used
     * in the handle stage.
     * <p>
     * Please note: the method signature is just a placeholder which is most likely going to change.
     *
     * @param txBody the {@link TransactionBody} with the transaction data
     * @param payer the {@link AccountID} of the payer
     * @return the {@link TransactionMetadata} with all information that needs to be passed to {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public TransactionMetadata preHandle(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID payer) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     * <p>
     * Please note: the method signature is just a placeholder which is most likely going to change.
     *
     * @param metadata the {@link TransactionMetadata} that was generated during pre-handle.
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionMetadata metadata) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
