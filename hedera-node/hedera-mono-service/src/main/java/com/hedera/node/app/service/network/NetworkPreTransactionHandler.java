package com.hedera.node.app.service.network;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;

public interface NetworkPreTransactionHandler extends PreTransactionHandler {
    /**
     * Submits a "wrapped" transaction to the network, skipping its standard prechecks. (Note that
     * the "wrapper" <tt>UncheckedSubmit</tt> transaction is still subject to normal prechecks,
     * including an authorization requirement that its payer be either the treasury or system admin
     * account.)
     */
    TransactionMetadata preHandleUncheckedSubmit(TransactionBody txn);
}
