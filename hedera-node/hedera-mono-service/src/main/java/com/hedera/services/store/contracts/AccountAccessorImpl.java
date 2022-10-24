package com.hedera.services.store.contracts;

import com.hedera.services.evm.accounts.AccountAccessor;
import org.hyperledger.besu.datatypes.Address;

public class AccountAccessorImpl implements AccountAccessor {

  private final WorldLedgers trackingLedgers;

  public AccountAccessorImpl(final WorldLedgers trackingLedgers) {
    this.trackingLedgers = trackingLedgers;
  }

  @Override
  public Address exists(final Address addressOrAlias) {
    return trackingLedgers.canonicalAddress(addressOrAlias);
  }
}
