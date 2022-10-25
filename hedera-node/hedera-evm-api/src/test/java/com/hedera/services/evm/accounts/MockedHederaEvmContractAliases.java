package com.hedera.services.evm.accounts;

import org.hyperledger.besu.datatypes.Address;

public class MockedHederaEvmContractAliases extends HederaEvmContractAliases {

  @Override
  public Address resolveForEvm(Address addressOrAlias) {
    return addressOrAlias;
  }
}
