package com.hedera.services.mocks;

import com.hedera.services.state.validation.AccountUsageTracking;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MockAccountTracking implements AccountUsageTracking {
  @Inject
  public MockAccountTracking() {
    // Dagger2
  }


  @Override
  public void refreshAccounts() {
    // No-op
  }

  @Override
  public void recordContracts(final int n) {
    // No-op
  }
}
