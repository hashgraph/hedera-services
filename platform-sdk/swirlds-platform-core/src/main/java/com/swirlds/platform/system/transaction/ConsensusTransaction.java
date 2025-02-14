// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.system.transaction;

import java.time.Instant;

/**
 * A transaction that has reached consensus.
 */
public sealed interface ConsensusTransaction extends Transaction permits TransactionWrapper {
    /**
     * Returns the community's consensus timestamp for this item.
     *
     * @return the consensus timestamp
     */
    Instant getConsensusTimestamp();
}
