package com.hedera.services.mocks;

import com.hedera.services.state.validation.ContractStorageLimits;

public class MockStorageLimits implements ContractStorageLimits {
  @Override
  public void refreshStorageSlots() {
    // No-op
  }

  @Override
  public void assertUsableTotalSlots(long n) {
    // No-op
  }

  @Override
  public void assertUsableContractSlots(long n) {
    // No-op
  }
}
