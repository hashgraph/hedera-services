package com.hedera.services.evm.store.models;

import com.hedera.services.evm.accounts.AccountAccessor;
import org.hyperledger.besu.datatypes.Address;

public class MockAccountAccessor implements AccountAccessor {
  private final Address address =
      Address.fromHexString("0x000000000000000000000000000000000000077e");

  @Override
  public Address exists(Address addressOrAlias) {
    return address;
  }
}
