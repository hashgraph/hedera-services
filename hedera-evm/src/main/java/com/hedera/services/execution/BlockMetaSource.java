package com.hedera.services.execution;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;

/** Provides block information to a {@link EvmTxProcessor}. */
public interface BlockMetaSource {

  /**
   * Returns the hash of the given block number
   *
   * @param blockNo the block number of interest
   * @return its hash, if available
   */
  Hash getBlockHash(long blockNo);

  /**
   * Returns the in-scope block values, given an effective gas limit.
   *
   * @param gasLimit the effective gas limit
   * @return the scoped block values
   */
  BlockValues computeBlockValues(long gasLimit);
}
