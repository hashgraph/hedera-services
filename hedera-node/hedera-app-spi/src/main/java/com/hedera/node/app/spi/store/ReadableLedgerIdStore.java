// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.spi.store;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Read-only access to the ledger id in consensus state, for services that cannot depend on the History service's own
 * store. Reading it from state gives every node the same value for the same round, unlike node-local sources such as
 * the latest block-proven snapshot.
 */
public interface ReadableLedgerIdStore {
    /**
     * Returns the ledger id initiating the chain of trusted history, if known.
     *
     * @return the ledger id, or {@code null} before it has been set in state
     */
    @Nullable
    Bytes getLedgerId();
}
