package com.hedera.node.app.spi;

import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code PreHandleDispatcher} takes a validated transaction and dispatches it to the correct
 * handler
 */
public interface PreHandleDispatcher {
    /**
     * Dispatch a request. It is forwarded to the correct handler, which takes care of the specific
     * functionality
     *
     * @param transactionBody the {@link TransactionBody} of the request
     * @throws NullPointerException if {@code transactionBody} is {@code null}
     */
    TransactionMetadata dispatch(@NonNull TransactionBody transactionBody, @NonNull AccountID payer);
}
