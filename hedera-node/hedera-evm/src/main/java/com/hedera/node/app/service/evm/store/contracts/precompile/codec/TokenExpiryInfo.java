package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import org.hyperledger.besu.datatypes.Address;

public class TokenExpiryInfo {

  private long second;
  private Address autoRenewAccount;
  private long autoRenewPeriod;

  public TokenExpiryInfo(long second, Address autoRenewAccount, long autoRenewPeriod) {
    this.second = second;
    this.autoRenewAccount = autoRenewAccount;
    this.autoRenewPeriod = autoRenewPeriod;
  }

  public long getSecond() {
    return second;
  }

  public Address getAutoRenewAccount() {
    return autoRenewAccount;
  }

  public long getAutoRenewPeriod() {
    return autoRenewPeriod;
  }
}
