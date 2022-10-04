package com.hedera.services.evm.store.contracts;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldView;

/**
 * Hedera adapted interface for a view over the accounts of the world state and methods for
 * persisting state changes
 */
public interface HederaEvmMutableWorldState
    extends WorldState, WorldView
{
  /**
   * Given a the EVM address of a sponsoring account, returns an EVM address appropriate for a new
   * contract.
   *
   * @param sponsor the address of the sponsor of a new contract
   * @return an appropriate EVM address for the new contract
   */
  Address newContractAddress(Address sponsor);

  /**
   * Creates an updater for this mutable world view.
   *
   * @return a new updater for this mutable world view. On commit, change made to this updater
   *     will become visible on this view.
   */
  HederaWorldUpdater updater();
}

