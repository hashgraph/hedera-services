package com.hedera.evm.execution;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

/**
 * Provides a stacked Hedera adapted world view. Utilised by {@link
 * org.hyperledger.besu.evm.frame.MessageFrame} in order to provide a layered view for read/writes
 * of the state during EVM transaction execution
 */
public interface HederaWorldUpdater extends WorldUpdater {
  /**
   * Allocates new Contract address based on the realm and shard of the sponsor IMPORTANT - The Id
   * must be reclaimed if the MessageFrame reverts
   *
   * @param sponsor sponsor of the new contract
   * @return newly generated contract {@link Address}
   */
  Address newContractAddress(Address sponsor);

  /**
   * Tracks how much Gas should be refunded to the sender account for the TX. SBH price is
   * refunded for the first allocation of new contract storage in order to prevent double charging
   * the client.
   *
   * @return the amount of Gas to refund;
   */
  long getSbhRefund();

  /**
   * Used to keep track of SBH gas refunds between all instances of HederaWorldUpdater. Lower
   * level updaters should add the value of Gas to refund to their respective parent Updater on
   * commit.
   *
   * @param refund the amount of Gas to refund;
   */
  void addSbhRefund(long refund);

  void countIdsAllocatedByStacked(int n);
}
