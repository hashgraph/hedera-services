package com.hedera.services.mocks;


import com.hedera.services.config.AccountNumbers;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MockAccountNumbers extends AccountNumbers {
  @Inject
  public MockAccountNumbers() {
    super(null);
  }

  @Override
  public long treasury() {
    return 2;
  }

  @Override
  public long systemAdmin() {
    return 50;
  }

  @Override
  public long addressBookAdmin() {
    return 55;
  }

  @Override
  public long feeSchedulesAdmin() {
    return 56;
  }

  @Override
  public long exchangeRatesAdmin() {
    return 57;
  }

  @Override
  public long freezeAdmin() { return 58; }

  @Override
  public long systemDeleteAdmin() { return 59; }

  @Override
  public long systemUndeleteAdmin() { return 60; }

  @Override
  public boolean isSuperuser(long num) {
    return (num == 2) || (num == 50);
  }

  @Override
  public long stakingRewardAccount() {
    return 800;
  }
}
