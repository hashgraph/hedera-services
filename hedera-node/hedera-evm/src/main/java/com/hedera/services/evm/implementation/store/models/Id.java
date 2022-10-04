package com.hedera.services.evm.implementation.store.models;

import com.hedera.services.utils.EntityIdUtils;
import org.hyperledger.besu.datatypes.Address;

/** Represents the id of a Hedera entity (account, topic, token, contract, file, or schedule). */
public record Id(long shard, long realm, long num) {
  public static final Id DEFAULT = new Id(0, 0, 0);

  /**
   * Returns the EVM representation of the Account
   *
   * @return {@link Address} evm representation
   */
  public Address asEvmAddress() {
    return Address.fromHexString(EntityIdUtils.asHexedEvmAddress(this));
  }
}
