package com.hedera.services.evm;

import com.hedera.services.evm.store.models.HederaEvmAccount;
import org.hyperledger.besu.datatypes.Address;

public class MockHederaEvmAccount implements HederaEvmAccount {

  private final Address address;

  public MockHederaEvmAccount(final Address address) {
    this.address = address;
  }

  @Override
  public Address canonicalAddress() {
    return address;
  }
}
