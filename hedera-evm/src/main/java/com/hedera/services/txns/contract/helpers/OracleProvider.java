package com.hedera.services.txns.contract.helpers;

import org.hyperledger.besu.evm.frame.MessageFrame;

public interface OracleProvider {

    /**
     * Returns the effective expiry for storage being allocated in the current frame.
     *
     * @param frame the active message frame
     * @return the effective expiry for allocated storage
     */
    long storageExpiryIn(MessageFrame frame);
}
