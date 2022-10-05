package com.hedera.services.evm.store.models;

import org.hyperledger.besu.datatypes.Address;

public interface HederaEvmAccount {

  Address canonicalAddress();
}
