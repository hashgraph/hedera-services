package com.hedera.services.evm.implementation.contracts.execution;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.ImmutableHash;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;

/** Provides block information to a {@link HederaEvmTxProcessor}. */
public interface BlockMetaSource {
  Hash UNAVAILABLE_BLOCK_HASH = ethHashFrom(
          new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]));

  /**
   * Returns the hash of the given block number, or {@link BlockMetaSource#UNAVAILABLE_BLOCK_HASH}
   * if unavailable.
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

  static org.hyperledger.besu.datatypes.Hash ethHashFrom(final com.swirlds.common.crypto.Hash hash) {
    final byte[] hashBytesToConvert = hash.getValue();
    final byte[] prefixBytes = new byte[32];
    System.arraycopy(hashBytesToConvert, 0, prefixBytes, 0, 32);
    return org.hyperledger.besu.datatypes.Hash.wrap(Bytes32.wrap(prefixBytes));
  }
}
