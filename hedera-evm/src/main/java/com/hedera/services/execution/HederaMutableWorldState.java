package com.hedera.services.execution;

import com.hederahashgraph.api.proto.java.ContractID;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldView;

/**
 * Hedera adapted interface for a view over the accounts of the world state and methods for
 * persisting state changes
 */
public interface HederaMutableWorldState extends WorldState, WorldView {
  /**
   * Given a the EVM address of a sponsoring account, returns an EVM address appropriate for a new
   * contract.
   *
   * <p><b>Important: </b>Since the new contract will <i>also</i> be a Hedera entity that has a
   * {@code 0.0.X} id, allocating a new contract address must imply reserving a Hedera entity
   * number. Implementations must be able to return their last reserved number on receiving a
   * {@link HederaMutableWorldState#reclaimContractId()} call.
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

