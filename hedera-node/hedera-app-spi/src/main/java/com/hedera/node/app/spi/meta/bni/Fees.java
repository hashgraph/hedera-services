package com.hedera.node.app.spi.meta.bni;

import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides access to the cost of gas and other operations within a given {@link Scope}.
 */
public interface Fees {
    /**
     * Returns the gas price in tinybars for this scope.
     *
     * @return the gas price in tinybars
     */
    long gasPrice();

    /**
     * Returns the lazy creation cost within this scope.
     *
     * @return the lazy creation cost in tinybars
     */
    long lazyCreationCostInGas();

    /**
     * Returns the cost to dispatch a {@code TransactionBody} transaction within this scope.
     *
     * @param syntheticTransaction the transaction to dispatch
     * @return the cost in tinybars
     */
    long transactionFeeInGas(@NonNull TransactionBody syntheticTransaction);
}
