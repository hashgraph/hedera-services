package com.hedera.evm.model;

import com.hederahashgraph.api.proto.java.AccountID;
import org.hyperledger.besu.datatypes.Address;

public record Id(long shard, long realm, long num) {

  public Address asEvmAddress() {
    return Address.fromHexString(EntityIdUtils.asHexedEvmAddress(this));
  }

  public static Id fromGrpcAccount(final AccountID id) {
    return new Id(id.getShardNum(), id.getRealmNum(), id.getAccountNum());
  }

}
