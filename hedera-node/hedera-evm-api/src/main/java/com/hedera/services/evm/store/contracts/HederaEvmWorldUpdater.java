package com.hedera.services.evm.store.contracts;

import org.hyperledger.besu.evm.worldstate.WorldUpdater;

/**
 * Provides a stacked Hedera adapted world view. Utilised by {@link
 * org.hyperledger.besu.evm.frame.MessageFrame} in order to provide a layered view for read/writes
 * of the state during EVM transaction execution
 */
public interface HederaEvmWorldUpdater extends WorldUpdater {

  /**
   * Tracks how much Gas should be refunded to the sender account for the TX. SBH price is
   * refunded for the first allocation of new contract storage in order to prevent double charging
   * the client.
   *
   * @return the amount of Gas to refund;
   */
  long getSbhRefund();
}
