package com.hedera.services.evm.accounts;

import org.hyperledger.besu.datatypes.Address;

public interface AccountAccessor {

  Address exists(final Address addressOrAlias);
}