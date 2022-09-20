package com.hedera.evm.store.contracts.precompile;

import com.hedera.evm.utils.accessors.TxnAccessor;

public interface Precompile {
    default void addImplicitCostsIn(final TxnAccessor accessor) {
        // Most transaction types can compute their full Hedera fee from just an initial transaction
        // body; but
        // for a token transfer, we may need to recompute to charge for the extra work implied by
        // custom fees
    }
}
